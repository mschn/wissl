package fr.msch.wissl.util;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.common.Config;
import fr.msch.wissl.server.DB;
import fr.msch.wissl.server.Library;
import fr.msch.wissl.server.Logger;
import fr.msch.wissl.server.Song;
import fr.msch.wissl.util.exception.FileOrganizerException;

/**
 * This class allows Wissl to organize you files into your music libraries
 * 
 * @author alexandre.trovato@gmail.com
 * 
 */
public class FileOrganizer {

	/** Static instance of the module */
	private static FileOrganizer instance = null;

	/** Default directory format */
	private static final String DEFAULT_DIRECTORY_FORMAT = "%artist%"
			+ File.separator + "%album%";

	/** Default filename format */
	private static final String DEFAULT_FILENAME_FORMAT = "%position% %title%%format%";

	/** Managed files/folders to remove after organizing */
	private final Map<String, Set<File>> foldersToRemove;

	/** Managed files/folders to NOT remove after organizing */
	private final Map<String, Set<File>> foldersToNotRemove;

	/** Building static instance */
	public static void create() {
		instance = new FileOrganizer();
	}

	/**
	 * Return the instance
	 * 
	 * @return The FileOrganizer instance
	 */
	public static FileOrganizer get() {
		return instance;
	}

	/**
	 * Constructor
	 */
	private FileOrganizer() {
		this.foldersToRemove = new HashMap<String, Set<File>>();
		this.foldersToNotRemove = new HashMap<String, Set<File>>();
	}

	/**
	 * Organize file into the music library corresponding to the song
	 * 
	 * @param file
	 *            The file to organize
	 * @param song
	 *            The song corresponding to the file
	 */
	public void organizeSong(File file, Song song) {
		if (Config.isFileOrganizerEnabled()) {
			this.organizeSong(this.findLibraryPath(song.filepath), file, song);
		}
	}

	/**
	 * Organize file into the music library corresponding to the artwork
	 * 
	 * @param file
	 *            The file to organize
	 * @param song
	 *            The song corresponding to the file
	 */
	public void organizeArtwork(File file, Song song) {
		boolean isValidSongObj = (song != null && song.album != null && song.album.artwork_path != null);

		if (Config.isFileOrganizerEnabled() && isValidSongObj) {
			this.organizeArtwork(this.findLibraryPath(song.filepath), file,
					song);
		}
	}

	/**
	 * Organize file into the music library corresponding to the song
	 * 
	 * @param musicPath
	 *            The music library path
	 * @param sourceFile
	 *            The file to organize
	 * @param song
	 *            The song corresponding to the file
	 */
	private void organizeSong(String musicPath, File sourceFile, Song song) {
		try {
			this.checkArguments(musicPath, sourceFile, song);

			String libPath = this.getDefaultLibrary(musicPath);
			String newFileDir = libPath + File.separator
					+ this.buildDestinationDirectory(song);
			String newFilePath = newFileDir + File.separator
					+ this.buildSongFilename(song);

			if (song.filepath == newFilePath) {
				return;
			}

			File destFile = new File(newFilePath);
			File destDir = new File(newFileDir);

			this.createDestinationDirectory(destDir);

			try {
				this.moveFile(sourceFile, destFile);

				this.updateSongLocation(song, newFilePath);
			} catch (Throwable e) {
				this.putFileToRemove(libPath, destDir);
				this.putFileToNotRemove(musicPath, sourceFile.getParentFile());

				throw new FileOrganizerException(e);
			}

			this.putFileToNotRemove(libPath, destDir);
			this.putFileToRemove(musicPath, sourceFile.getParentFile());
		} catch (FileOrganizerException e) {
			Logger.error("FileOrganizer : cannot organize file", e);
		}
	}

	/**
	 *  Organize file into the music library corresponding to the artwork file
	 * @param musicPath
	 *            The music library path
	 * @param sourceFile
	 *            The file to organize
	 * @param song
	 *            The song corresponding to the file
	 */
	private void organizeArtwork(String musicPath, File sourceFile, Song song) {
		try {
			this.checkArguments(musicPath, sourceFile, song);

			String libPath = this.getDefaultLibrary(musicPath);
			String newFileDir = libPath + File.separator
					+ this.buildDestinationDirectory(song);
			String newFilePath = newFileDir + File.separator
					+ sourceFile.getName();

			File destFile = new File(newFilePath);

			if (!destFile.exists()) {
				File destDir = new File(newFileDir);

				this.createDestinationDirectory(destDir);

				this.moveFile(sourceFile, destFile);
			}
		} catch (FileOrganizerException e) {
			Logger.error("FileOrganizer : cannot organize artwork", e);
		}
	}

