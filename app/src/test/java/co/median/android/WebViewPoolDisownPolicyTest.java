package co.median.android;

import org.junit.Test;

import static org.junit.Assert.*;

public class WebViewPoolDisownPolicyTest {

    @Test
    public void enum_hasExpectedValues() {
        WebViewPoolDisownPolicy[] values = WebViewPoolDisownPolicy.values();
        assertEquals(3, values.length);
    }

    @Test
    public void enum_containsAlways() {
        assertNotNull(WebViewPoolDisownPolicy.valueOf("Always"));
    }

    @Test
    public void enum_containsReload() {
        assertNotNull(WebViewPoolDisownPolicy.valueOf("Reload"));
    }

    @Test
    public void enum_containsNever() {
        assertNotNull(WebViewPoolDisownPolicy.valueOf("Never"));
    }

    @Test
    public void defaultPolicy_isReload() {
        assertEquals(WebViewPoolDisownPolicy.Reload, WebViewPoolDisownPolicy.defaultPolicy);
    }

    @Test
    public void defaultPolicy_isMutable() {
        WebViewPoolDisownPolicy original = WebViewPoolDisownPolicy.defaultPolicy;
        try {
            WebViewPoolDisownPolicy.defaultPolicy = WebViewPoolDisownPolicy.Never;
            assertEquals(WebViewPoolDisownPolicy.Never, WebViewPoolDisownPolicy.defaultPolicy);
        } finally {
            WebViewPoolDisownPolicy.defaultPolicy = original;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void valueOf_invalidName_throws() {
        WebViewPoolDisownPolicy.valueOf("Invalid");
    }

    @Test
    public void ordinal_alwaysIsFirst() {
        assertEquals(0, WebViewPoolDisownPolicy.Always.ordinal());
    }

    @Test
    public void ordinal_reloadIsSecond() {
        assertEquals(1, WebViewPoolDisownPolicy.Reload.ordinal());
    }

    @Test
    public void ordinal_neverIsThird() {
        assertEquals(2, WebViewPoolDisownPolicy.Never.ordinal());
    }
}
