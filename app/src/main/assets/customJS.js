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
          tracks.forEach(function (track) {
            var onEnded = function () {
              try { track.removeEventListener('ended', onEnded); } catch (e) {}
              sendEndedEvent();
            };
            try { track.addEventListener('ended', onEnded); } catch (e) {}
          });
        }
      }
      return stream;
    });
  };
})();
