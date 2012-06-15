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
		var content = '';

		content += '<div id="home-title">Wissl</div>';
		content += '<form id="home-search-form" method="post" onsubmit="wsl.search();return false">';
		content += '<input id="home-search-input" type="text"';
		content += 'placeholder="song, artist, album" />';
		content += '<input id="home-search-ok" type="submit"';
		content += 'value="Search" class="button button-search" />';
		content += '</form>';

		wsl.refreshNavbar({
			home : true
		});
		wsl.showContent({
			home : content
		});
		wsl.unlockUI();
	};

	wsl.search = function () {
		var query;
		query = $('#home-search-input').val();
		console.log('searching: ' + query);
	};

}(wsl));
