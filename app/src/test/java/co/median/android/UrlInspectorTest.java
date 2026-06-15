package co.median.android;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class UrlInspectorTest {

    private UrlInspector inspector;

    @Before
    public void setUp() throws Exception {
        // Reset the singleton so each test starts fresh
        Field instanceField = UrlInspector.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        inspector = UrlInspector.getInstance();
    }

    @Test
    public void getInstance_returnsSameInstance() {
        UrlInspector first = UrlInspector.getInstance();
        UrlInspector second = UrlInspector.getInstance();
        assertSame(first, second);
    }

    @Test
    public void getUserId_initiallyNull() {
        assertNull(inspector.getUserId());
    }

    @Test
    public void inspectUrl_noRegex_userIdRemainsNull() {
        inspector.inspectUrl("https://example.com/user/123");
        assertNull(inspector.getUserId());
    }

    @Test
    public void inspectUrl_withMatchingRegex_extractsUserId() throws Exception {
        setUserIdRegex(inspector, Pattern.compile("/user/(\\d+)"));

        inspector.inspectUrl("https://example.com/user/42");

        assertEquals("42", inspector.getUserId());
    }

    @Test
    public void inspectUrl_withNonMatchingRegex_userIdRemainsNull() throws Exception {
        setUserIdRegex(inspector, Pattern.compile("/user/(\\d+)"));

        inspector.inspectUrl("https://example.com/profile/abc");

        assertNull(inspector.getUserId());
    }

    @Test
    public void inspectUrl_multipleMatches_extractsFirstGroup() throws Exception {
        setUserIdRegex(inspector, Pattern.compile("/user/(\\w+)/(\\w+)"));

        inspector.inspectUrl("https://example.com/user/john/settings");

        assertEquals("john", inspector.getUserId());
    }

    @Test
    public void inspectUrl_updatesUserIdOnSubsequentMatch() throws Exception {
        setUserIdRegex(inspector, Pattern.compile("/user/(\\d+)"));

        inspector.inspectUrl("https://example.com/user/100");
        assertEquals("100", inspector.getUserId());

        inspector.inspectUrl("https://example.com/user/200");
        assertEquals("200", inspector.getUserId());
    }

    @Test
    public void inspectUrl_regexWithNoCapturingGroup_doesNotSetUserId() throws Exception {
        setUserIdRegex(inspector, Pattern.compile("/user/\\d+"));

        inspector.inspectUrl("https://example.com/user/42");

        assertNull(inspector.getUserId());
    }

    @Test
    public void inspectUrl_emptyUrl_doesNotCrash() throws Exception {
        setUserIdRegex(inspector, Pattern.compile("/user/(\\d+)"));
        inspector.inspectUrl("");
        assertNull(inspector.getUserId());
    }

    @Test
    public void inspectUrl_complexRegex_extractsCorrectly() throws Exception {
        setUserIdRegex(inspector, Pattern.compile("[?&]uid=([^&]+)"));

        inspector.inspectUrl("https://example.com/page?uid=abc123&other=val");

        assertEquals("abc123", inspector.getUserId());
    }

    private void setUserIdRegex(UrlInspector inspector, Pattern pattern) throws Exception {
        Field regexField = UrlInspector.class.getDeclaredField("userIdRegex");
        regexField.setAccessible(true);
        regexField.set(inspector, pattern);
    }
}
