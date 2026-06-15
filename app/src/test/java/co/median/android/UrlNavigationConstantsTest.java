package co.median.android;

import org.junit.Test;

import static org.junit.Assert.*;

public class UrlNavigationConstantsTest {

    @Test
    public void offlinePageUrl_hasCorrectValue() {
        assertEquals("file:///android_asset/offline.html", UrlNavigation.OFFLINE_PAGE_URL);
    }

    @Test
    public void offlinePageUrlRaw_hasCorrectValue() {
        assertEquals("file:///offline.html", UrlNavigation.OFFLINE_PAGE_URL_RAW);
    }

    @Test
    public void defaultHtmlSize_is10KB() {
        assertEquals(10 * 1024, UrlNavigation.DEFAULT_HTML_SIZE);
    }

    @Test
    public void offlinePageUrl_startsWithFileScheme() {
        assertTrue(UrlNavigation.OFFLINE_PAGE_URL.startsWith("file:///"));
    }

    @Test
    public void offlinePageUrl_containsOfflineHtml() {
        assertTrue(UrlNavigation.OFFLINE_PAGE_URL.contains("offline.html"));
    }

    @Test
    public void offlinePageUrl_containsAndroidAsset() {
        assertTrue(UrlNavigation.OFFLINE_PAGE_URL.contains("android_asset"));
    }

    @Test
    public void offlinePageUrlRaw_doesNotContainAndroidAsset() {
        assertFalse(UrlNavigation.OFFLINE_PAGE_URL_RAW.contains("android_asset"));
    }

    @Test
    public void defaultHtmlSize_isPositive() {
        assertTrue(UrlNavigation.DEFAULT_HTML_SIZE > 0);
    }
}
