/* This file is part of Wissl - Copyright (C) 2012 Mathieu Schnoor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.msch.wissl.server;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jboss.util.file.Files;

import fr.msch.wissl.common.Config;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 * 
 */
public class Library {

	private static Library instance = null;

	/** songs added to DB during this indexer run */
	private long addSongCount = 0;
	/** songs already present in DB during this indexer run */
	private long skipSongCount = 0;
	/** songs that could not be added in DB during this indexer run */
	private long failedSongCount = 0;

	/** MD5 */
	MessageDigest md5 = null;

	/** parse id3 position tags that look like '2/17' */
	private static final Pattern positionPattern = Pattern
			.compile("[ ]*([0-9]+).*");

	private Pattern artworkRegex = null;
	private FileFilter artworkFilter = null;
	private FileFilter artworkFallback = null;

	/** indexer thread */
	private Thread thread = null;
	private boolean running = true;
	private boolean kill = false;

	private Queue<File> files = null;
	private Map<String, File> toRead = null;
	private Queue<Song> songs = null;
	private Map<String, Map<String, String>> artworks = null;
	private Queue<Song> toInsert = null;
	private Set<String> hashes = null;

	private boolean fileSearchDone = false;
	private boolean dbCheckDone = false;
	private boolean fileReadDone = false;
	private boolean resizeDone = false;

	private long fileSearchTime = 0;
	private long dbCheckTime = 0;
	private long fileReadTime = 0;
	private long resizeTime = 0;
	private long dbInsertTime = 0;

	/** false when idle, true when indexing */
	private boolean working = false;
	/** when indexing, estimates percent done in [0,1] */
	private float percentDone = 1.0f;
	/** when indexing, estimates time left in seconds */
	private long secondsLeft = -1;
	/** total songs indexed in current run */
	private long songsDone = 0;
	/** total songs to index in current run */
	private long songsTodo = 0;

	/**
	 * Create library and launch indexer thread
	 */
	public static void create() {
		stfuLog4j();

		instance = new Library();

		try {
			instance.md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e1) {
			Logger.error("Cannot load MD5 digest", e1);
			throw new Error("Cannot load MD5 digest", e1);
		}

		instance.startIndexing();
	}

	/**
	 * Kill indexer thread
	 */
	public static void stop() {
		if (instance == null)
			return;

		instance.running = false;
		instance.kill = true;
		instance.thread.interrupt();
	}

	public static void interrupt() {
		instance.thread.interrupt();
	}

	/**
	 * @return indexer status as JSON object
	 */
	public static String getIndexerStatusAsJSON() {
		if (instance.working) {
			StringBuilder sb = new StringBuilder();
			sb.append("{\"running\": true,");
			sb.append("\"percentDone\":" + instance.percentDone + ",");
			sb.append("\"secondsLeft\":" + instance.secondsLeft + ",");
			sb.append("\"songsDone\":" + instance.songsDone + ",");
			sb.append("\"songsTodo\":" + instance.songsTodo + "}");
			return sb.toString();
		} else {
			return "{\"running\": false}";
		}
	}

