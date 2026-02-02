/* Custom JavaScript for the application */

(function () {
  if (!navigator || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return;
  if (navigator.mediaDevices.__median_webrtc_wrapped) return;
  navigator.mediaDevices.__median_webrtc_wrapped = true;

  var originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);

  function sendEndedEvent() {
    try {
      if (window.JSBridge && typeof JSBridge.postMessage === 'function') {
        JSBridge.postMessage(JSON.stringify({
          __median_webrtc: true,
          type: 'audioTrack',
          event: 'ended'
        }));
      }
    } catch (e) {
      // best-effort only
    }
  }

  navigator.mediaDevices.getUserMedia = function (constraints) {
    var wantsAudio = false;
    if (constraints && typeof constraints === 'object' && constraints.audio) {
      wantsAudio = true;
    }

    return originalGetUserMedia(constraints).then(function (stream) {
      if (wantsAudio && stream && typeof stream.getAudioTracks === 'function') {
        var tracks = stream.getAudioTracks();
        if (tracks && tracks.length) {
          var activeCount = tracks.length;
          var endedNotified = false;

          function maybeNotifyEnded() {
            if (endedNotified) return;
            endedNotified = true;
            sendEndedEvent();
          }

          tracks.forEach(function (track) {
            var onEnded = function () {
              try { track.removeEventListener('ended', onEnded); } catch (e) {}
              activeCount -= 1;
              if (activeCount <= 0) {
                maybeNotifyEnded();
              }
            };
            try { track.addEventListener('ended', onEnded); } catch (e) {}
          });

          // Some implementations fire `inactive` on the stream when all tracks stop.
          if (typeof stream.addEventListener === 'function') {
            var onInactive = function () {
              try { stream.removeEventListener('inactive', onInactive); } catch (e) {}
              maybeNotifyEnded();
            };
            try { stream.addEventListener('inactive', onInactive); } catch (e) {}
          }
        }
      }
      return stream;
    }).catch(function (err) {
      // If getUserMedia fails after permission was granted, make sure we
      // still emit a single end signal so native can clean up.
      sendEndedEvent();
      throw err;
    });
  };
})();
