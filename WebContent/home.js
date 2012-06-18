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

	wsl.displayHome = function () {
		wsl.lockUI();
		$.ajax({
			url : '/wissl/stats',
			headers : {
				sessionId : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {
				var content = '', st = data.stats;

				content += '<div id="home-title">Wissl</div>';

				content += '<div id="home-search">';
				content += '<form id="search-form" method="post" onsubmit="wsl.search();return false">';
				content += '<input id="search-input" type="text"';
				content += 'placeholder="song, artist, album" />';
				content += '<input id="search-ok" type="submit"';
				content += 'value="Search" class="button button-search" />';
				content += '</form>';
				content += '</div>';

				if (st) {
					content += '<p class="home-stats-p">';
					content += '<span><span>' + st.songs + '</span> songs</span>';
					content += '<span><span>' + st.albums + '</span> albums</span>';
					content += '<span><span>' + st.artists + '</span> artists</span>';
					content += '<span><span>' + st.playlists + '</span> playlist' + (st.playlists > 1 ? 's' : '') + '</span>';
					content += '<span><span>' + st.users + '</span> user' + (st.users > 1 ? 's' : '') + '</span>';
					content += '</p><p class="home-stats-p">';
					content += '<span><span>' + wsl.formatSeconds(st.playtime) + '</span> playtime</span>';
					content += '<span><span>' + wsl.formatBytes(st.downloaded, 0) + '</span> downloaded</span>';
					content += '<span><span>' + wsl.formatSeconds(st.uptime / 1000, true, true) + '</span> uptime</span>';
					content += '</p>';
				}

				wsl.refreshNavbar({
					home : true
				});
				wsl.showContent({
					home : content
				});
				wsl.unlockUI();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Failed to get runtime stats", xhr);
				wsl.unlockUI();
			}
		});

	};

	wsl.search = function () {
		var query, url;
		query = $('#search-input').val();
		url = '\?search/' + encodeURIComponent(query);

		wsl.load(url);
	},

	wsl.displaySearch = function (query) {
		wsl.lockUI();
		$.ajax({
			url : '/wissl/search/' + encodeURIComponent(query),
			headers : {
				sessionId : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {

				var content, artists, albums, songs, i;

				artists = data.artists;
				albums = data.albums;
				songs = data.songs;

				content = '';

				content += '<form id="search-form" method="post" onsubmit="wsl.search();return false">';
				content += '<input id="search-input" type="text"';
				content += 'value="' + query + '"';
				content += 'placeholder="song, artist, album" />';
				content += '<input id="search-ok" type="submit"';
				content += 'value="Search" class="button button-search" />';
				content += '</form>';

				if (artists) {
					content += '<h2>Artists:</h2><ul>';

					for (i = 0; i < artists.length; i += 1) {
						content += '<li>' + artists[i].name + '</li>';
					}
					content += '</ul>';
				}
				if (albums) {
					content += '<h2>Albums:</h2><ul>';
					for (i = 0; i < albums.length; i += 1) {
						content += '<li>' + albums[i].name + '</li>';
					}
					content += '</ul>';
				}
				if (songs) {
					content += '<h2>Songs:</h2><ul>';
					for (i = 0; i < songs.length; i += 1) {
						content += '<li>' + songs[i].title + '</li>';
					}
					content += '</ul>';
				}

				wsl.refreshNavbar({
					search : true
				});
				wsl.showContent({
					search : content
				});

				wsl.unlockUI();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Search failed", xhr);
				wsl.unlockUI();
			}

		});

	};

}(wsl));
