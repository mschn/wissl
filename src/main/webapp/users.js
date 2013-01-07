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

	wsl.displayUsers = function (scroll) {
		wsl.lockUI();

		$.ajax({
			url : 'wissl/users',
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
	};

	wsl.displayUser = function (uid, scroll) {
		wsl.lockUI();

		$.ajax({
			url : 'wissl/user/' + uid,
			headers : {
				sessionId : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {
				var content = '', t1, i, j, pl, song, sess;

				content += '<p><span class="user-name">' + data.user.username + '</span>';
				if (data.user.auth === 1) {
					content += '<span class="user-admin">admin</span></p>';
				}
				content += '<p>Downloaded ' + (data.user.downloaded / (1024 * 1024)).toFixed(2) + ' MiB</p>';

				if (data.sessions) {
					content += '<h3>' + data.sessions.length + ' active sessions</h3>';
					for (i = 0; i < data.sessions.length; i++) {
						sess = data.sessions[i];
						t1 = new Date(sess.created_at).getTime() / 1000;
						content += '<div class="user-session">';
						content += '<p>Connected ' + wsl.formatSecondsAlt(t1, 2) + ' ago</p>';
						if (sess.origins) {
							content += '<table><thead><tr><th>IP</th><th>last activity</th><th>client</th></tr></thead><tdoby>';

							for (j = 0; j < sess.origins.length; j++) {
								content += '<tr><td>' + sess.origins[j].ip + '</td>';
								content += '<td>' + wsl.formatSecondsAlt(sess.origins[j].time / 1000, 2) + '</td>';
								content += '<td>' + sess.origins[j].user_agent + '</td></tr>';
							}
						}
						content += '</table></tdoby>';
						if (sess.last_played_song) {
							song = sess.last_played_song;
							content += '<p>Last played ';
							content += '<a onclick="">' + song.title + '</a> on ';
							content += '<a onclick="wsl.load(\'?songs/' + song.album_id + '\')">' + song.album_name + '</a> by ';
							content += '<a onclick="wsl.load(\'?albums/' + song.artist_id + '\')">' + song.artist_name + '</a></p>';
						}
						content += '</div>';
					}
				} else {
					content += '<p>Not connected</p>';
				}

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
	};

}(wsl));
