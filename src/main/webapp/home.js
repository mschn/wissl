/* This file is part of Wissl - Copyright (C) 2013 Mathieu Schnoor
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

	wsl.searchTab = {
		artists : false,
		albums : false,
		songs : true
	};

	wsl.displayHome = function () {
		var stats, done, content = '';

		wsl.lockUI();

		content += '<div id="home-search">';
		content += '<form id="search-form" method="post" onsubmit="wsl.search();return false">';
		content += '<input id="search-input" type="text"';
		content += 'placeholder="search" />';
		content += '<input id="search-ok" type="submit"';
		content += 'value="Search" />';
		content += '</form>';
		content += '</div>';
		content += '<div id="home-sections">';

		done = function () {
			content += '</div>';

			wsl.refreshNavbar({
				home : true
			});
			wsl.showContent({
				home : content
			});

			wsl.unlockUI();
		};

		stats = function () {
			$.ajax({
				url : 'wissl/stats',
				headers : {
					sessionId : wsl.sessionId
				},
				dataType : 'json',
				success : function (data) {
					var st = data.stats;

					content += '<div class="home-section home-section-stats">';
					content += '<h3>Stats</h3>';
					content += '<ul>';
					content += '<li><span class="stat-left">' + st.songs + '</span><span class="stat-right">songs</span></li>';
					content += '<li><span class="stat-left">' + st.albums + '</span><span class="stat-right">albums</span></li>';
					content += '<li><span class="stat-left">' + st.artists + '</span><span class="stat-right">artists</span></li>';
					content += '<li><span class="stat-left">' + st.playlists + '</span><span class="stat-right">playlist' + (st.playlists > 1 ? 's' : '') + '</span></li>';
					content += '<li><span class="stat-left">' + st.users + '</span><span class="stat-right">user' + (st.users > 1 ? 's' : '') + '</span></li>';
					content += '<li><span class="stat-left">' + wsl.formatSecondsAlt(st.playtime, 2) + '</span><span class="stat-right">playtime</span></li>';
					content += '<li><span class="stat-left">' + wsl.formatBytes(st.downloaded, 0) + '</span><span class="stat-right">downloaded</span></li>';
					content += '<li><span class="stat-left">' + wsl.formatSecondsAlt(st.uptime / 1000, 2) + '</span><span class="stat-right">uptime</span></li>';
					content += '</ul>';
					content += '</div>';

					done();
				},
				error : function (xhr, textStatus, errorThrown) {
					wsl.ajaxError("Failed to get runtime stats", xhr);
					wsl.unlockUI();
				}
			});
		};

		$.ajax({
			url : 'wissl/recent/12',
			dataType : 'json',
			headers : {
				sessionId : wsl.sessionId
			},
			success : function (data) {
				var albums = data.albums, artists = data.artists, i, cb, al, ar, artist = null, arr;
				content += '<div class="home-section home-section-albums">';
				content += '<h3>New albums</h3>';

				arr = {};
				for (i = 0; i < albums.length; i += 1) {
					al = albums[i];
					if (!arr.hasOwnProperty(al.artist_name)) {
						arr[al.artist_name] = new Array();
					}
					arr[al.artist_name].push(al);
				}

				for (artist in arr) {
					if (arr[artist].length === 1) {
						al = arr[artist][0];
						if (al.name === '') {
							al.name = '<em>No metadata</em>';
						}

						cb = 'onclick="wsl.load(\'?songs/' + al.id + '\')"';
						content += '<ul><li>';
						content += '<span class="album-single" ' + cb + ' title="' + al.name + '">' + al.name;
						content += ' <span class="lighter">by <em>' + artist + '</em></span></span>';
						content += '<span class="meta duration">' + wsl.formatSeconds(al.playtime) + '</span>';
						content += '<span class="meta songs">' + al.songs + '</span>';
						content += '</li></ul>';
					} else {
						content += '<span class="lighter">' + arr[artist].length + ' albums by <em>' + artist + '</em></span>';
						content += '<ul>';

						for (i = 0; i < arr[artist].length; i += 1) {
							al = arr[artist][i];
							if (al.name === '') {
								al.name = '<em>No metadata</em>';
							}

							cb = 'onclick="wsl.load(\'?songs/' + al.id + '\')"';
							content += '<li>';
							content += '<span class="album-name" ' + cb + ' title="' + al.name + '">' + al.name + '</span>';
							content += '<span class="meta duration">' + wsl.formatSeconds(al.playtime) + '</span>';
							content += '<span class="meta songs">' + al.songs + '</span>';
							content += '</li>';
						}
						content += '</ul>';
					}
				}
				content += '</div>';

				content += '<div class="home-section home-section-artists">';
				content += '<h3>New artists</h3>';
				content += '<ul>';
				for (i = 0; i < artists.length; i += 1) {
					ar = artists[i];
					if (ar.name === '') {
						ar.name = '<em>No metadata</em>';
					}

					cb = 'onclick="wsl.load(\'?albums/' + ar.id + '\')"';
					content += '<li>';
					content += '<span class="artist-name" ' + cb + ' title="' + ar.name + '">' + ar.name + '</span>';
					content += '<span class="meta duration">' + wsl.formatSeconds(ar.playtime) + '</span>';
					content += '<span class="meta albums">' + ar.albums + '</span>';
					content += '<span class="meta songs">' + ar.songs + '</span>';
					content += '</li>';
				}
				content += '</ul>';
				content += '</div>';

				stats();

			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Failed to get latest albums and artists", xhr);
				wsl.unlockUI();
			}
		});

	};

	wsl.search = function () {
		var query, url, enc;
		query = $('#search-input').val();
		enc = encodeURIComponent(query);
		if (enc.length === 0) {
			return;
		}

		url = '?search/' + enc;

		wsl.load(url);
	};

	wsl.displaySearch = function (query) {
		wsl.lockUI();
		$.ajax({
			url : 'wissl/search/' + encodeURIComponent(query),
			headers : {
				sessionId : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {

				var content, artists, albums, songs, i, claz, cb, link;

				artists = data.artists;
				albums = data.albums;
				songs = data.songs;
				content = '';

				content += '<form id="search-form" method="post" onsubmit="wsl.search();return false">';
				content += '<input id="search-input" type="text"';
				content += 'value="' + query + '"';
				content += 'placeholder="song, artist, album" />';
				content += '<input id="search-ok" type="submit"';
				content += 'value="Search"/>';
				content += '</form>';

				if (artists.length + albums.length + songs.length === 0) {
					content += '<div id="no-search-results">';
					content += 'No search results';
					content += '</div>';
				} else {
					if (songs.length > 0) {
						wsl.searchTab = {
							songs : true
						};
					} else if (albums.length > 0) {
						wsl.searchTab = {
							albums : true
						};
					} else if (artists.length > 0) {
						wsl.searchTab = {
							artists : true
						};
					}

					content += '<div id="search-results">';

					content += '<span id="search-results-tab-songs"';
					content += 'onclick="wsl.showSearchResult({songs:true});" ';
					content += 'class="search-result-tab">Songs (' + songs.length + ')</span>';

					content += '<span id="search-results-tab-albums" ';
					content += 'onclick="wsl.showSearchResult({albums:true});" ';
					content += 'class="search-result-tab">Albums (' + albums.length + ')</span>';

					content += '<span id="search-results-tab-artists" ';
					content += 'onclick="wsl.showSearchResult({artists:true});" ';
					content += 'class="search-result-tab">Artists (' + artists.length + ')</span>';

					if (artists) {
						content += '<ul id="search-results-artists">';
						for (i = 0; i < artists.length; i += 1) {
							link = 'onclick="wsl.load(\'?albums/' + artists[i].id + '\')"';
							claz = (i % 2 === 0 ? '' : 'odd');
							if (player.playing && player.playing.artist_id === artists[i].id) {
								claz += ' playing';
							}

							content += '<li id="artist-' + artists[i].id + '" class="' + claz + '">';
							content += '<span class="artist-name" ' + link + '>' + wsl.highlightSearch(artists[i].name, query) + '</li>';
						}
						content += '</ul>';
					}
					if (albums) {
						content += '<ul id="search-results-albums">';
						for (i = 0; i < albums.length; i += 1) {
							claz = 'selectable' + (i % 2 ? ' odd' : '');
							if (player.playing && player.playing.album_id === albums[i].id) {
								claz += ' playing';
							}
							content += '<li id="album-' + albums[i].id + '" class="' + claz + '">';

							cb = 'onmousedown="wsl.mouseDown(this,event);return false" ';
							content += '<span ' + cb + ' class="select-box">&nbsp</span>';
							content += '<span class="album-id">' + albums[i].id + '</span>';
							content += '<span class="album-date">' + albums[i].date + '</span>';
							content += '<span onclick="wsl.load(\'?songs/' + albums[i].id + '\')"';
							content += 'class="album-name">' + wsl.highlightSearch(albums[i].name, query) + '</span>';
							content += '<span onclick="wsl.load(\'?albums/' + albums[i].artist + '\')"';
							content += 'class="album-artist-name">' + albums[i].artist_name + '</span>';
							content += '<span class="album-songs">' + albums[i].songs + ' songs</span>';
							content += '<span class="album-playtime">' + wsl.formatSeconds(albums[i].playtime) + '</span>';

							content += '</li>';
						}
						content += '</ul>';
					}
					if (songs) {
						content += '<ul id="search-results-songs">';
						for (i = 0; i < songs.length; i += 1) {
							claz = 'selectable' + (i % 2 ? ' odd' : '');
							if (player.playing && player.playing.song_id === songs[i].id) {
								claz += ' playing';
							}
							cb = 'onmousedown="wsl.mouseDown(this,event);return false" ';

							content += '<li id="song-' + songs[i].id + '" class="' + claz + '">';
							content += '<span ' + cb + ' class="select-box">&nbsp</span>';
							content += '<span class="song-id">' + songs[i].id + '</span>';
							content += '<span onclick="wsl.playAlbum(' + songs[i].album_id + ',' + songs[i].id + ',' + songs[i].position + ')"" ';
							content += 'class="song-title">' + wsl.highlightSearch(songs[i].title, query) + '</span>';
							content += '<span onclick="wsl.load(\'?songs/' + songs[i].album_id + '\')" ';
							content += 'class="song-album">' + songs[i].album_name + '</span>';
							content += '<span onclick="wsl.load(\'?albums/' + songs[i].artist_id + '\')" ';
							content += 'class="song-artist">' + songs[i].artist_name + '</span>';
							content += '<span class="song-duration">' + wsl.formatSeconds(songs[i].duration) + '</span>';

							content += '</li>';
						}
						content += '</ul>';
					}
					content += '</div>';
				}

				wsl.refreshNavbar({
					search : true
				});
				wsl.showContent({
					search : content
				});

				wsl.showSearchResult(wsl.searchTab);
				wsl.unlockUI();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Search failed", xhr);
				wsl.unlockUI();
			}

		});
	};

	wsl.highlightSearch = function (str, query) {
		var i, ret;
		i = str.toLowerCase().indexOf(query.toLowerCase(query));
		if (i === -1) {
			return str;
		}

		ret = str.substring(0, i);
		ret += '<span class="search-highlight">';
		ret += str.substring(i, i + query.length);
		ret += '</span>';
		ret += str.substring(i + query.length, str.length);
		return ret;
	};

	wsl.showSearchResult = function (tab) {
		wsl.clearSelection();
		wsl.searchTab.songs = tab.songs;
		wsl.searchTab.albums = tab.albums;
		wsl.searchTab.artists = tab.artists;
		if (tab.artists) {
			$('#search-results-songs').hide();
			$('#search-results-albums').hide();
			$('#search-results-artists').show();
			$('#search-results-tab-songs').removeClass('search-result-tab-selected');
			$('#search-results-tab-albums').removeClass('search-result-tab-selected');
			$('#search-results-tab-artists').addClass('search-result-tab-selected');
		} else if (tab.albums) {
			$('#search-results-songs').hide();
			$('#search-results-albums').show();
			$('#search-results-artists').hide();
			$('#search-results-tab-songs').removeClass('search-result-tab-selected');
			$('#search-results-tab-albums').addClass('search-result-tab-selected');
			$('#search-results-tab-artists').removeClass('search-result-tab-selected');
		} else if (tab.songs) {
			$('#search-results-songs').show();
			$('#search-results-albums').hide();
			$('#search-results-artists').hide();
			$('#search-results-tab-songs').addClass('search-result-tab-selected');
			$('#search-results-tab-albums').removeClass('search-result-tab-selected');
			$('#search-results-tab-artists').removeClass('search-result-tab-selected');
		}
	};

}(wsl));
