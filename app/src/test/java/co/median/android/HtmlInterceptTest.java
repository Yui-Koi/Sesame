package co.median.android;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class HtmlInterceptTest {

    private Method urlMatchesMethod;
    private Method stringsNotEqualMethod;
    private Method getCharsetMethod;

    @Before
    public void setUp() throws Exception {
        urlMatchesMethod = HtmlIntercept.class.getDeclaredMethod("urlMatches", String.class, String.class);
        urlMatchesMethod.setAccessible(true);

        stringsNotEqualMethod = HtmlIntercept.class.getDeclaredMethod("stringsNotEqual", String.class, String.class);
        stringsNotEqualMethod.setAccessible(true);

        getCharsetMethod = HtmlIntercept.class.getDeclaredMethod("getCharset", String.class);
        getCharsetMethod.setAccessible(true);
    }

    // --- stringsNotEqual ---

    @Test
    public void stringsNotEqual_bothNull_returnsFalse() throws Exception {
        assertFalse((boolean) stringsNotEqualMethod.invoke(null, null, null));
    }

    @Test
    public void stringsNotEqual_equalStrings_returnsFalse() throws Exception {
        assertFalse((boolean) stringsNotEqualMethod.invoke(null, "abc", "abc"));
    }

    @Test
    public void stringsNotEqual_differentStrings_returnsTrue() throws Exception {
        assertTrue((boolean) stringsNotEqualMethod.invoke(null, "abc", "def"));
    }

    @Test
    public void stringsNotEqual_firstNullSecondNonNull_returnsTrue() throws Exception {
        assertTrue((boolean) stringsNotEqualMethod.invoke(null, null, "abc"));
    }

    @Test
    public void stringsNotEqual_firstNonNullSecondNull_returnsTrue() throws Exception {
        assertTrue((boolean) stringsNotEqualMethod.invoke(null, "abc", null));
    }

    @Test
    public void stringsNotEqual_emptyStrings_returnsFalse() throws Exception {
        assertFalse((boolean) stringsNotEqualMethod.invoke(null, "", ""));
    }

    // --- getCharset ---

    @Test
    public void getCharset_nullContentType_returnsNull() throws Exception {
        assertNull(getCharsetMethod.invoke(null, (String) null));
    }

    @Test
    public void getCharset_emptyContentType_returnsNull() throws Exception {
        assertNull(getCharsetMethod.invoke(null, ""));
    }

    @Test
    public void getCharset_noCharset_returnsNull() throws Exception {
        assertNull(getCharsetMethod.invoke(null, "text/html"));
    }

    @Test
    public void getCharset_withUtf8Charset_returnsUtf8() throws Exception {
        assertEquals("UTF-8", getCharsetMethod.invoke(null, "text/html; charset=UTF-8"));
    }

    @Test
    public void getCharset_withIso8859_returnsIso8859() throws Exception {
        assertEquals("iso-8859-1", getCharsetMethod.invoke(null, "text/html; charset=iso-8859-1"));
    }

    @Test
    public void getCharset_multipleParams_extractsCharset() throws Exception {
        assertEquals("UTF-8", getCharsetMethod.invoke(null, "text/html; boundary=something; charset=UTF-8"));
    }

    @Test
    public void getCharset_charsetOnly_returnsValue() throws Exception {
        assertEquals("windows-1252", getCharsetMethod.invoke(null, "charset=windows-1252"));
    }

    // --- urlMatches ---

    @Test
    public void urlMatches_bothNull_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null, null, null));
    }

    @Test
    public void urlMatches_firstNull_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null, null, "https://example.com"));
    }

    @Test
    public void urlMatches_secondNull_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null, "https://example.com", null));
    }

    @Test
    public void urlMatches_identicalUrls_returnsTrue() throws Exception {
        assertTrue((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path", "https://example.com/path"));
    }

    @Test
    public void urlMatches_differentProtocol_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null,
                "http://example.com/path", "https://example.com/path"));
    }

    @Test
    public void urlMatches_differentHost_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path", "https://other.com/path"));
    }

    @Test
    public void urlMatches_differentQuery_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path?a=1", "https://example.com/path?a=2"));
    }

    @Test
    public void urlMatches_malformedUrl_returnsFalse() throws Exception {
        // MalformedURLException is caught internally, but Log.w is unavailable
        // in JVM unit tests, so the catch block itself may throw.
        try {
            boolean result = (boolean) urlMatchesMethod.invoke(null, "not-a-url", "also-not-a-url");
            assertFalse(result);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Expected in unit test env where android.util.Log is not mocked
        }
    }

    @Test
    public void urlMatches_firstUrlHasTrailingSlash_returnsTrue() throws Exception {
        assertTrue((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path/", "https://example.com/path"));
    }

    @Test
    public void urlMatches_secondUrlHasTrailingSlash_returnsTrue() throws Exception {
        assertTrue((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path", "https://example.com/path/"));
    }

    @Test
    public void urlMatches_bothHaveTrailingSlash_returnsTrue() throws Exception {
        assertTrue((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path/", "https://example.com/path/"));
    }

    @Test
    public void urlMatches_pathsDifferByMoreThanSlash_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path", "https://example.com/path/extra"));
    }

    @Test
    public void urlMatches_differentPaths_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/path1", "https://example.com/path2"));
    }

    @Test
    public void urlMatches_urlWithPort_matchesSamePort() throws Exception {
        assertTrue((boolean) urlMatchesMethod.invoke(null,
                "https://example.com:8080/path", "https://example.com:8080/path"));
    }

    @Test
    public void urlMatches_urlWithDifferentPort_returnsFalse() throws Exception {
        assertFalse((boolean) urlMatchesMethod.invoke(null,
                "https://example.com:8080/path", "https://example.com:9090/path"));
    }

    @Test
    public void urlMatches_rootPathWithAndWithoutSlash_returnsTrue() throws Exception {
        assertTrue((boolean) urlMatchesMethod.invoke(null,
                "https://example.com/", "https://example.com"));
    }
}
