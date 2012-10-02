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

	wsl.displayPlaylists = function (scroll) {
		wsl.lockUI();
		$.ajax({
			url : "wissl/playlists",
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var playlists = data.playlists, content = "<ul>", i, playlist, events;
				for (i = 0; i < playlists.length; i += 1) {
					playlist = playlists[i];
					events = 'onmousedown="wsl.mouseDown(this,event);return false" ';

					content += '<li class="selectable ' + (i % 2 ? 'odd' : '') + '">';
					content += '<span class="playlist-id">' + playlist.id + '</span>';
					content += '<span ' + events + ' class="select-box">&nbsp</span>';
					content += '<span class="name">';
					content += '<a onclick="wsl.load(\'?playlist/' + playlist.id + '\')">' + playlist.name + '</a>';
					content += '</span>';
					content += '<span class="duration">' + wsl.formatSeconds(playlist.playtime) + '</span>';
					content += '<span class="album-count">' + playlist.songs + ' songs</span>';
					content += '</li>';
				}
				content += "</ul>";

				wsl.showContent({
					library : content,
					scroll : scroll
				});
				wsl.refreshNavbar({
					playlists : true
				});
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to get playlists", xhr);
				wsl.unlockUI();
			}
		});
	};

	wsl.displayPlaylist = function (pid, scroll) {
		var id = parseInt(pid, 10);
		wsl.lockUI();
		$.ajax({
			url : "wissl/playlist/" + id + "/songs",
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var songs, content, i, song, play, li_id, liclass, art_name, alb_name, c1, c2, events, doScroll;
				songs = data.playlist;
				content = "<ul>";
				for (i = 0; i < songs.length; i += 1) {
					song = songs[i];
					play = 'onclick="wsl.play(' + song.id + ',' + id + ',\'' + data.name + '\',' + i + ',event)"';
					li_id = 'id="playlist-' + id + '-' + i + '"';
					liclass = 'selectable ' + (i % 2 ? 'odd' : '');
					if (player.playing && player.playing.playlist_id === id && player.playing.song_id === song.id) {
						liclass += ' playing';
						player.playing.position = i;
					}

					c1 = 'playlist-span playlist-artist';
					art_name = song.artist_name;
					if (art_name === '') {
						art_name = 'no metadata';
						c1 += ' playlist-no-metadata';
					}

					c2 = 'playlist-span playlist-album';
					alb_name = song.album_name;
					if (alb_name === '') {
						alb_name = 'no metadata';
						c2 += ' playlist-no-metadata';
					}
					events = 'onmousedown="wsl.mouseDown(this,event);return false" ';

					content += '<li class="' + liclass + '" ' + li_id + '>';
					content += '<span class="song-id">' + song.id + '</span>';
					content += '<span ' + events + ' class="select-box">&nbsp</span>';
					content += '<span onclick="wsl.load(\'?albums/' + song.artist_id + '\')"class="' + c1 + '">' + art_name + '</span>';
					content += '<span onclick="wsl.load(\'?songs/' + song.album_id + '\')"class="' + c2 + '">' + alb_name + '</span>';
					content += '<span class="playlist-span playlist-title" ' + play + '>' + song.title + '</span>';
					content += '<span class="playlist-span playlist-duration">' + wsl.formatSeconds(song.duration) + '</span></li>';
					content += '</li>';
				}
				content += "</ul>";

				doScroll = (player.playing && player.playing.playlist_id === id && player.playing.position <= songs.length);

				wsl.showContent({
					library : content,
					scroll : (doScroll ? undefined : scroll)
				});
				wsl.refreshNavbar({
					playlist : {
						id : id,
						name : data.name
					}
				});

				if (doScroll) {
					scroll = $("#playlist-" + id + '-' + player.playing.position).offset().top - 75;
					$('body, html').scrollTop(scroll);
				}

				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to get playlists", xhr);
				wsl.unlockUI();
			}
		});
	};

	wsl.play = function (song_id, playlist_id, playlist_name, position, event) {
		event.stopPropagation();
		player.play({
			song_id : song_id,
			playlist_id : playlist_id,
			playlist_name : playlist_name,
			position : position
		});
	};

	wsl.playAlbum = function (album_id, song_id, position) {
		wsl.lockUI();
		$.ajax({
			url : 'wissl/playlist/create-add',
			headers : {
				'sessionId' : wsl.sessionId
			},
			dataType : 'json',
			type : 'POST',
			data : {
				name : 'Quick playlist',
				album_ids : [ album_id ],
				clear : true
			},
			success : function (data) {
				var playlist = data.playlist;
				player.play({
					song_id : song_id,
					playlist_id : playlist.id,
					playlist_name : playlist.name,
					position : position
				});
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to add album to quick playlist", xhr);
				wsl.unlockUI();
			}
		});
	};

	wsl.randomPlaylist = function () {
		var cb = function () {
			wsl.lockUI();
			$.ajax({
				url : 'wissl/playlist/random',
				headers : {
					"sessionId" : wsl.sessionId
				},
				dataType : "json",
				type : "POST",
				data : {
					name : 'Random',
					number : 20
				},
				success : function (data) {
					player.play({
						song_id : data.first_song,
						playlist_id : data.playlist.id,
						playlist_name : 'Random',
						position : 0
					});
					History.replaceState(null, document.title, '?playlist/' + data.playlist.id);
				},
				error : function (xhr) {
					wsl.ajaxError("Failed to get playlists", xhr);
					wsl.unlockUI();
				}
			});
		};

		if (player.playing && player.playing.playlist_name === 'Random') {
			wsl.confirmDialog('', 'Reshuffle Random playlist?', function () {
				cb();
			}, function () {
				History.replaceState(null, document.title, '?playing/');
			});
		} else {
			cb();
		}
	};

	wsl.deletePlaylist = function (playlist_id) {
		var ids = [];
		$('.selected .playlist-id').each(function (index) {
			ids[index] = parseInt(this.innerHTML, 10);
		});
		if (ids.length === 0) {
			return;
		}

		wsl.lockUI();
		$.ajax({
			url : "wissl/playlists/remove",
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			type : "POST",
			data : {
				"playlist_ids" : ids
			},
			success : function (data) {
				wsl.displayPlaylists();
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to remove playlists", xhr);
				wsl.unlockUI();
			}
		});
		wsl.clearSelection();
	};

	wsl.deleteSelectedSongs = function (playlist_id) {
		var ids = [];
		$('.selected .song-id').each(function (index) {
			ids[index] = parseInt(this.innerHTML, 10);
		});
		if (ids.length === 0) {
			return;
		}

		wsl.lockUI();
		$.ajax({
			url : "wissl/playlist/" + playlist_id + "/remove",
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			type : "POST",
			data : {
				"song_ids" : ids
			},
			success : function (data) {
				wsl.displayPlaylist(playlist_id);
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to remove songs", xhr);
				wsl.unlockUI();
			}
		});
		wsl.clearSelection();
	};

	wsl.showCreatePlaylist = function () {
		wsl.showDialog('playlist-create-dialog');
		$('#playlist-name').focus();
		wsl.hideDialog = wsl.cancelCreatePlaylist;
	};

	wsl.createPlaylist = function (name) {
		var playlistName = $('#playlist-name').val();

		wsl.lockUI();
		$.ajax({
			url : "wissl/playlist/create",
			headers : {
				"sessionId" : wsl.sessionId
			},
			type : "POST",
			data : {
				"name" : playlistName
			},
			dataType : "json",
			success : function (data) {
				wsl.displayPlaylists();
				wsl.cancelCreatePlaylist();
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to create playlist", xhr);
				wsl.cancelCreatePlaylist();
				wsl.unlockUI();
			}
		});
	};

	wsl.cancelCreatePlaylist = function () {
		$('#dialog-mask').hide();
		$('#playlist-name').val('');
		$('#playlist-create-dialog').hide();
	};

}(wsl));