	/**
	 * TODO a commenter
	 * @param musicPath
	 * @return
	 */
	private String getDefaultLibrary(String musicPath) {
		String configLibrary = Config.getFileOrganizerLibrary();

		if (configLibrary == null || configLibrary.isEmpty()) {
			return musicPath;
		}

		return configLibrary;
	}

	/**
	 * Cleans all library path which are organized
	 */
	public void clean() {
		if (Config.isFileOrganizerEnabled()) {
			for (String musicPath : this.foldersToRemove.keySet()) {
				Set<File> folderList = this.foldersToRemove.get(musicPath);

				if (this.foldersToNotRemove.containsKey(musicPath)) {
					folderList
							.removeAll(this.foldersToNotRemove.get(musicPath));
				}

				Logger.info("Cleaning " + musicPath + "...");

				this.cleanLibrary(musicPath, folderList, true);
			}

			this.foldersToRemove.clear();
			this.foldersToNotRemove.clear();
		}
	}

	/**
	 * Cleans one music library which is organized
	 * 
	 * @param musicPath The music library path
	 * @param folderList All folders to remove into this music library
	 * @param removeAll Set TRUE if you want to remove all included files, or FALSE to remove only the current file
	 */
	private void cleanLibrary(String musicPath, Set<File> folderList,
			boolean removeAll) {
		Set<File> newFilesToRemove = new HashSet<File>();

		for (File folder : folderList) {
			if (folder.exists() && !folder.getAbsoluteFile().equals(musicPath)) {
				File parent = folder.getParentFile();
				if (parent != null) {
					newFilesToRemove.add(parent);
				}

				if (removeAll) {
					Config.deleteRecursive(folder);
				} else {
					folder.delete();
				}
			}
		}

		if (!newFilesToRemove.isEmpty()) {
			this.cleanLibrary(musicPath, newFilesToRemove, false);
		}
	}

	/**
	 * Builds a correct directory path with song information
	 * 
	 * @param song The song containing the information
	 * @return The correct and complete directory path
	 * @throws FileOrganizerException Due to missing song information
	 */
	private String buildDestinationDirectory(Song song)
			throws FileOrganizerException {
		String result = DEFAULT_DIRECTORY_FORMAT;

		if (!song.artist_name.isEmpty()) {
			result = result.replaceAll("%artist%",
					this.formatPath(song.artist_name));
		} else if (Config.allowFileOrganizeMissingTag()) {
			result = result.replaceAll("%artist%", "Artiste inconnu");
		} else {
			throw new FileOrganizerException("Missing artist tag for file : "
					+ song.filepath);
		}

		if (!song.album_name.isEmpty()) {
			result = result.replaceAll("%album%",
					this.formatPath(song.album_name));
		} else if (Config.allowFileOrganizeMissingTag()) {
			result = result.replaceAll("%album%", "Album inconnu");
		} else {
			throw new FileOrganizerException("Missing album tag for file : "
					+ song.filepath);
		}

		return result;
	}

	/**
	 * Builds a correct file name with song information
	 * 
	 * @param song The song containing the information
	 * @return The correct and complete file name
	 * @throws FileOrganizerException Due to missing song information
	 */
	private String buildSongFilename(Song song) throws FileOrganizerException {
		if (song.title.isEmpty()) {
			throw new FileOrganizerException("Missing title tag for file "
					+ song.filepath);
		}

		String result = DEFAULT_FILENAME_FORMAT;

		if (song.position > 0) {
			result = result.replaceAll("%position%",
					String.format("%02d", song.position));
		} else if (Config.allowFileOrganizeMissingTag()) {
			result = result.replaceAll("%position%[ ]*", "");
		} else {
			throw new FileOrganizerException("Missing position tag for file : "
					+ song.filepath);
		}

		result = result.replaceAll("%title%", song.title);

		result = result.replaceAll("%format%",
				song.filepath.substring(song.filepath.lastIndexOf('.')));

		return this.formatPath(result);
	}

