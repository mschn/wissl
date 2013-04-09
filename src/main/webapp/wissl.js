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

	// id of logged user
	wsl.userId = null;
	// session id used for all ajax requests
	wsl.sessionId = null;
	wsl.loggedIn = false;
	// true if logged user is administrator
	wsl.admin = false;
	// number of currently selected elements
	wsl.selCount = 0;
	// used by the admin page to reload the indexer status
	wsl.indexerStatusInterval = null;

	// true when a callback is running and UI should be blocked
	wsl.uiLock = false;

	wsl.login = function () {
		$('#login-error').hide();
		var username = $('#username').val(), password = $('#password').val();
		wsl.doLogin(username, password);
	};

	wsl.doLogin = function (username, password) {
		wsl.lockUI();
		$.ajax({
			url : "wissl/login",
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
				wsl.loggedIn = true;

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
	};

	wsl.logout = function () {
		wsl.confirmDialog('Logout', 'Do you really want to leave?', function () {
			var sid = wsl.sessionId;

			localStorage.removeItem('sessionId');
			localStorage.removeItem('userId');
			localStorage.removeItem('auth');

			wsl.sessionId = null;
			wsl.userId = null;
			wsl.admin = false;
			wsl.loggedIn = false;

			wsl.lockUI();
			$.ajax({
				url : "wissl/logout",
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
	};

	wsl.addFirstUser = function () {
		var user, pw, pwConfirm, auth = 1;

		$('#firstuser-error').hide();

		user = $('#firstuser-username').val();
		pw = $('#firstuser-password').val();
		pwConfirm = $('#firstuser-password-confirm').val();

		if (pw !== pwConfirm) {
			$('#firstuser-error').empty().html('Passwords do not match').show();
			return;
		}

		wsl.lockUI();
		$.ajax({
			url : 'wissl/user/add',
			type : 'POST',
			dataType : 'json',
			data : {
				username : user,
				password : pw,
				auth : auth
			},
			success : function (data) {
				wsl.unlockUI();
				$('#firstuser').hide();
				wsl.doLogin(user, pw);
			},
			error : function (xhr) {
				var msg = 'Failed to create user', e;
				e = $.parseJSON(xhr.responseText);
				if (e.message) {
					msg += ': ' + e.message;
				}
				wsl.unlockUI();
				$('#firstuser-error').empty().html(msg).show();
				console.log(xhr);
			}
		});
	};

	wsl.nothingPlaying = function () {
		wsl.showContent({
			library : "<span class='empty-library'>Nothing is currently playing!</span>"
		});
		wsl.refreshNavbar({});
	};

	wsl.refreshNavbar = function (arg) {
		var navbar = '', clazz, cb, name;

		clazz = 'navbar-playing';
		cb = 'wsl.load(\'?playing/\')';
		navbar += '<a id="navbar-playing" class="' + clazz + '" onclick="' + cb + '">Playing</a>';

		clazz = 'navbar-random';
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?random\')">Random</a>';

		navbar += '<hr/>';

		clazz = (arg.home ? 'selected-nav ' : '') + 'navbar-home';
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?\')">Home</a>';

		if (arg.search) {
			clazz = 'navbar-search indent selected-nav';
			navbar += '<a class="' + clazz + '">Search</a>';
			navbar += '<div id="context-action">';
			navbar += "<a class='navbar-select-all' onclick='wsl.selectAll()'>Select all</a>";
			navbar += "<a class='navbar-cancel-select selection-disabled' onclick='wsl.clearSelection()'>Cancel selection</a>";
			navbar += "<a class='navbar-add-songs selection-disabled' onclick='wsl.showAddToPlaylist()'>Add selected to playlist</a>";
			navbar += "<a class='navbar-play selection-disabled' onclick='wsl.playNow()'>Play now</a>";
			navbar += '</div>';
		}

		clazz = (arg.artists ? 'selected-nav ' : '') + 'navbar-artists';
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?artists/\')">Library</a>';

		if (arg.artist) {
			clazz = 'navbar-artist indent' + (arg.album ? '' : ' selected-nav');
			name = arg.artist.name;
			if (name === '') {
				name = 'no metadata';
				clazz += ' navbar-no-metadata';
			}
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?albums/' + arg.artist.id + '\')">';
			navbar += name + "</a>";
			if (!arg.album) {
				navbar += "<div id='context-action'>";
				navbar += "<a class='navbar-select-all' onclick='wsl.selectAll()'>Select all albums</a>";
				navbar += "<a class='navbar-cancel-select selection-disabled' onclick='wsl.clearSelection()'>Cancel selection</a>";
				navbar += "<a class='navbar-add-songs selection-disabled' onclick='wsl.showAddToPlaylist()'>Add selected songs to playlist</a>";
				navbar += "<a class='navbar-play selection-disabled' onclick='wsl.playNow()'>Play now</a>";
				if (wsl.admin === true) {
					navbar += "<a class='navbar-edit selection-disabled' onclick='wsl.showEditAlbum()'>Edit album</a>";
				}
				navbar += "</div>";
			}
		}

		if (arg.album) {
			clazz = 'navbar-album indent selected-nav';
			name = arg.album.name;
			if (name === '') {
				name = 'no metadata';
				clazz += ' navbar-no-metadata';
			}
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?songs/' + arg.album.id + '\')">';
			navbar += name + "</a>";
			navbar += "<div id='context-action'>";
			navbar += "<a class='navbar-select-all' onclick='wsl.selectAll()'>Select all songs</a>";
			navbar += "<a class='navbar-cancel-select selection-disabled' onclick='wsl.clearSelection()'>Cancel selection</a>";
			navbar += "<a class='navbar-add-songs selection-disabled' onclick='wsl.showAddToPlaylist()'>Add selected songs to playlist</a>";
			navbar += "<a class='navbar-play selection-disabled' onclick='wsl.playNow()'>Play now</a>";
			if (wsl.admin === true) {
				navbar += "<a class='navbar-edit selection-disabled' onclick='wsl.showEditSong()'>Edit song</a>";
			}
			navbar += "</div>";
		}

		clazz = (arg.playlists ? 'selected-nav ' : '') + 'navbar-playlists';
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?playlists/\')">Playlists</a>';
		if (arg.playlists) {
			navbar += '<div id="context-action">';
			navbar += "<a class='selection-disabled navbar-delete-playlist' onclick='wsl.deletePlaylist()'>Delete selected playlists</a>";
			navbar += "</div>";
		}

		if (arg.playlist) {
			clazz = 'navbar-playlist indent selected-nav';
			navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?playlist/' + arg.playlist.id + '\')">';
			navbar += arg.playlist.name + "</a>";

			navbar += "<div id='context-action'>";
			cb = "onclick='wsl.deleteSelectedSongs(" + arg.playlist.id + ")'";
			navbar += "<a class='navbar-select-all' onclick='wsl.selectAll()'>Select all songs</a>";
			navbar += "<a class='navbar-cancel-select selection-disabled' onclick='wsl.clearSelection()'>Cancel selection</a>";
			navbar += "<a class='selection-disabled navbar-delete-songs-playlist' " + cb + ">Remove selected songs from playlist</a>";
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

		navbar += "<hr/>";

		clazz = 'navbar-about';
		if (arg.about) {
			clazz += ' selected-nav';
		}
		navbar += '<a class="' + clazz + '" onclick="wsl.load(\'?about\')">About</a>';

		navbar += "<a class='navbar-logout' onclick='wsl.logout()'>Logout</a>";

		$("#navbar").empty().append(navbar);
	};

	wsl.showContent = function (content) {
		var pages, i, page;

		pages = [ 'home', 'search', 'artists', 'library', 'users', 'user', 'settings', 'admin', 'about', 'error' ];

		for (i = 0; i < pages.length; i += 1) {
			page = pages[i];
			if (content[page]) {
				$('#' + page).show().empty().append(content[page]);
			} else {
				$('#' + page).empty().hide();
			}
		}

		if (content.scroll) {
			$('html, body').scrollTop(content.scroll);
		}
	};

	wsl.selectionDrag = null;

	wsl.mouseDown = function (e, event) {
		var mouseup, mousemove;

		mousemove = function (event) {
			var y, y2, elt, top, height;

			top = $('.selectable:visible').first().offset().top;
			height = $('.selectable:visible').first().height();

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

			$('.selectable:visible').each(function () {
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
	};

	wsl.clearSelection = function () {
		$('.selected').each(function (i) {
			wsl.deselect(this);
		});
	};

	wsl.selectAll = function () {
		wsl.selCount = 0;
		$('.selectable').each(function (i) {
			wsl.select(this);
		});
	};

	wsl.select = function (e) {
		var el = $(e);
		if (el.hasClass('selected')) {
			return;
		}
		wsl.selCount += 1;
		el.addClass('selected');
		el.find('.select-box').addClass('select-box-checked');

		if (wsl.selCount === 1) {
			$('.selection-disabled').removeClass('selection-disabled').addClass('selection-enabled');
		}
		wsl.selectionChanged();
	};

	wsl.deselect = function (e) {
		wsl.selCount = Math.max(0, wsl.selCount - 1);
		$(e).removeClass('selected');
		$(e).find('.select-box').removeClass('select-box-checked');

		if (wsl.selCount === 0) {
			$('.selection-enabled').removeClass('selection-enabled').addClass('selection-disabled');
		}
		wsl.selectionChanged();
	};

	wsl.toggleSelection = function (e) {
		if ($(e).hasClass('selected')) {
			wsl.deselect(e);
		} else {
			wsl.select(e);
		}
		wsl.selectionChanged();
	};

	wsl.selectionChanged = function () {
		if (wsl.selCount === 0) {
			$('#context-action').hide();
			$('#navbar>a').each(function () {
				$(this).show();
			});
		} else {
			$('#navbar>a').each(function () {
				$(this).hide();
			});
			$('#context-action').show();
		}
	};

	wsl.playNow = function () {
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
			url : 'wissl/playlist/create-add',
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
					url : 'wissl/playlist/' + playlist.id + '/song/0',
					headers : {
						'sessionId' : wsl.sessionId
					},
					dataType : 'json',
					success : function (data) {
						player.play({
							song_id : data.song.id,
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
	};

	wsl.displayError = function (status, message, trace) {
		wsl.lockUI();
		var content = '<h1>Error ' + status + '</h1>';
		content += '<p>' + message;

		if (trace) {
			content += '<br>';
			content += trace.class + ': ' + trace.message;
		}
		content += '<p>';

		wsl.showContent({
			error : content
		});
		wsl.refreshNavbar({
			error : true
		});
		wsl.unlockUI();
	};

	wsl.displayAbout = function (scroll) {
		wsl.lockUI();
		$.ajax({
			url : 'wissl/info',
			type : 'GET',
			headers : {
				sessionId : wsl.sessionId
			},
			success : function (data) {
				var content = '<div id="about-header"></div>';
				content += '<p class="subtitle">Web Interface for Sound Streaming Library</p>';

				content += '<div>';
				content += '<p>Copyright (c) 2013 Mathieu Schnoor</p>';
				content += '<p>Distributed under the terms of the <a target="_blank"';
				content += 'href="http://www.gnu.org/licenses/gpl.html">GNU GPL v3</a></p>';
				content += '<p>Website: <a target="_blank" href="http://msch.fr/wissl">http://msch.fr/wissl</a></p>';
				content += '</div>';

				content += '<table>';
				content += '<tr><td class="left">Version</td><td class="right">' + data.version + '</td></tr>';
				content += '<tr><td class="left">Build</td><td class="right">' + data.build + '</td></tr>';
				content += '<tr><td class="left">Server</td><td class="right">' + data.server + '</td></tr>';
				content += '<tr><td class="left">OS</td><td class="right">' + data.os + '</td></tr>';
				content += '<tr><td class="left">Java</td><td class="right">' + data.java + '</td></tr>';
				content += '</table>';
				content += '</ul></div>';

				wsl.showContent({
					about : content,
					scroll : scroll
				});
				wsl.refreshNavbar({
					about : true
				});
				wsl.unlockUI();
			},
			error : function (xhr) {
				wsl.unlockUI();
				wsl.ajaxError("Failed to get server info", xhr);
			}
		});
	};

	wsl.load = function (hash) {
		if (wsl.uiLock) {
			return;
		}
		History.replaceState({
			scroll : Math.max($('body').scrollTop(), $('html').scrollTop())
		}, '', History.getState().url);

		History.pushState(null, '', hash);
	};

	wsl.loadContent = function (hash) {
		var hist, sid, uid, match, auth, st, scroll;
		sid = localStorage.getItem('sessionId');
		if (sid) {
			wsl.sessionId = sid;
			wsl.loggedIn = true;
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
			$.ajax({
				url : 'wissl/hasusers',
				type : 'GET',
				dataType : "json",
				success : function (data) {
					wsl.unlockUI();

					if (data.hasusers === false) {
						$('#firstuser-error').hide();
						$('#firstuser').show();
						$('#firstuser-username').focus();
					} else {
						$('#login').show();
						$('#username').focus();
					}
				},
				error : function (xhr) {
					wsl.unlockUI();
					wsl.ajaxError('Failed to contact the server', xhr);
				}
			});

			return;
		}

		hist = window.History;
		if (!hist.enabled) {
			wsl.displayError(418, 'No history support. I giver up.');
			return false;
		}

		if (wsl.indexerStatusInterval) {
			window.clearInterval(wsl.indexerStatusInterval);
			wsl.indexerStatusInterval = null;
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
					hist.replaceState(null, '', '?playlist/' + player.playing.playlist_id);
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
			} else if (/about\/?$/.test(hash)) {
				wsl.displayAbout(scroll);
			} else if (/search\/([\S ]+)/.test(hash)) {
				match = /search\/([\S ]+)/.exec(hash);
				wsl.displaySearch(match[1]);
			} else if (/search\/?$/.test(hash)) {
				wsl.displayHome();
			} else if (/logout\/?$/.test(hash)) {
				hist.replaceState(null, '', '?');
			} else if (hash === '?') {
				wsl.displayHome();
			} else {
				wsl.displayError(404, "Page not found: ?" + hash);
			}
		} else {
			wsl.displayHome();
		}

		if (player.playing && player.song && player.song.title && player.song.title !== '') {
			document.title = player.song.title;
		} else {
			document.title = 'Wissl';
		}
		wsl.selCount = 0;
	};

	wsl.getCurrentHash = function () {
		var st, hash;
		st = History.getState();

		if (st.url.indexOf('?') === -1) {
			hash = '';
		} else {
			hash = st.url.substring(st.url.indexOf('?') + 1);
		}
		return hash;
	};
}(wsl));

$(document).ready(function () {
	'use strict';

	var h;

	History.Adapter.bind(window, 'statechange', function () {
		if (wsl.hideDialog) {
			wsl.hideDialog();
			wsl.hideDialog = null;
		}

		var h = wsl.getCurrentHash();
		if (h === wsl.previousHash) {
			return;
		}
		wsl.previousHash = h;
		wsl.loadContent(h);
	});

	h = wsl.getCurrentHash();
	wsl.previousHash = h;
	wsl.loadContent(h);

	$(window).on('beforeunload', function () {
		if (player.playing) {
			return 'Music will stop if you continue.';
		}
	});

	$(window).on('scroll', function (e) {
		wsl.refreshArtistImages(e);
	});

});
