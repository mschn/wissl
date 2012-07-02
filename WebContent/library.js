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

/*global $, localStorage, player, window, document, console, History */

var wsl = wsl || {};

(function (wsl) {
	'use strict';

	wsl.displayArtists = function (scroll) {
		wsl.lockUI();
		$.ajax({
			url : "/wissl/artists",
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var ar, content, i, j, artist, name, clazz, liclass, onclick, artworks;
				ar = data.artists;
				content = '<ul>';
				i = 0;

				for (i = 0; i < ar.length; i += 1) {
					artist = ar[i].artist;
					artworks = ar[i].artworks;
					name = artist.name;
					clazz = 'name';
					if (name === '') {
						clazz += ' no-metadata';
						name = 'no metadata';
					}

					liclass = (i % 2 ? 'odd' : '');
					if (player.playing && player.playing.artist_id === artist.id) {
						liclass += ' playing';
					}
					onclick = 'onclick="wsl.load(\'?albums/' + artist.id + '\')"';

					content += '<li ' + onclick + ' id="artist-' + artist.id + '" class="' + liclass + '">';
					content += '<div class="artists-info">';
					content += '<span class="' + clazz + '">' + name + '</span>';
					content += '<span class="duration">' + wsl.formatSeconds(artist.playtime) + '</span>';
					content += '<span class="albums">' + artist.albums + '</span>';
					content += '<span class="songs">' + artist.songs + '</span>';
					content += '</div>';
					content += '<div class="artists-artworks">';
					for (j = 0; j < artworks.length && j < 10; j += 1) {
						content += '<img src="/wissl/art/' + artworks[j] + '" />';
					}
					content += '</div>';
					content += '</li>';
				}
				content += "</div>";
				wsl.refreshNavbar({
					artists : true
				});
				wsl.showContent({
					artists : content,
					scroll : scroll
				});
				wsl.unlockUI();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Failed to get artists", xhr);
				wsl.unlockUI();
			}
		});
	};

	wsl.displayAlbums = function (id, scroll) {
		wsl.lockUI();
		$.ajax({
			url : "/wissl/albums/" + id,
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var artist, albums, content, album, i, name, clazz, liclass, events, hastag;
				artist = data.artist;
				albums = data.albums;
				content = "<ul>";
				hastag = true;
				for (i = 0; i < albums.length; i += 1) {
					album = albums[i];
					clazz = 'name';
					name = album.name;
					if (name === '') {
						clazz += ' no-metadata';
						name = 'no metadata';
						hastag = false;
					}

					liclass = ' selectable' + (i % 2 ? ' odd' : '');
					if (player.playing && player.playing.album_id === album.id) {
						liclass += ' playing';
					}
					events = 'onmousedown="wsl.mouseDown(this,event);return false" ';

					content += '<li id="album-' + album.id + '" class="' + liclass + '">';
					content += '<span class="album-id">' + album.id + '</span>';
					content += '<span ' + events + ' class="select-box">&nbsp</span>';
					content += '<span class="before">' + album.date + '</span>';
					content += '<span onclick="wsl.load(\'?songs/' + album.id + '\')" class="' + clazz + '">';
					if (album.artwork) {
						content += '<img src="/wissl/art/' + album.id + '" />';
					} else {
						if (hastag) {
							content += '<img src="img/no-artwork.jpg" />';
						}
					}
					content += name + '</span>';
					content += '<span class="duration">' + wsl.formatSeconds(album.playtime) + '</span>';
					content += '<span class="album-count">' + album.songs + ' song' + (album.songs > 1 ? 's' : '') + '</span>';
					content += '</li>';
				}
				content += "</ul>";

				wsl.showContent({
					library : content,
					scroll : scroll
				});
				wsl.refreshNavbar({
					artist : {
						id : artist.id,
						name : artist.name
					}
				});
				wsl.unlockUI();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Failed to get albums for " + id, xhr);
				wsl.unlockUI();
			}
		});
	};

	wsl.displaySongs = function (id, scroll) {
		wsl.lockUI();
		$.ajax({
			url : "/wissl/songs/" + id,
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var artist, album, songs, content, i, song, liclass, events, name;
				artist = data.artist;
				album = data.album;
				songs = data.songs;
				name = album.name;
				if (name === '') {
					name = 'no metadata';
				}

				content = '<div id="library-heading">';
				if (album.artwork) {
					content += '<img src="/wissl/art/' + album.id + '" />';
				} else {
					content += '<img src="img/no-artwork.jpg" />';
				}

				content += '<span class="library-heading-title">' + name + '</span>';
				content += '<span class="library-heading-link">' + artist.name + '</span>';
				content += '<span class="library-heading-duration">' + wsl.formatSeconds(album.playtime) + '</span>';
				content += '</div>';

				content += '<ul>';
				for (i = 0; i < songs.length; i += 1) {
					song = songs[i];

					liclass = 'selectable' + (i % 2 ? ' odd' : '');
					if (player.playing && player.playing.song_id === song.id) {
						liclass += ' playing';
					}
					events = 'onmousedown="wsl.mouseDown(this,event);return false" ';

					content += '<li id="song-' + song.id + '" class="' + liclass + '">';
					content += '<span ' + events + ' class="select-box">&nbsp</span>';
					content += '<span class="song-id">' + song.id + '</span>';
					content += '<span class="before">' + song.position + '</span>';
					content += '<span class="title" onclick="wsl.playAlbum(' + album.id + ',' + song.id + ',' + song.position + ')">' + song.title + '</span>';
					content += '<span class="duration">' + wsl.formatSeconds(song.duration) + '</span></li>';
				}
				content += "</ul>";

				wsl.showContent({
					library : content,
					scroll : scroll
				});
				wsl.refreshNavbar({
					artist : {
						id : artist.id,
						name : artist.name
					},
					album : {
						id : album.id,
						name : album.name
					}
				});
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to get songs for " + id, xhr);
				wsl.unlockUI();
			}
		});
	};

	wsl.showAddToPlaylist = function (e) {
		wsl.showDialog('add-to-playlist-dialog');
		wsl.lockUI();
		$.ajax({
			url : "/wissl/playlists",
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var radioItems = "", i, p, inputName, labelClass, labelText;
				for (i = 0; i < data.playlists.length; i += 1) {
					p = data.playlists[i];
					inputName = 'playlist - radio - ' + i;
					labelClass = 'class="radio-label"';
					labelText = p.name;
					if (player.playing && player.playing.playlist_id === p.id) {
						labelText += ' <strong>(playing)</strong>';
					}

					radioItems += '<p><input type="radio" id="' + inputName + '" name="playlist" value="' + p.id + '" required="required"/>';
					radioItems += '<label ' + labelClass + ' for="' + inputName + '">' + labelText + '</label></p>';
				}
				radioItems += '<p><input type="radio" id="playlist-radio-new" name="playlist" value="-1" required="required"/>';
				radioItems += '<label class="radio-label" for="playlist-radio-new">';
				radioItems += '<input type="text" class="dialog-text-input" placeholder="New playlist" ';
				radioItems += 'id="playlist-radio-new-text"/></label></p>';

				$('#add-to-playlist-dialog .dialog-form-input').html(radioItems);

				$('#playlist-radio-new').on('click', function () {
					$('#playlist-radio-new-text').focus();
				});

				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to get playlists", xhr);
				wsl.unlockUI();
			}
		});
	};

	wsl.cancelAddToPlaylist = function () {
		$('#dialog-mask').hide();
		$('#add-to-playlist-dialog').hide();
	};

	wsl.addToPlaylist = function () {
		var song_ids = [], album_ids = [], playlist_id, playlist_name;

		$('.selected .song-id').each(function (index) {
			song_ids[index] = parseInt(this.innerHTML, 10);
		});
		$('.selected .album-id').each(function (index) {
			album_ids[index] = parseInt(this.innerHTML, 10);
		});

		if (song_ids.length === 0 && album_ids.length === 0) {
			return;
		}

		playlist_id = parseInt($('#add-to-playlist-dialog input[type=radio]:checked').val(), 10);

		// new playlist
		if (playlist_id === -1) {
			playlist_name = $('#playlist-radio-new-text').val();

			wsl.lockUI();
			$.ajax({
				url : '/wissl/playlist/create-add',
				headers : {
					"sessionId" : wsl.sessionId
				},
				dataType : "json",
				type : "POST",
				data : {
					"name" : playlist_name,
					"song_ids" : song_ids,
					"album_ids" : album_ids
				},
				success : function (data) {
					wsl.unlockUI();
				},
				error : function (xhr) {
					wsl.ajaxError("Failed to add songs to playlist", xhr);
					wsl.unlockUI();
				}
			});

		} else {

			wsl.lockUI();
			$.ajax({
				url : '/wissl/playlist/' + playlist_id + '/add',
				headers : {
					"sessionId" : wsl.sessionId
				},
				dataType : "json",
				type : "POST",
				data : {
					"song_ids" : song_ids,
					"album_ids" : album_ids
				},
				success : function (data) {
					wsl.unlockUI();
				},
				error : function (xhr) {
					wsl.ajaxError("Failed to add songs to playlist", xhr);
					wsl.unlockUI();
				}
			});
		}
		wsl.clearSelection();
		wsl.cancelAddToPlaylist();
	};

}(wsl));