	private Library() {
		this.songs = new ConcurrentLinkedQueue<Song>();
		this.toRead = new ConcurrentHashMap<String, File>();
		this.files = new ConcurrentLinkedQueue<File>();
		this.toInsert = new ConcurrentLinkedQueue<Song>();
		this.hashes = new HashSet<String>();
		this.artworks = new HashMap<String, Map<String, String>>();

		this.artworkFallback = new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return Pattern.matches(".*[.](jpeg|jpg|png|bmp|gif)$", pathname
						.getName().toLowerCase());
			}
		};

		Runnable timer = new Runnable() {

			@Override
			public void run() {
				while (running && !kill) {
					final long t1 = System.currentTimeMillis();

					final List<File> music = new ArrayList<File>();
					for (String path : Config.getMusicPath()) {
						music.add(new File(path));
					}

					addSongCount = 0;
					skipSongCount = 0;
					failedSongCount = 0;
					fileSearchTime = 0;
					dbCheckTime = 0;
					fileReadTime = 0;
					dbInsertTime = 0;
					resizeTime = 0;
					songs.clear();
					toRead.clear();
					files.clear();
					hashes.clear();
					toInsert.clear();
					artworks.clear();

					songsTodo = 0;
					songsDone = 0;
					working = true;
					percentDone = 0.0f;
					secondsLeft = -1;

					artworkRegex = Pattern.compile(Config.getArtworkRegex());
					artworkFilter = new FileFilter() {
						@Override
						public boolean accept(File pathname) {
							return (artworkRegex.matcher(pathname.getName()
									.toLowerCase()).matches());
						}
					};

					// walks filesystem and indexes files that look like music
					fileSearchDone = false;
					Thread fileSearch = new Thread(new Runnable() {
						public void run() {
							long f1 = System.currentTimeMillis();
							for (File f : music) {
								try {
									listFiles(f, files);
								} catch (IOException e) {
									Logger.error(
											"Failed to add directory to library: "
													+ f.getAbsolutePath(), e);
								} catch (InterruptedException e) {
									return;
								}
							}
							fileSearchDone = true;
							fileSearchTime = (System.currentTimeMillis() - f1);
						}
					});
					fileSearch.start();

					// exclude files that are already in DB
					dbCheckDone = false;
					Thread dbCheck = new Thread(new Runnable() {
						public void run() {
							while (!kill && !dbCheckDone) {
								long f1 = System.currentTimeMillis();
								while (!files.isEmpty()) {
									File f = files.remove();
									String hash = new String(md5.digest(f
											.getAbsolutePath().getBytes()));

									boolean hasSong = false;

									try {
										hasSong = DB.get().hasSong(hash);
									} catch (SQLException e) {
										Logger.error(
												"Failed to query DB for file "
														+ f.getAbsolutePath(),
												e);
									}
									if (!hasSong) {
										toRead.put(hash, f);
									} else {
										skipSongCount++;
									}
									hashes.add(hash);
								}

								dbCheckTime += (System.currentTimeMillis() - f1);
								if (fileSearchDone && files.isEmpty()) {
									dbCheckDone = true;
									return;
								}
							}
						}
					});
					dbCheck.start();

					// read file metadata
					fileReadDone = false;
					Thread fileRead = new Thread(new Runnable() {
						public void run() {
							while (!kill && !fileReadDone) {
								long f1 = System.currentTimeMillis();

								Iterator<Entry<String, File>> it = toRead
										.entrySet().iterator();
								while (it.hasNext()) {
									Entry<String, File> f = it.next();
									it.remove();
									try {
										Song s = getSong(f.getValue(),
												f.getKey());
										songs.add(s);
										addSongCount++;
									} catch (IOException e) {
										Logger.warn(
												"Failed to read music file "
														+ f.getValue(), e);
										failedSongCount++;
									}
								}

								fileReadTime += (System.currentTimeMillis() - f1);
								if (dbCheckDone && toRead.isEmpty()) {
									fileReadDone = true;
									return;
								}
							}
						}
					});
					fileRead.start();

					// resize images
					resizeDone = false;
					Thread resize = new Thread(new Runnable() {
						public void run() {
							while (!kill && !resizeDone) {
								long f1 = System.currentTimeMillis();
								while (!songs.isEmpty()) {
									Song s = songs.remove();
									String path = null;
									Map<String, String> m = artworks
											.get(s.artist.name);
									if (m != null
											&& m.containsKey(s.album.name)) {
										path = m.get(s.album.name);
									}
									if (path != null) {
										if (new File(path + "_SCALED.jpg")
												.exists()) {
											path = path + "_SCALED.jpg";
										} else {
											try {
												path = resizeArtwork(path);
											} catch (IOException e) {
												Logger.warn(
														"Failed to resize image",
														e);
											}
										}
										s.album.artwork_path = path;
									}
									toInsert.add(s);
								}
								resizeTime += (System.currentTimeMillis() - f1);

								if (fileReadDone && songs.isEmpty()) {
									resizeDone = true;
									return;
								}
							}
						}
					});
					resize.start();

					// insert Songs in DB
					Thread dbInsert = new Thread(new Runnable() {
						public void run() {
							while (!kill) {
								long f1 = System.currentTimeMillis();
								while (!toInsert.isEmpty()) {
									Song s = toInsert.remove();
									try {
										DB.get().addSong(s);
									} catch (SQLException e) {
										Logger.warn("Failed to insert in DB "
												+ s.filepath, e);
										failedSongCount++;
									}
									songsDone++;
									percentDone = songsDone
											/ ((float) songsTodo);

									float songsPerSec = songsDone
											/ ((System.currentTimeMillis() - t1) / 1000f);
									secondsLeft = (long) ((songsTodo - songsDone) / songsPerSec);
								}
								dbInsertTime += (System.currentTimeMillis() - f1);

								if (resizeDone && toInsert.isEmpty()) {
									return;
								}
							}
						}
					});
					dbInsert.start();
					try {
						dbInsert.join();
					} catch (InterruptedException e3) {
						Logger.warn("Library indexer interrupted", e3);
					}

					// remove files from DB that were not found
					int removed = 0;
					long r1 = System.currentTimeMillis();
					try {
						removed = DB.get().removeSongs(hashes);
					} catch (SQLException e3) {
						Logger.error("Failed to remove songs", e3);
					}
					long dbRemoveTime = (System.currentTimeMillis() - r1);

					// update statistics
					long u1 = System.currentTimeMillis();
					try {
						DB.get().updateSongCount();
					} catch (SQLException e1) {
						Logger.error("Failed to update song count", e1);
					}
					long dbUpdateTime = (System.currentTimeMillis() - u1);

					try {
						RuntimeStats.get().songCount.set(DB.get()
								.getSongCount());
						RuntimeStats.get().albumCount.set(DB.get()
								.getAlbumCount());
						RuntimeStats.get().artistCount.set(DB.get()
								.getArtistCount());
						RuntimeStats.get().playlistCount.set(DB.get()
								.getPlaylistCount());
						RuntimeStats.get().userCount.set(DB.get()
								.getUserCount());
						RuntimeStats.get().playtime.set(DB.get()
								.getTotalSongDuration());
					} catch (SQLException e) {
						Logger.error("Failed to update runtime statistics", e);
					}

					working = false;

					long t2 = (System.currentTimeMillis() - t1);
					Logger.info("Processed " + songsDone + " files " //
							+ "(add:" + addSongCount + "," //
							+ "skip:" + skipSongCount + "," //
							+ "fail:" + failedSongCount + "," //
							+ "rem:" + removed + ")");
					Logger.info("Indexer took " + t2 + " ("
							+ ((float) songsDone / ((float) t2 / 1000))
							+ " /s) (" //
							+ "search:" + fileSearchTime + "," //
							+ "check:" + dbCheckTime + ","//
							+ "read:" + fileReadTime + "," //
							+ "resize:" + resizeTime + "," //
							+ "insert:" + dbInsertTime + "," //
							+ "remove:" + dbRemoveTime + "," //
							+ "update:" + dbUpdateTime + ")");

					if (Thread.interrupted()) {
						Logger.warn("Library indexer has been interrupted");
					} else {
						int seconds = Config.getMusicRefreshRate();
						try {
							Thread.sleep(seconds * 1000);
						} catch (InterruptedException e) {
							Logger.warn("Library indexer interrupted", e);
						}
					}
				}
			}

		};
		this.thread = new Thread(timer, "MusicIndexer");
	}

	private void startIndexing() {
		this.thread.start();
	}

	private void listFiles(File dir, Queue<File> acc) throws IOException,
			InterruptedException {
		if (kill)
			throw new InterruptedException();

		if (!dir.isDirectory()) {
			throw new IOException(dir.getAbsolutePath() + " is not a directory");
		}

		File[] children = dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				String name = pathname.getName().toLowerCase();
				for (String format : Config.getMusicFormats()) {
					if (name.endsWith(format)) {
						return true;
					}
				}
				if (pathname.isDirectory()) {
					return true;
				}
				return false;
			}
		});

		for (File child : children) {
			if (child.isDirectory()) {
				listFiles(child, acc);
			} else {
				acc.add(child);
				songsTodo++;
			}
		}
	}

	private Song getSong(File mp3, String hash) throws IOException {
		Song song = new Song();
		Album album = new Album();
		Artist artist = new Artist();

		AudioFile af = null;
		try {
			af = AudioFileIO.read(mp3);
		} catch (Throwable e) {
			throw new IOException(e);
		}
		Tag tag = af.getTag();

		if (tag == null) {
			Logger.warn("No tag for file " + mp3.getAbsolutePath());
			song.title = "";
			song.position = 0;
			album.date = "";
			artist.name = "";
		} else {
			song.title = tag.getFirst(FieldKey.TITLE);
			String pos = tag.getFirst(FieldKey.TRACK);
			Matcher mat = positionPattern.matcher(pos);
			if (mat.matches() && mat.groupCount() == 1) {
				song.position = Integer.parseInt(mat.group(1));
			}
			album.date = tag.getFirst(FieldKey.YEAR);
			if (!album.date.matches("[0-9]{4}")) {
				album.date = "";
			}
			album.name = tag.getFirst(FieldKey.ALBUM);
			album.genre = tag.getFirst(FieldKey.GENRE);
			if (album.genre.length() > 63) {
				album.genre = album.genre.substring(0, 63);
			}

			String discNo = tag.getFirst(FieldKey.DISC_NO);
			if (discNo != null && discNo.trim().length() > 0) {
				mat = positionPattern.matcher(discNo);
				if (mat.matches() && mat.groupCount() == 1) {
					song.disc_no = Integer.parseInt(mat.group(1));
				}
				try {
					song.disc_no = Integer.parseInt(discNo);
				} catch (Throwable t) {
				}
			}

			artist.name = tag.getFirst(FieldKey.ALBUM_ARTIST);
			if (artist.name == null || artist.name.trim().length() == 0) {
				artist.name = tag.getFirst(FieldKey.ARTIST);
			}

			Map<String, String> art = artworks.get(artist.name);
			boolean hasArt = (art != null && art.containsKey(album.name));
			if (!hasArt) {
				File artwork = null;
				Artwork at = tag.getFirstArtwork();

				String fileName = artist.name.replaceAll("/|\\\\|\\?", "_")
						+ "___" + album.name.replaceAll("/|\\\\|\\?", "_");

				// tag exists and may contain artwork
				if (at != null) {
					artwork = new File(Config.getArtworkPath()
							+ File.separatorChar + fileName);
					// artwork may already exist from previous run / song
					if (!artwork.exists()) {

						byte[] img = null;
						String url = null;
						try {
							img = at.getBinaryData();
						} catch (Throwable t) {
							// the tag reading lib can throws funny stuff
							Logger.warn(
									"Failed to read image in "
											+ mp3.getAbsolutePath(), t);
						}
						try {
							url = at.getImageUrl();
						} catch (Throwable t) {
							Logger.warn(
									"Failed to read image url in "
											+ mp3.getAbsolutePath(), t);
						}

						if (url != null && url.trim().length() > 0) {
							Logger.info("GOT URL " + url);
						}

						// found image in tag... best case scenario
						if (img != null) {
							FileOutputStream fos = new FileOutputStream(artwork);
							fos.write(img);
							fos.close();
						}
					}
				}

				// no tag, take a semi-random file in folder
				if (artwork == null || !artwork.exists()) {
					artwork = null;
					// search inside current directory
					File dir = mp3.getParentFile();
					File[] matches = dir.listFiles(artworkFilter);
					if (matches.length > 0) {
						File dest = new File(Config.getArtworkPath()
								+ File.separatorChar + fileName);
						Files.copy(matches[0], dest);
						artwork = dest;
					} else {
						// take first image in folder! probably wrong..
						matches = dir.listFiles(artworkFallback);
						if (matches.length > 0) {
							File dest = new File(Config.getArtworkPath()
									+ File.separatorChar + fileName);
							Files.copy(matches[0], dest);
							artwork = dest;
						} else {
							// no artwork found...
							artwork = null;
						}
					}
				}
				if (artwork != null && artwork.exists()) {
					Map<String, String> m = artworks.get(artist.name);
					if (m == null) {
						m = new ConcurrentHashMap<String, String>();
						artworks.put(artist.name, m);
					}
					if (!m.containsKey(album.name)) {
						m.put(album.name, artwork.getAbsolutePath());
					}
				}
			}
		}

		song.filepath = mp3.getAbsolutePath();
		song.hash = hash;
		song.album = album;
		song.artist = artist;
		song.filepath = mp3.getAbsolutePath();
		song.duration = af.getAudioHeader().getTrackLength();
		song.hash = hash;
		song.album_name = album.name;
		song.artist_name = artist.name;
		album.artist_name = artist.name;

		String format = af.getAudioHeader().getEncodingType();
		if ("mp3".equalsIgnoreCase(format)) {
			song.format = "audio/mpeg";
		} else if ("aac".equalsIgnoreCase(format)) {
			song.format = "audio/aac";
		} else if (format.toLowerCase().startsWith("ogg")) {
			song.format = "audio/ogg";
		} else {
			// maybe FLAC and WAV re not unknown,
			// but they make little sense over a streaming server.
			// maybe when there is transcoding we'll see about it..
			throw new IOException("Unknown format: " + format);
		}

		if (song.title.trim().isEmpty()) {
			song.title = mp3.getName();
		}
		if (artist.name.trim().isEmpty()) {
			artist.name = "";
		}
		if (album.name.trim().isEmpty()) {
		}

		return song;
	}

	private static String resizeArtwork(String artPath) throws IOException {
		BufferedImage orig = ImageIO.read(new File(artPath));

		if (orig == null) {
			return artPath;
		}
		Image sc = orig.getScaledInstance(70, 70, Image.SCALE_SMOOTH);

		BufferedImage scaled = new BufferedImage(70, 70,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaled.createGraphics();
		g.drawImage(sc, 0, 0, 70, 70, null);
		g.dispose();

		File ret = new File(artPath + "_SCALED.jpg");
		ImageIO.write(scaled, "JPG", ret);

		return ret.getAbsolutePath();
	}

	private static void stfuLog4j() {
		Properties props = new Properties();
		// props.setProperty("org.jaudiotagger.level",
		// Level.WARNING.toString());
		props.setProperty(".level", Level.OFF.toString());
		// props.setProperty("handlers",
		// "java.util.logging.ConsoleHandler,java.util.logging.FileHandler");
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			props.store(baos, null);
			byte[] data = baos.toByteArray();
			baos.close();
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			LogManager.getLogManager().readConfiguration(bais);
		}

		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
