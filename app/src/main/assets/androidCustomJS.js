/* Android specific custom JavaScript for the application */

(function () {
  // Instrument navigator.mediaDevices.getUserMedia to detect when WebRTC
  // audio tracks are created and ended. This lets the native layer manage
  // a Foreground Service only while microphone is actually in use.
  if (!navigator || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    return;
  }

  var originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);

  function sendToNative(event) {
    try {
      if (window.JSBridge && typeof JSBridge.postMessage === 'function') {
        JSBridge.postMessage(JSON.stringify(event));
      }
    } catch (e) {
      // Swallow any errors; this is best-effort debug/instrumentation.
    }
  }

  navigator.mediaDevices.getUserMedia = function (constraints) {
    var wantsAudio = false;
    if (constraints && typeof constraints === 'object') {
      if (constraints.audio) {
        wantsAudio = true;
      }
    }

    return originalGetUserMedia(constraints).then(function (stream) {
      if (wantsAudio && stream && typeof stream.getAudioTracks === 'function') {
        var audioTracks = stream.getAudioTracks();
        if (audioTracks && audioTracks.length) {
          audioTracks.forEach(function (track) {
            // Notify native that an audio track has started.
            sendToNative({
              __median_webrtc: true,
              type: 'audioTrack',
              event: 'started'
            });

            // When the track ends (e.g., track.stop() or underlying source
            // is closed), notify native so it can decrement its count.
            var onEnded = function () {
              try {
                track.removeEventListener('ended', onEnded);
              } catch (e) {}
              sendToNative({
                __median_webrtc: true,
                type: 'audioTrack',
                event: 'ended'
              });
            };

            try {
              track.addEventListener('ended', onEnded);
            } catch (e) {
              // Older implementations may not support addEventListener on tracks.
              // As a fallback, we won't get an "ended" event in that case.
            }
          });
        }
      }

      return stream;
    }).catch(function (err) {
      // Propagate errors to caller unchanged.
      throw err;
    });
  };
})();
