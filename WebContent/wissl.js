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
'use strict';

var wsl = {

	// id of logged user
	userId : null,
	// session id used for all ajax requests
	sessionId : null,
	// true if logged user is administrator
	admin : false,

	// number of currently selected elements
	selCount : 0,

	login : function () {
		$('#login-error').hide();
		var username = $('#username').val(), password = $('#password').val();

		wsl.lockUI();
		$.ajax({
			url : "/wissl/login",
			type : "POST",
			data : {
				"username" : username,
				"password" : password
			},
			dataType : "json",
			success : function (session) {
				var uid, auth;
				uid = parseInt(session.userId, 10);
				auth = parseInt(session.auth, 10);

				wsl.sessionId = session.sessionId;
				wsl.userId = uid;
				wsl.admin = (auth === 1);

				localStorage.setItem('sessionId', session.sessionId);
				localStorage.setItem('userId', uid);
				localStorage.setItem('auth', auth);

				$('#login').hide();

				wsl.loadContent(wsl.getCurrentHash());
				wsl.unlockUI();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Login failure", xhr);
				wsl.unlockUI();
			}
		});
	},

	logout : function () {
		wsl.confirmDialog('Logout', 'Do you really want to leave?', function () {
			var sid = wsl.sessionId;

			localStorage.removeItem('sessionId');
			localStorage.removeItem('userId');
			localStorage.removeItem('auth');

			wsl.sessionId = null;
			wsl.userId = null;
			wsl.admin = false;

			wsl.lockUI();
			$.ajax({
				url : "/wissl/logout",
				type : "POST",
				headers : {
					"sessionId" : sid
				},
				dataType : "json",
				success : function () {
					player.stop();
					wsl.unlockUI();
					History.pushState(null, document.title, '?logout');
				},
				error : function (xhr, textStatus, errorThrown) {
					wsl.ajaxError("Logout failure", xhr);
					wsl.unlockUI();
				}
			});
		}, function () {
		});
	},

	shutdown : function () {
		var msg = 'Do you really want to shutdown the server?<br>';
		msg += 'You will not be able to login util you restart the server manually!';
		wsl.confirmDialog('Shutdown server', msg, function () {
			wsl.lockUI();
			$.ajax({
				url : "wissl/shutdown",
				type : 'POST',
				headers : {
					'sessionId' : wsl.sessionId
				},
				success : function () {
					// will never be a success
				},
				error : function (xhr, textStatus, errorThrown) {
					wsl.ajaxError("Shutdown failure", xhr);
					wsl.unlockUI();
				}
			});
		}, function () {
		});
	},

	displayUsers : function (scroll) {
		wsl.lockUI();

		$.ajax({
			url : '/wissl/users',
			headers : {
				sessionId : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {
				var content = '<ul>', user, uid, i, j, liclass, sess;
				for (i = 0; i < data.users.length; i += 1) {
					user = data.users[i];
					uid = parseInt(user.id, 10);

					sess = null;
					for (j = 0; j < data.sessions.length; j += 1) {
						if (data.sessions[j].user_id === uid) {
							sess = data.sessions[j];
						}
					}

					liclass = '';
					if (uid === wsl.userId) {
						liclass += 'users-mine';
					}

					content += '<li onclick="wsl.load(\'?user/' + uid + '\')" "class="' + liclass + '">';
					content += '<span class="users-id">' + uid + '</span>';
					content += '<span class="users-name">' + user.username + '</span>';
					if (user.auth === 1) {
						content += '<span class="users-admin">admin</span>';
					}
					if (sess) {
						content += '<span class="users-connected">connected</span>';
					}
					content += '</li>';
				}
				wsl.showContent({
					users : content,
					scroll : scroll
				});
				wsl.refreshNavbar({
					users : true
				});
				wsl.unlockUI();
			},
			error : function (xhr, st, err) {
				wsl.ajaxError("Failed to get users", xhr);
				wsl.unlockUI();
			}
		});

	},

	displayUser : function (uid, scroll) {
		wsl.lockUI();

		$.ajax({
			url : '/wissl/user/' + uid,
			headers : {
				sessionId : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {
				var content = '', t1, t2, i, pl, song;

				content += '<p><span class="user-name">' + data.user.username + '</span>';
				if (data.user.auth === 1) {
					content += '<span class="user-admin">admin</span></p>';
				}
				if (data.session) {
					t1 = new Date(data.session.created_at).getTime() / 1000;
					t2 = new Date(data.session.last_activity).getTime() / 1000;
					content += '<p>Connected ' + wsl.formatSeconds(t1, true) + ' ago';
					if (data.session.origin) {
						content += ' from ' + data.session.origin;
					}
					content += '</p><p>Last activity ' + wsl.formatSeconds(t2, true) + ' ago</p>';
					if (data.session.last_played_song) {
						song = data.session.last_played_song;
						content += '<p>Last played ';
						content += '<a onclick="">' + song.title + '</a> on ';
						content += '<a onclick="wsl.load(\'?songs/' + song.album_id + '\')">' + song.album_name + '</a> by ';
						content += '<a onclick="wsl.load(\'?albums/' + song.artist_id + '\')">' + song.artist_name + '</a></p>';
					}
				} else {
					content += '<p>Not connected</p>';
				}
				content += '<p>Downloaded ' + (data.user.downloaded / (1024 * 1024)).toFixed(2) + ' MiB</p>';

				if (data.playlists && data.playlists.length > 0) {
					content += '<p class="user-title">Playlists</p>';
					content += '<ul id="user-playlist">';
					for (i = 0; i < data.playlists.length; i += 1) {
						pl = data.playlists[i];

						content += '<li onclick="wsl.load(\'?playlist/' + pl.id + '\')">';
						content += '<span class="user-playlist-name">' + pl.name + '</span>';
						content += '<span class="user-playlist-songs">' + pl.songs + ' songs</span>';
						content += '<span class="user-playlist-time">' + wsl.formatSeconds(pl.playtime) + '</span>';
						content += '</li>';
					}
					content += '</ul>';
				}

				wsl.showContent({
					user : content,
					scroll : scroll
				});
				wsl.refreshNavbar({
					user : {
						id : uid,
						name : data.user.username
					}
				});
				wsl.unlockUI();
			},
			error : function (xhr, st, err) {
				wsl.ajaxError("Failed to get user " + uid, xhr);
				wsl.unlockUI();
			}
		});
	},

	displaySettings : function (scroll) {
		wsl.showContent({
			settings : 'User settings',
			scroll : scroll
		});
		wsl.refreshNavbar({
			settings : true
		});
	},

	displayAdmin : function (scroll) {
		var cb, folders, users, click;

		wsl.lockUI();

		cb = function () {
			var i, content;

			if (!users || !folders) {
				return;
			}

			content = '<h3>Music folders</h3>';
			if (folders.length > 0) {
				content += '<ul id="admin-music-folders">';
				for (i = 0; i < folders.length; i += 1) {
					content += '<li class="selectable">';
					content += '<span onclick="wsl.toggleSelection(this.parentNode)" ';
					content += 'class="select-box">&nbsp</span>';
					content += '<span class="admin-music-folder-directory">' + folders[i] + '</span>';
					content += '</li>';
				}
				content += '</ul>';
			} else {
				content += '<p>No music folders!</p>';
			}
			content += '<div><span class="button button-add" onclick="wsl.showAddMusicFolder()">Add</span>';
			content += '<span class="button button-cancel" onclick="wsl.removeMusicFolder()">Remove</span><div>';

			content += '<h3>Users</h3>';
			content += '<ul id="admin-users-list">';
			for (i = 0; i < users.length; i += 1) {
				content += '<li class="selectable">';
				content += '<span onclick="wsl.toggleSelection(this.parentNode)" ';
				content += 'class="select-box">&nbsp</span>';
				content += '<span class="users-admin-id">' + users[i].id + '</span>';
				content += '<span>' + users[i].username + '</span>';
				if (users[i].auth === 1) {
					content += '<span class="users-admin">admin</span>';
				}
				content += '</li>';
			}
			content += '</ul>';
			content += '<div><span class="button button-add" onclick="wsl.showAddUser()">Add</span>';
			content += '<span class="button button-cancel" onclick="wsl.removeUser()">Remove</span><div>';

			content += '<h3>Other</h3>';
			cb = 'window.open(\'/wissl/logs?&sessionId=' + wsl.sessionId + '\',\'_blank\')';
			content += '<p><span class="button button-logs" onclick="' + cb + '">Server logs</span></p>';
			content += '<p><span class="button button-shutdown" onclick="wsl.shutdown()">Shutdown server</p>';

			wsl.showContent({
				admin : content,
				scroll : scroll
			});
			wsl.refreshNavbar({
				admin : true
			});
			wsl.unlockUI();
		};

		$.ajax({
			url : '/wissl/folders',
			headers : {
				'sessionId' : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {
				folders = data.folders;
				cb();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Failed to display admin", xhr);
				wsl.unlockUI();
			}
		});

		$.ajax({
			url : '/wissl/users',
			headers : {
				'sessionId' : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {
				users = data.users;
				cb();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Failed to get artists", xhr);
				wsl.unlockUI();
			}
		});
	},

	displayArtists : function (scroll) {
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
	},

	/**
	 * fetch all albums for one artist, display it in library
	 * 
	 * @param id unique artist id
	 */
	displayAlbums : function (id, scroll) {
		wsl.lockUI();
		$.ajax({
			url : "/wissl/albums/" + id,
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var artist, albums, content, album, i, name, clazz, liclass, events;
				artist = data.artist;
				albums = data.albums;
				content = "<ul>";
				for (i = 0; i < albums.length; i += 1) {
					album = albums[i];
					clazz = 'name';
					name = album.name;
					if (name === '') {
						clazz += ' no-metadata';
						name = 'no metadata';
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
						content += '<img src="img/no-artwork.jpg" />';
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
	},

	/**
	 * fetch all songs for one album, display it in library
	 * 
	 * @param id unique album id
	 */
	displaySongs : function (id, scroll) {
		wsl.lockUI();
		$.ajax({
			url : "/wissl/songs/" + id,
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var artist, album, songs, content, i, song, liclass, events;
				artist = data.artist;
				album = data.album;
				songs = data.songs;

				content = '<div id="library-heading">';
				if (album.artwork) {
					content += '<img src="/wissl/art/' + album.id + '" />';
				} else {
					content += '<img src="img/no-artwork.jpg" />';
				}
				content += '<span class="library-heading-title">' + album.name + '</span>';
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
	},

	/**
	 * fetch all playlists for the logged user, display it in library
	 */
	displayPlaylists : function (scroll) {
		wsl.lockUI();
		$.ajax({
			url : "/wissl/playlists",
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
	},

	displayPlaylist : function (pid, scroll) {
		var id = parseInt(pid, 10);
		wsl.lockUI();
		$.ajax({
			url : "/wissl/playlist/" + id + "/songs",
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
					content += '<a onclick="wsl.load(\'?albums/' + song.artist_id + '\')"class="' + c1 + '">' + art_name + '</a>';
					content += '<a onclick="wsl.load(\'?songs/' + song.album_id + '\')"class="' + c2 + '">' + alb_name + '</a>';
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
					scroll = $("#playlist-" + id + '-' + player.playing.position).offset().top - 60;
					$('body').scrollTop(scroll);
				}

				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to get playlists", xhr);
				wsl.unlockUI();
			}
		});
	},

	randomPlaylist : function () {
		var cb = function () {
			wsl.lockUI();
			$.ajax({
				url : "/wissl/playlist/random",
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
	},

	nothingPlaying : function () {
		wsl.showContent({
			library : "<span class='empty-library'>Nothing is currently playing!</span>"
		});
		wsl.refreshNavbar({});
	},

	play : function (song_id, playlist_id, playlist_name, position, event) {
		event.stopPropagation();
		player.play({
			song_id : song_id,
			playlist_id : playlist_id,
			playlist_name : playlist_name,
			position : position
		});
	},

	deleteSelectedSongs : function (playlist_id) {
		var ids = [];
		$('.selected .song-id').each(function (index) {
			ids[index] = parseInt(this.innerHTML, 10);
		});
		if (ids.length === 0) {
			return;
		}

		wsl.lockUI();
		$.ajax({
			url : "/wissl/playlist/" + playlist_id + "/remove",
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
	},

	deletePlaylist : function (playlist_id) {
		var ids = [];
		$('.selected .playlist-id').each(function (index) {
			ids[index] = parseInt(this.innerHTML, 10);
		});
		if (ids.length === 0) {
			return;
		}

		wsl.lockUI();
		$.ajax({
			url : "/wissl/playlists/remove",
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
	},

	refreshNavbar : function (arg) {
		var navbar = '', clazz, style, cb, name;
		navbar += '<hr/>';

		clazz = 'hist navbar-playing';
		style = (player.playing) ? '' : 'style="display:none;"';
		navbar += '<a ' + style + ' id="navbar-playing" class="' + clazz + '" onclick="wsl.load(\'?playing/\')">Playing</a>';

		clazz = 'hist navbar-random';
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?random\')">Random</a>';

		clazz = (arg.playlists ? 'selected-nav ' : '') + 'hist navbar-playlists';
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?playlists/\')">Playlists</a>';
		if (arg.playlists) {
			navbar += '<div class="context-action">';
			navbar += '<a class="navbar-new-playlist" onclick="wsl.showCreatePlaylist()" title="Create a new playlist"></a>';
			navbar += "<a class='selection-disabled navbar-delete-playlist' onclick='wsl.deletePlaylist()' title='Delete selected playlists'></a>";
			navbar += "</div>";
		}

		if (arg.playlist) {
			clazz = 'hist navbar-playlist indent selected-nav';
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?playlist/' + arg.playlist.id + '\')">';
			navbar += arg.playlist.name + "</a>";

			navbar += "<div class='context-action'>";
			cb = "onclick='wsl.deleteSelectedSongs(" + arg.playlist.id + ")'";
			navbar += "<a class='navbar-select-all context-action' onclick='wsl.selectAll()' title='Select all songs'></a>";
			navbar += "<a class='navbar-cancel-select context-action selection-disabled' onclick='wsl.clearSelection()' title='Cancel selection'></a>";
			navbar += "<a class='selection-disabled context-action navbar-delete-songs-playlist' " + cb + " title='Remove selected songs from playlist'></a>";
			navbar += "</div>";
		}

		clazz = (arg.artists ? 'selected-nav ' : '') + 'hist navbar-artists';
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?\')">Library</a>';

		if (arg.artist) {
			clazz = 'hist navbar-artist indent' + (arg.album ? '' : ' selected-nav');
			name = arg.artist.name;
			if (name === '') {
				name = 'no metadata';
				clazz += ' navbar-no-metadata';
			}
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?albums/' + arg.artist.id + '\')">';
			navbar += name + "</a>";
			if (!arg.album) {
				navbar += "<div class='context-action'>";
				navbar += "<a class='navbar-select-all context-action' onclick='wsl.selectAll()' title='Select all albums'></a>";
				navbar += "<a class='navbar-cancel-select context-action selection-disabled' onclick='wsl.clearSelection()' title='Cancel selection'></a>";
				navbar += "<a class='navbar-add-songs context-action selection-disabled' onclick='wsl.showAddToPlaylist()' title='Add selected songs to playlist'></a>";
				navbar += "<a class='navbar-play context-action selection-disabled' onclick='wsl.playNow()' title='Play now'></a>";
				navbar += "</div>";
			}
		}

		if (arg.album) {
			clazz = 'hist navbar-album indent selected-nav';
			name = arg.album.name;
			if (name === '') {
				name = 'no metadata';
				clazz += ' navbar-no-metadata';
			}
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?songs/' + arg.album.id + '\')">';
			navbar += name + "</a>";
			navbar += "<div class='context-action'>";
			navbar += "<a class='navbar-select-all context-action' onclick='wsl.selectAll()' title='Select all songs'></a>";
			navbar += "<a class='navbar-cancel-select context-action selection-disabled' onclick='wsl.clearSelection()' title='Cancel selection'></a>";
			navbar += "<a class='navbar-add-songs context-action selection-disabled' onclick='wsl.showAddToPlaylist()' title='Add selected songs to playlist'></a>";
			navbar += "<a class='navbar-play context-action selection-disabled' onclick='wsl.playNow()' title='Play now'></a>";
			navbar += "</div>";
		}

		navbar += "<hr/>";

		clazz = 'navbar-users';
		if (arg.users) {
			clazz += ' selected-nav';
		}
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?users/\')">Users</a>';

		if (arg.user) {
			clazz = 'navbar-user indent selected-nav';
			name = arg.user.name;
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?user/' + arg.user.id + '\')">';
			navbar += name + "</a>";
		}

		clazz = 'navbar-settings';
		if (arg.settings) {
			clazz += ' selected-nav';
		}
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?settings\')">Settings</a>';
		if (wsl.admin) {
			clazz = 'navbar-admin';
			if (arg.admin) {
				clazz += ' selected-nav';
			}
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?admin\')">Admin</a>';
		}
		navbar += "<a class='navbar-logout' onclick='wsl.logout()'>Logout</a>";

		$("#navbar").empty().append(navbar);
	},

	showContent : function (content) {
		var pages, i, page;

		pages = [ 'artists', 'library', 'users', 'user', 'settings', 'admin' ];

		for (i = 0; i < pages.length; i += 1) {
			page = pages[i];
			if (content[page]) {
				$('#' + page).fadeIn(200).empty().append(content[page]);
			} else {
				$('#' + page).empty().fadeOut(200);
			}
		}

		if (content.scroll) {
			$('body').scrollTop(content.scroll);
		}
	},

	selectionDrag : null,

	mouseDown : function (e, event) {
		var mouseup, mousemove;

		mousemove = function (event) {
			var y, y2, elt, top, height;

			top = $('.selectable').first().offset().top;
			height = $('.selectable').first().height();

			y = Math.min(wsl.selectionDrag.mouseY, event.pageY);
			y = Math.floor((y - top) / height) * height + top;

			if (wsl.selectionDrag.mouseY < event.pageY) {
				y2 = event.pageY;
			} else {
				y2 = wsl.selectionDrag.mouseY;
			}
			y2 = Math.ceil((y2 - top) / height) * height + top;

			elt = $('#library-drag-selection');
			elt.css({
				top : (y - (event.pageY - event.clientY)) + 'px'
			});
			elt.height(y2 - y);
			elt.show();
		};

		mouseup = function (event) {
			var sel = [], i;

			$('#library-drag-selection').hide();
			$(document).off('mousemove', mousemove);

			$('.selectable').each(function () {
				var elt, offset, y, h, my, my2;
				elt = $(this);
				offset = elt.offset();
				y = offset.top;
				h = elt.height();

				my = Math.min(wsl.selectionDrag.mouseY, event.pageY);
				my2 = Math.max(wsl.selectionDrag.mouseY, event.pageY);

				if (my < y + h && y < my2) {
					sel.push(elt);
				}
			});

			if (sel.length === 1) {
				wsl.toggleSelection(sel[0]);
			} else if (sel.length > 1) {
				for (i = 0; i < sel.length; i += 1) {
					wsl.select(sel[i]);
				}
			}

			wsl.selectionDrag = null;
		};

		wsl.selectionDrag = {
			element : e,
			mouseX : event.clientX,
			mouseY : event.pageY
		};

		$(document).one('mouseup', mouseup);
		$(document).on('mousemove', mousemove);
	},

	clearSelection : function () {
		$('.selected').each(function (i) {
			wsl.deselect(this);
		});
	},

	selectAll : function () {
		wsl.selCount = 0;
		$('.selectable').each(function (i) {
			wsl.select(this);
		});
	},

	select : function (e) {
		wsl.selCount += 1;
		$(e).addClass('selected');
		$(e).find('.select-box').addClass('select-box-checked');

		if (wsl.selCount === 1) {
			$('.selection-disabled').removeClass('selection-disabled').addClass('selection-enabled');
		}
	},

	deselect : function (e) {
		wsl.selCount = Math.max(0, wsl.selCount - 1);
		$(e).removeClass('selected');
		$(e).find('.select-box').removeClass('select-box-checked');

		if (wsl.selCount === 0) {
			$('.selection-enabled').removeClass('selection-enabled').addClass('selection-disabled');
		}
	},

	toggleSelection : function (e) {
		if ($(e).hasClass('selected')) {
			wsl.deselect(e);
		} else {
			wsl.select(e);
		}
	},

	playAlbum : function (album_id, song_id, position) {
		// wsl.select(this.parentNode);wsl.playNow()
		wsl.lockUI();
		$.ajax({
			url : '/wissl/playlist/create-add',
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
					position : position - 1
				});
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to add album to quick playlist", xhr);
				wsl.unlockUI();
			}
		});

	},

	playNow : function () {
		var song_ids = [], album_ids = [];
		$('.selected .song-id').each(function (index) {
			song_ids[index] = parseInt(this.innerHTML, 10);
		});
		$('.selected .album-id').each(function (index) {
			album_ids[index] = parseInt(this.innerHTML, 10);
		});

		if (song_ids.length === 0 && album_ids.length === 0) {
			return;
		}

		wsl.lockUI();
		$.ajax({
			url : '/wissl/playlist/create-add',
			headers : {
				'sessionId' : wsl.sessionId
			},
			dataType : 'json',
			type : 'POST',
			data : {
				name : 'Quick playlist',
				song_ids : song_ids,
				album_ids : album_ids,
				clear : true
			},
			success : function (data) {
				var playlist = data.playlist;
				wsl.clearSelection();

				if (data.added === 0) {
					wsl.unlockUI();
					return;
				}

				$.ajax({
					url : '/wissl/playlist/' + playlist.id + '/song/0',
					headers : {
						'sessionId' : wsl.sessionId
					},
					dataType : 'json',
					success : function (data) {
						player.play({
							song_id : data.id,
							playlist_id : playlist.id,
							playlist_name : playlist.name,
							position : 0
						});
						wsl.unlockUI();
					},
					error : function (xhr) {
						wsl.ajaxError("Failed to get first song in Quick Playlist");
						wsl.unlockUI();
					}
				});
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to add songs to Quick Playlist", xhr);
				wsl.unlockUI();
			}
		});
	},

	showAddToPlaylist : function (e) {
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
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.ajaxError("Failed to get playlists", xhr);
				wsl.unlockUI();
			}
		});
	},

	cancelAddToPlaylist : function () {
		$('#dialog-mask').hide();
		$('#add-to-playlist-dialog').hide();
	},

	addToPlaylist : function () {
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
	},

	showCreatePlaylist : function () {
		wsl.showDialog('playlist-create-dialog');
	},

	createPlaylist : function (name) {
		var playlistName = $('#playlist-name').val();

		wsl.lockUI();
		$.ajax({
			url : "/wissl/playlist/create",
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
	},

	cancelCreatePlaylist : function () {
		$('#dialog-mask').hide();
		$('#playlist-name').val('');
		$('#playlist-create-dialog').hide();
	},

	confirmDialog : function (title, message, okCallback, cancelCallback) {
		var dialog, winW, winH, w, h;
		dialog = $('#confirm-dialog');
		winW = $(window).width();
		winH = $(window).height();
		w = 400;
		h = 300;

		dialog.css('left', (winW - w) / 2);
		dialog.css('top', (winH - h) / 2);
		dialog.width(w);
		$('#confirm-dialog-title').html(title);
		$('#confirm-dialog-message').html(message);

		dialog.show();
		$('#dialog-mask').show();

		$('#confirm-dialog-ok').on('click', function () {
			$('#dialog-mask').hide();
			$('#confirm-dialog').hide();
			okCallback();
			$('#confirm-dialog-ok').off();
			$('#confirm-dialog-cancel').off();
		});
		$('#confirm-dialog-cancel').on('click', function () {
			$('#dialog-mask').hide();
			$('#confirm-dialog').hide();
			cancelCallback();
			$('#confirm-dialog-ok').off();
			$('#confirm-dialog-cancel').off();
		});
	},

	showAddUser : function () {
		wsl.showDialog('adduser-dialog');
	},

	cancelAddUser : function () {
		$('#adduser-username').val('');
		$('#adduser-password').val('');
		$('#dialog-mask').hide();
		$('#adduser-dialog').hide();
	},

	addUser : function () {
		var user, pw, auth = 2;

		user = $('#adduser-username').val();
		pw = $('#adduser-password').val();
		if ($('#adduser-auth').is(':checked')) {
			auth = 1;
		}

		wsl.lockUI();
		$.ajax({
			url : '/wissl/user/add',
			type : 'POST',
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : 'json',
			data : {
				username : user,
				password : pw,
				auth : auth
			},
			success : function (data) {
				wsl.unlockUI();
				wsl.cancelAddUser();
				wsl.displayAdmin();
			},
			error : function (xhr) {
				wsl.unlockUI();
				wsl.cancelAddUser();
				wsl.ajaxError("Failed to add user", xhr);
			}
		});
	},

	removeUser : function () {
		var sel = [];
		$('#admin-users-list .selected .users-admin-id').each(function (index) {
			sel.push(this.innerHTML);
		});

		if (sel.length > 0) {
			wsl.lockUI();
			$.ajax({
				url : '/wissl/user/remove',
				type : 'POST',
				headers : {
					sessionId : wsl.sessionId
				},
				data : {
					user_ids : sel
				},
				success : function (data) {
					wsl.unlockUI();
					wsl.displayAdmin();
				},
				error : function (xhr) {
					wsl.unlockUI();
					wsl.ajaxError("Failed to remove user", xhr);
				}
			});
		}
	},

	showAddMusicFolder : function () {
		wsl.showDialog('addmusic-dialog');
		wsl.updateAddMusicFolderListing();
	},

	updateAddMusicFolderListing : function (dir) {
		wsl.lockUI();
		$.ajax({
			url : '/wissl/folders/listing',
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			type : "GET",
			data : {
				directory : dir
			},
			success : function (data) {
				var content = '', i, d, name, cb;

				content += '<div id="addmusic-dialog-bar">';
				content += '<input id="addmusic-dialog-dirname" type="text" value="' + data.directory + '" readonly="readonly"/>';

				cb = '';
				if (data.parent) {
					cb = 'onclick="wsl.updateAddMusicFolderListing(\'' + data.parent.replace(/\\/g, "\\\\") + '\')"';
				}
				content += '<span ' + cb + ' class="button button-parent button-notext" id="addmusic-dialog-parent"></span>';
				content += '</div>';

				if (data.listing && data.listing.length > 0) {
					content += '<ul>';
					for (i = 0; i < data.listing.length; i += 1) {
						d = data.listing[i];
						if (data.directory.length > 0) {
							name = d.substring(d.lastIndexOf(data.separator) + 1);
						} else if (d === '/') {
							name = ' / ';
						} else {
							name = d;
						}

						if (name[0] !== '.') {
							cb = 'onclick="wsl.selectOrOpenMusicFolder(this,\'' + d.replace(/\\/g, "\\\\") + '\')"';
							content += '<li ' + cb + ' title=' + name + '>' + name;
							content += '<span class="dir-name">' + d + '</span>';
						}
					}
					content += '</ul>';
				} else {
					content += '<p>empty directory</p>';
				}
				$('#addmusic-dialog-content').empty().append(content);
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.unlockUI();
				wsl.cancelAddMusicFolder();
				wsl.ajaxError("Failed to get directory listing", xhr);
			}
		});
	},

	selectOrOpenMusicFolder : function (elt, dir) {
		if ($(elt).hasClass('dir-selected')) {
			wsl.updateAddMusicFolderListing(dir);
		} else {
			$('#addmusic-dialog-content ul li').removeClass('dir-selected');
			$(elt).addClass('dir-selected');
		}
	},

	addMusic : function () {
		var sel, dir;

		sel = $('#addmusic-dialog-content .dir-selected .dir-name');
		if (sel[0]) {
			dir = sel[0].innerHTML;
			$.ajax({
				url : '/wissl/folders/add',
				type : 'POST',
				headers : {
					"sessionId" : wsl.sessionId
				},
				data : {
					directory : dir
				},
				success : function (data) {
					wsl.unlockUI();
					wsl.cancelAddMusicFolder();
					wsl.displayAdmin();
				},
				error : function (xhr) {
					wsl.unlockUI();
					wsl.cancelAddMusicFolder();
					wsl.ajaxError("Failed to add music directory", xhr);
				}
			});
		}
	},

	removeMusicFolder : function () {
		var sel = [];
		$('#admin-music-folders .selected .admin-music-folder-directory').each(function (index) {
			sel.push(this.innerHTML);
		});

		if (sel.length > 0) {
			wsl.lockUI();
			$.ajax({
				url : '/wissl/folders/remove',
				type : 'POST',
				headers : {
					sessionId : wsl.sessionId
				},
				data : {
					directory : sel
				},
				success : function (data) {
					wsl.unlockUI();
					wsl.displayAdmin();
				},
				error : function (xhr) {
					wsl.unlockUI();
					wsl.ajaxError("Failed to remove music directory", xhr);
				}
			});
		}
	},

	cancelAddMusicFolder : function () {
		$('#dialog-mask').hide();
		$('#addmusic-dialog').hide();
	},

	// error during ajax request to server
	ajaxError : function (message, xhr) {
		var errorMsg, status, e;
		errorMsg = message;
		status = xhr.status;
		try {
			e = $.parseJSON(xhr.responseText);
		} catch (exc) {
			console.log('Failed to parse JSON:', exc);
		}

		console.log(xhr, xhr.responseText, status);
		if (e) {
			errorMsg = "<strong>Error " + status + "</strong><br>";
			errorMsg += message + ": " + e.message;
		} else {
			errorMsg = "Could not connect to server";
		}
		if (status === 0 || status === 401 || status === 503) {
			wsl.fatalError(errorMsg);
		} else {
			wsl.error(errorMsg);
			wsl.load('?');
		}
	},

	// display an non fatal error
	error : function (message) {
		wsl.unlockUI();
		$("#error-dialog-message").html(message);
		wsl.showDialog('error-dialog');
	},

	// fatal error, get back to login page
	fatalError : function (message) {
		player.stop();
		wsl.sessionId = null;
		wsl.userId = null;
		wsl.admin = false;

		localStorage.removeItem('sessionId');
		localStorage.removeItem('userId');
		localStorage.removeItem('auth');

		$("#login").show();
		$("#login-error").show().html(message);
	},

	// close the error box in ui
	closeError : function () {
		$('#dialog-mask').hide();
		$("#error-dialog").hide();
	},

	formatSeconds : function (seconds, alt) {
		var hou, min, sec, ret;

		hou = Math.floor(seconds / 3600);
		seconds -= (hou * 3600);
		min = Math.floor(seconds / 60);
		sec = Math.floor(seconds % 60);

		if (alt) {
			ret = (hou > 0 ? hou + 'h ' : '');
			ret += (min > 0 ? min + 'm ' : '');
			ret += sec + 's';
		} else {
			ret = (hou > 0 ? hou + ':' : '');
			ret += (min < 10 && hou > 0 ? '0' : '') + min + ':';
			ret += (sec < 10 ? '0' : '') + sec;
		}
		return ret;
	},

	// true when a callback is running and UI should be blocked
	uiLock : false,

	lockUI : function () {
		wsl.uiLock = true;
		$('#uilock').show();
	},

	unlockUI : function () {
		$('#uilock').hide();
		wsl.uiLock = false;
		$('#navbar').show();
	},

	showDialog : function (id) {
		var dialog, winW, winH, w, h;
		dialog = $('#' + id);
		winW = $(window).width();
		winH = $(window).height();
		w = 400;
		h = 300;

		dialog.css('left', (winW - w) / 2);
		dialog.css('top', (winH - h) / 2);
		dialog.width(w);

		$('#dialog-mask').show();
		dialog.show();
	},

	/**
	 * called from the html document in replacement of <a href="url">
	 */
	load : function (hash) {
		if (wsl.uiLock) {
			return;
		}

		History.replaceState({
			scroll : $('body').scrollTop()
		}, History.getState().title, History.getState().url);

		History.pushState(null, document.title, hash);
	},

	/**
	 * load content based on the the hash, ie http://server:port/?hash
	 */
	loadContent : function (hash) {
		var hist, sid, uid, match, title, auth, st, scroll;
		sid = localStorage.getItem('sessionId');
		if (sid) {
			wsl.sessionId = sid;
		}
		uid = parseInt(localStorage.getItem('userId'), 10);
		if (uid) {
			wsl.userId = uid;
		}

		auth = parseInt(localStorage.getItem('auth'), 10);
		if (auth) {
			wsl.admin = (auth === 1);
		}

		if (!sid) {
			$('#login').show();
			wsl.unlockUI();
			return;
		}

		hist = window.History;
		title = document.title;
		if (!hist.enabled) {
			wsl.error('No history support. What browser is this?');
			return false;
		}

		st = hist.getState();
		scroll = 0;
		if (st && st.data && st.data.scroll) {
			scroll = st.data.scroll;
		}

		if (hash !== "") {
			if (/artists\/?$/.test(hash)) {
				wsl.displayArtists(scroll);
			} else if (/playlists\/?$/.test(hash)) {
				wsl.displayPlaylists(scroll);
			} else if (/albums\/[0-9]+$/.test(hash)) {
				match = /albums\/([0-9]+$)/.exec(hash);
				wsl.displayAlbums(match[1], scroll);
			} else if (/songs\/[0-9]+$/.test(hash)) {
				match = /songs\/([0-9]+$)/.exec(hash);
				wsl.displaySongs(match[1], scroll);
			} else if (/playlist\/[0-9]+$/.test(hash)) {
				match = /playlist\/([0-9]+$)/.exec(hash);
				wsl.displayPlaylist(match[1], scroll);
			} else if (/playing\/?$/.test(hash)) {
				if (player.playing) {
					hist.replaceState(null, title, '?playlist/' + player.playing.playlist_id);
				} else {
					wsl.nothingPlaying();
				}
			} else if (/random\/?$/.test(hash)) {
				wsl.randomPlaylist();
			} else if (/users\/?$/.test(hash)) {
				wsl.displayUsers(scroll);
			} else if (/user\/([0-9]+$)/.test(hash)) {
				match = /user\/([0-9]+$)/.exec(hash);
				wsl.displayUser(match[1], scroll);
			} else if (/settings\/?$/.test(hash)) {
				wsl.displaySettings(scroll);
			} else if (/admin\/?$/.test(hash)) {
				wsl.displayAdmin(scroll);
			} else if (/logout\/?$/.test(hash)) {
				hist.replaceState(null, title, '?');
			} else {
				wsl.error("Page not found: ?" + hash);
				wsl.load("?");
			}
		} else {
			wsl.displayArtists(scroll);
		}

		wsl.selCount = 0;
	},

	getCurrentHash : function () {
		var st, hash;
		st = History.getState();
		if (st.url.indexOf('?') === -1) {
			hash = '';
		} else {
			hash = st.url.substring(st.url.indexOf('?') + 1);
		}
		return hash;
	}

};

$(document).ready(function () {

	History.Adapter.bind(window, 'statechange', function () {
		wsl.loadContent(wsl.getCurrentHash());
	});

	wsl.loadContent(wsl.getCurrentHash());

});
