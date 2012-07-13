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

	wsl.displayAdmin = function (scroll) {
		var cb, folders = null, users = null, modules = null;

		wsl.lockUI();

		cb = function () {
			var i, content, fileOrganizerModule = null, fileorganizerlibraries = null, defaultLibrary = 'No default library';
			;

			if (!users || !folders) {
				return;
			}

			if (!!modules) {
				if (!!modules.file_organizer) {
					fileOrganizerModule = modules.file_organizer;
				}
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

			content += '<p id="admin-indexer-status">&nbsp;</p>';

			if (!wsl.indexerStatusInterval) {
				wsl.indexerStatusInterval = window.setInterval(function () {
					$.ajax({
						url : 'wissl/indexer/status',
						headers : {
							sessionId : wsl.sessionId
						},
						dataType : 'json',
						success : function (data) {
							var c = '';
							if (data.running) {
								c += 'Indexer running: ' + (data.percentDone * 100).toFixed(2) + '%';
								c += ' (' + data.songsDone + '/' + data.songsTodo + ')';
								c += ', ' + wsl.formatSeconds(data.secondsLeft, true) + ' left';
							} else {
								c += 'Indexer is not running';
								window.clearInterval(wsl.indexerStatusInterval);
								wsl.indexerStatusInterval = null;
							}
							$('#admin-indexer-status').empty().html(c);
						},
						error : function (xhr, textStatus, errorThrown) {
							wsl.ajaxError("Failed to get indexer status", xhr);
							wsl.unlockUI();
						}
					});
				}, 1000);
			}

			if (fileOrganizerModule !== null) {
				if (fileOrganizerModule.library.length > 0) {
					defaultLibrary = fileOrganizerModule.library;
				}

				content += '<ul>';
				content += '<li id="fileOragnizerEnable" class="selectable">';
				content += '<span onclick="wsl.enableFileOrganizer(this.parentNode)" ';
				content += 'class="select-box">&nbsp</span>';
				content += '<span>File oragnizer</span>';
				content += '</li>';
				content += '<li>';
				content += '<span>' + defaultLibrary + '</span>';
				content += '<span onclick="wsl.showSelectFileOrganizerLibraryFolder()" ';
				content += 'class="button">...</span>';
				content += '</li>';
				content += '</ul>';

				fileorganizerlibraries = '<div>';
				fileorganizerlibraries += '<input type="radio" id="null" name="fileorganizerlibrary" value="null" required="required"';

				if (defaultLibrary == 'No default library') {
					fileorganizerlibraries += 'checked';
				}

				fileorganizerlibraries += '/>';
				fileorganizerlibraries += '<label class="radio-label" for="null">No default library</label>';
				fileorganizerlibraries += '</div>';

				for (i = 0; i < folders.length; i += 1) {
					fileorganizerlibraries += '<div>';
					fileorganizerlibraries += '<input type="radio" id="f' + i + '" name="fileorganizerlibrary" value="f' + i + '" required="required"';

					if (defaultLibrary == folders[i]) {
						fileorganizerlibraries += 'checked';
					}

					fileorganizerlibraries += '>';
					fileorganizerlibraries += '<label class="radio-label" for="f' + i + '">' + folders[i] + '</label>';
					fileorganizerlibraries += '</div>';
				}

				$('#fileorganizerlibrary-dialog-content').html(fileorganizerlibraries);
			}

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

			if (fileOrganizerModule !== null && fileOrganizerModule.enabled) {
				wsl.select('#fileOragnizerEnable');
			}

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
			url : '/wissl/modules',
			headers : {
				'sessionId' : wsl.sessionId
			},
			dataType : 'json',
			success : function (data) {
				modules = data.modules;
				cb();
			},
			error : function (xhr, textStatus, errorThrown) {
				wsl.ajaxError("Failed to get modules info", xhr);
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
	};

	wsl.showAddUser = function () {
		wsl.showDialog('adduser-dialog');
		$('#adduser-username').focus();
	};

	wsl.cancelAddUser = function () {
		$('#adduser-username').val('');
		$('#adduser-password').val('');
		$('#dialog-mask').hide();
		$('#adduser-dialog').hide();
	};

	wsl.addUser = function () {
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
				wsl.ajaxError("Failed to add user", xhr, 'adduser-dialog-error');
			}
		});
	};

	wsl.removeUser = function () {
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
	};

	wsl.showAddMusicFolder = function () {
		wsl.showDialog('addmusic-dialog');
		wsl.updateAddMusicFolderListing();
	};

	wsl.updateAddMusicFolderListing = function (dir) {
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
	};

	wsl.selectOrOpenMusicFolder = function (elt, dir) {
		if ($(elt).hasClass('dir-selected')) {
			wsl.updateAddMusicFolderListing(dir);
		} else {
			$('#addmusic-dialog-content ul li').removeClass('dir-selected');
			$(elt).addClass('dir-selected');
		}
	};

	wsl.addMusic = function () {
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
	};

	wsl.removeMusicFolder = function () {
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
	};

	wsl.cancelAddMusicFolder = function () {
		$('#dialog-mask').hide();
		$('#addmusic-dialog').hide();
	};

	wsl.shutdown = function () {
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
	};

	wsl.enableFileOrganizer = function (parentNode) {
		var enabled = false;

		wsl.toggleSelection(parentNode);

		enabled = $(parentNode).hasClass('selected');
		console.log($(parentNode));

		wsl.lockUI();

		$.ajax({
			url : '/wissl/modules/file_organizer/enable',
			type : 'POST',
			headers : {
				sessionId : wsl.sessionId
			},
			data : {
				enabled : enabled
			},
			success : function (data) {
				if (data) {
					if (data.enabled) {
						wsl.select(parentNode);
					} else {
						wsl.deselect(parentNode);
					}
				}

				wsl.unlockUI();
				wsl.displayAdmin();
			},
			error : function (xhr) {
				wsl.unlockUI();
				wsl.ajaxError("Failed to enable/disable file organizer", xhr);
			}
		});

	};

	wsl.showSelectFileOrganizerLibraryFolder = function () {
		wsl.showDialog('fileorganizerlibrary-dialog');
	};

	wsl.cancelSelectFileOrganizerLibraryFolder = function () {
		$('#dialog-mask').hide();
		$('#fileorganizerlibrary-dialog').hide();
	};

	wsl.setFileOrganizerLibrary = function () {
		var sel, dir;

		sel = $('#fileorganizerlibrary-dialog input[type=radio]:checked').val();
		if (sel) {
			if (sel != 'null') {
				dir = $('#fileorganizerlibrary-dialog label[for=' + sel + ']').html();
			} else {
				dir = '';
			}

			$.ajax({
				url : '/wissl/modules/file_organizer/library',
				type : 'POST',
				headers : {
					sessionId : wsl.sessionId
				},
				data : {
					dir : dir
				},
				success : function (data) {
					wsl.unlockUI();
					wsl.displayAdmin();
				},
				error : function (xhr) {
					wsl.unlockUI();
					wsl.ajaxError("Failed to change file organizer library", xhr);
				}
			});
		}
		
		wsl.cancelSelectFileOrganizerLibraryFolder();
	};

}(wsl));
