package co.median.android;

import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import co.median.median_core.GNLog;

/**
 * Shared utility for posting JSON payloads over HTTP.
 */
class HttpPostHelper {
    private static final String TAG = HttpPostHelper.class.getName();

    /**
     * Posts a JSON body to the given URL synchronously.
     * Must be called from a background thread.
     *
     * @return the HTTP response code, or -1 on failure
     */
    static int postJson(String targetUrl, JSONObject json) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
            writer.write(json.toString());
            writer.close();
            connection.connect();
            return connection.getResponseCode();
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, "Error posting to " + targetUrl, e);
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
