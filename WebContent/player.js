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

/*global $, wsl, soundManager, document */

soundManager.url = "static/soundmanager-swf";
soundManager.flashVersion = 9;
soundManager.preferFlash = true;
soundManager.autoLoad = true;
soundManager.useHTML5Audio = true;
soundManager.multiShot = false;

var player = {};

(function (player) {
	'use strict';

	// set to true when soundmanager loads
	player.hasSound = false;
	// currently playing sound
	player.sound = null;

	player.playing = null;

	player.volume = 100;

	player.muted = false;

	player.play = function (playing) {
		if (!playing) {
			return;
		}
		if (player.playing && player.playing.playlist_id !== playing.playlist_id) {
			var msg = 'A song is currently playing from another playlist.<br>Continue?';
			wsl.confirmDialog('New playlist', msg, function () {
				player.internalPlay(playing);
			}, function () {
			});
		} else {
			player.internalPlay(playing);
		}
	};

	player.internalPlay = function (playing) {
		player.playing = playing;

		$.ajax({
			url : "/wissl/song/" + playing.song_id,
			headers : {
				"sessionId" : wsl.sessionId
			},
			dataType : "json",
			success : function (data) {
				var song, artist, album, art;
				song = data.song;
				artist = data.artist;
				album = data.album;

				player.destroySound();
				player.song = song;
				player.playing = playing;
				player.playing.album_id = album.id;
				player.playing.artist_id = artist.id;

				$('.playing').removeClass('playing');
				$('#play').addClass('pause');
				$('#player').fadeIn(300);
				$('#navbar-playing').show();

				$('#playing-title').html('<a title="' + song.title + '">' + song.title + '</a><br>');
				$('#playing-album').empty().html('<a title="' + album.name + '" onclick="wsl.load(\'?songs/' + album.id + '\')" class="playing-album">' + (album.name || ' ') + '</a>');
				$('#playing-artist').empty().html('<a title="' + artist.name + '" onclick="wsl.load(\'?albums/' + artist.id + '\')" class="playing-artist">' + (artist.name || ' ') + '</a>');

				$('#playing').show();

				if (album.artwork) {
					art = '<img src="/wissl/art/' + album.id + '" />';
				} else {
					art = '<img src="img/no-artwork.jpg" />';
				}
				$('#art').empty().html(art);

				if (song.title && song.title !== '') {
					document.title = song.title;
				} else {
					document.title = 'wissl';
				}

				if (playing) {
					$('#playlist-' + playing.playlist_id + '-' + playing.position).addClass('playing');
					$('#song-' + song.id).addClass('playing');
					$('#album-' + album.id).addClass('playing');
					$('#artist-' + artist.id).addClass('playing');
				}

				if (!player.hasSound) {
					wsl.error("Cannot play " + song.title + ": no sound");
					return;
				}

				player.sound = soundManager.createSound({
					id : "song_" + song.id,
					url : "/wissl/song/" + song.id + "/stream?sessionId=" + wsl.sessionId,
					type : data.song.format,
					autoPlay : true,
					onfinish : function () {
						player.next();
					},
					onplay : function () {
					},
					whileplaying : function () {
						var width, w1, w2, d1, d2, t, kbps, vol;

						if (player.sound.muted !== player.mute) {
							if (player.mute) {
								player.sound.mute();
							} else {
								player.sound.unmute();
							}
						}
						$('#volume-slider-full').height(player.volume * $('#volume-slider').height() / 100);
						player.sound.setVolume(Math.pow(player.volume / 100, 3) * 100);
						vol = $('#volume-icon');
						vol.removeClass();
						if (player.mute) {
							vol.addClass('volume-mute');
						} else {
							if (player.volume > 75) {
								vol.addClass('volume-high');
							} else if (player.volume > 50) {
								vol.addClass('volume-medium');
							} else if (player.volume > 25) {
								vol.addClass('volume-low');
							} else {
								vol.addClass('volume-zero');
							}
						}

						player.song.duration = player.sound.durationEstimate / 1000;
						width = $("#progress").width();
						w1 = (player.sound.position / (player.song.duration * 1000)) * width;
						w2 = (player.sound.bytesLoaded / player.sound.bytesTotal) * width;
						d1 = wsl.formatSeconds(player.sound.position / 1000);
						d2 = wsl.formatSeconds(player.song.duration);
						$("#progress-played").width(w1);
						$("#progress-download").width(w2);
						$("#position").html('<strong>' + d1 + "</strong> / " + d2);

						if (player.sound.bytesLoaded !== player.sound.bytesTotal) {
							t = new Date().getTime();
							if (!player.sound.t) {
								player.sound.t = t;
								player.sound.bytesAtT = player.sound.bytesLoaded;
							}
							if (t - player.sound.t > 1000) {
								kbps = Math.ceil((player.sound.bytesLoaded - player.sound.bytesAtT) / 1024);
								$('#download-rate').empty().html(kbps + 'Kbps').show();
								player.sound.t = t;
								player.sound.bytesAtT = player.sound.bytesLoaded;
							}
						} else {
							$('#download-rate').hide();
						}
					}
				});
			},
			error : function (xhr) {
				wsl.ajaxError("failed to get song " + playing.song_id, xhr);
			}
		});
	};

	player.togglePlay = function () {
		if (player.sound) {
			player.sound.togglePause();
			if (player.sound.paused) {
				$('#play').removeClass('pause');
			} else {
				$('#play').addClass('pause');
			}
		}
	};

	player.showSeek = function (event) {
		if (player.sound) {
			var progress, x, w, time, elt;
			progress = $("#progress");
			x = event.clientX - progress.offset().left;
			w = progress.width();
			time = wsl.formatSeconds((x / w) * player.song.duration);
			elt = $('#seek-popup');
			elt.html(time).show();
			elt.css('left', event.clientX);
			elt.css('top', 30);
		}
	};

	player.hideSeek = function () {
		$('#seek-popup').hide();
	};

	player.seek = function (event) {
		if (player.sound) {
			var x, w;
			x = event.clientX - $("#progress").offset().left;
			w = $("#progress").width();
			player.sound.setPosition((x / w) * player.song.duration * 1000);
		}
	};

	player.destroySound = function () {
		if (player.sound) {
			player.sound.destruct();
			player.sound = null;
		}
		if (player.playing) {
			player.playing = null;
		}
	};

	player.stop = function () {
		if (player.sound) {
			$('#player').fadeOut(300);
			player.destroySound();
			$('#play').removeClass('pause');
			$("#progress-played").width(0);
			$("#progress-download").width(0);
			$("#playing-title").empty();
			$("#playing-artist").empty();
			$("#playing-album").empty();
			$('.playing').removeClass('playing');
			$('#playing').hide();
			document.title = 'wissl';
		}
	};

	player.previous = function () {
		if (player.playing) {
			player.sound.destruct();
			var p = player.playing;
			$.ajax({
				url : "/wissl/playlist/" + p.playlist_id + "/song/" + (p.position - 1),
				headers : {
					"sessionId" : wsl.sessionId
				},
				dataType : "json",
				success : function (data) {
					if (data && data.id) {
						player.play({
							song_id : data.id,
							playlist_id : p.playlist_id,
							playlist_name : p.playlist_name,
							position : p.position - 1
						});
					} else {
						player.stop();
					}
				},
				error : function (xhr) {
					wsl.ajaxError("Failed to get previous song in playlist", xhr);
				}
			});
		}
	};

	player.next = function () {
		if (player.playing) {
			player.sound.destruct();
			var p = player.playing;
			$.ajax({
				url : "/wissl/playlist/" + p.playlist_id + "/song/" + (p.position + 1),
				headers : {
					"sessionId" : wsl.sessionId
				},
				dataType : "json",
				success : function (data) {
					if (data && data.id) {
						player.play({
							song_id : data.id,
							playlist_id : p.playlist_id,
							playlist_name : p.playlist_name,
							position : p.position + 1
						});
					} else {
						player.stop();
					}
				},
				error : function (xhr) {
					wsl.ajaxError("Failed to get next song in playlist", xhr);
				}
			});
		}
	};

	player.toggleMute = function () {
		player.mute = !player.mute;
	};

	player.showVolume = function () {
		$('#volume-container').show();
	};

	player.hideVolume = function () {
		$('#volume-container').hide();
	};

	player.adjustVolume = function (event) {
		var y, h, vol, vs;
		vs = $("#volume-slider");
		h = vs.height();
		y = h - event.clientY + vs.offset().top;

		vol = (y / h) * 100;
		vol = Math.min(100, vol);
		vol = Math.max(0, vol);
		player.volume = vol;
	};

}(player));

soundManager.onready(function () {
	'use strict';
	player.hasSound = true;
});
soundManager.ontimeout(function () {
	'use strict';
	wsl.error("Failed to start soundmanager2");
});