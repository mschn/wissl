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

	wsl.displaySettings = function (scroll) {
		var content = '<h2>Settings</h2>';

		content += '<p>';
		content += '<span class="button button-password" onclick="wsl.showChangePassword()">Change password</span>';
		content += '</p>';

		wsl.showContent({
			settings : content,
			scroll : scroll
		});
		wsl.refreshNavbar({
			settings : true
		});
		wsl.unlockUI();
	};

	wsl.showChangePassword = function () {
		wsl.showDialog('password-dialog');
		wsl.hideDialog = wsl.cancelChangePassword;
	};

	wsl.cancelChangePassword = function () {
		$('#password-old').val('');
		$('#password-new').val('');
		$('#password-confirm').val('');
		$('#dialog-mask').hide();
		$('#password-dialog').hide();
		$('#password-dialog-error').hide();
	};

	wsl.changePassword = function () {
		var oldPw, newPw, newPwConfirm;

		$('#password-dialog-error').hide();

		oldPw = $('#password-old').val();
		newPw = $('#password-new').val();
		newPwConfirm = $('#password-confirm').val();

		if (newPw !== newPwConfirm) {
			$('#password-dialog-error').html('Passwords do not match').show();
			return false;
		}

		wsl.lockUI();
		$.ajax({
			url : 'wissl/user/password',
			type : 'POST',
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : 'json',
			data : {
				old_password : oldPw,
				new_password : newPw
			},
			success : function (data) {
				wsl.unlockUI();
				wsl.cancelChangePassword();
			},
			error : function (xhr) {
				wsl.unlockUI();
				wsl.ajaxError("Failed to change password", xhr, 'password-dialog-error');
			}
		});
	};

}(wsl));