	/**
	 * Removes all non authorized characters from the string
	 * 
	 * @param path The string to clean
	 * @return The cleaned string
	 */
	private String formatPath(String path) {
		String result = path;

		result = result.replaceAll("[?:\\/*'\"<>|]", "_");
		result = result.replaceAll(" +", " ");
		result = result.replaceAll("^ ", "");
		result = result.replaceAll("$ ", "");

		return result;
	}

	/**
	 * Finds the library path containing a file
	 * 
	 * @param songPath The path of the file
	 * @return The library path according to the song
	 */
	private String findLibraryPath(String songPath) {
		String result = null;

		List<String> musicPaths = Config.getMusicPath();
		for (String path : musicPaths) {
			if (songPath.startsWith(path)) {
				return path;
			}
		}

		return result;
	}

	/**
	 * Prepares the current object to send in JSON to the GUI
	 * 
	 * @return A JSON object
	 */
	public String toJSON() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		str.append("\"file_organizer\": ");

		str.append('{');
		str.append("\"enabled\":" + Config.isFileOrganizerEnabled() + ", ");
		str.append("\"library\":"
				+ JSONObject.quote(Config.getFileOrganizerLibrary()));
		str.append('}');

		str.append('}');

		return str.toString();
	}

	/**
	 * Adds a file to remove at the cleaning step
	 * 
	 * @param library The library path containing the file
	 * @param folder The file to remove
	 */
	private void putFileToRemove(String library, File folder) {
		if (!this.foldersToRemove.containsKey(library)) {
			this.foldersToRemove.put(library, new HashSet<File>());
		}

		this.foldersToRemove.get(library).add(folder);
	}

	/**
	 * Keeps a file from removing at the cleaning step
	 * 
	 * @param library The library path containing the file
	 * @param folder The file to keep
	 */
	private void putFileToNotRemove(String library, File folder) {
		if (!this.foldersToNotRemove.containsKey(library)) {
			this.foldersToNotRemove.put(library, new HashSet<File>());
		}

		this.foldersToNotRemove.get(library).add(folder);
	}

	/**
	 * Builds all directory hierarchy 
	 * @param destDir The directory file
	 * @throws FileOrganizerException Unable to create directory
	 */
	private void createDestinationDirectory(File destDir)
			throws FileOrganizerException {
		if (!destDir.exists() && !destDir.mkdirs()) {
			throw new FileOrganizerException("Unable to create directory "
					+ destDir.getAbsolutePath());
		}

		destDir.mkdirs();
	}

	/**
	 * Moves a file into the music library
	 * @param sourceFile The file to move
	 * @param destFile The new destination of the file
	 * @throws FileOrganizerException Unable to move the file
	 */
	private void moveFile(File sourceFile, File destFile)
			throws FileOrganizerException {
		if (!destFile.exists()) {
			if (!sourceFile.renameTo(destFile)) {
				throw new FileOrganizerException("Cannot move file "
						+ sourceFile.getAbsolutePath() + " to "
						+ destFile.getAbsolutePath());
			}
		}
	}

	/**
	 * Updates song location into database
	 * 
	 * @param song Song to update
	 * @param newFilePath New file location
	 * @throws FileOrganizerException Error during database update
	 */
	private void updateSongLocation(Song song, String newFilePath)
			throws FileOrganizerException {
		song.filepath = newFilePath;
		song.hash = Library.getInstance().md5(newFilePath);

		try {
			if (DB.get().updateSongLocation(song) < 0) {
				throw new FileOrganizerException("Cannot update database for "
						+ song.filepath + " song");
			}
		} catch (SQLException e) {
			throw new FileOrganizerException(e);
		}
	}

	/**
	 * Checks all necessary properties to organize a song file
	 * 
	 * @param musicPath The music library containing the song
	 * @param sourceFile The real file of the song
	 * @param song The song object with all song TAGs
	 * @throws FileOrganizerException One of these property is not valid
	 */
	private void checkArguments(String musicPath, File sourceFile, Song song)
			throws FileOrganizerException {
		StringBuilder sb = new StringBuilder();

		if (musicPath == null || musicPath.isEmpty()) {
			sb.append("MusicLibrary is NULL");
			sb.append("\n");
		}

		if (sourceFile == null || musicPath.isEmpty()) {
			sb.append("MusicFile is NULL");
			sb.append("\n");
		}

		if (song == null) {
			sb.append("MusicSong is NULL");
			sb.append("\n");
		}

		if (sb.length() > 0) {
			throw new FileOrganizerException(sb.toString());
		}
	}
}