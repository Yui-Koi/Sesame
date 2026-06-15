package co.median.android;

import org.junit.Test;

import static org.junit.Assert.*;

public class WebviewLoadStateTest {

    @Test
    public void enum_hasExpectedValues() {
        WebviewLoadState[] values = WebviewLoadState.values();
        assertEquals(4, values.length);
    }

    @Test
    public void enum_containsStateUnknown() {
        assertNotNull(WebviewLoadState.valueOf("STATE_UNKNOWN"));
    }

    @Test
    public void enum_containsStateStartLoad() {
        assertNotNull(WebviewLoadState.valueOf("STATE_START_LOAD"));
    }

    @Test
    public void enum_containsStatePageStarted() {
        assertNotNull(WebviewLoadState.valueOf("STATE_PAGE_STARTED"));
    }

    @Test
    public void enum_containsStateDone() {
        assertNotNull(WebviewLoadState.valueOf("STATE_DONE"));
    }

    @Test
    public void ordinals_areSequential() {
        assertEquals(0, WebviewLoadState.STATE_UNKNOWN.ordinal());
        assertEquals(1, WebviewLoadState.STATE_START_LOAD.ordinal());
        assertEquals(2, WebviewLoadState.STATE_PAGE_STARTED.ordinal());
        assertEquals(3, WebviewLoadState.STATE_DONE.ordinal());
    }

    @Test(expected = IllegalArgumentException.class)
    public void valueOf_invalidName_throws() {
        WebviewLoadState.valueOf("INVALID_STATE");
    }

    @Test
    public void name_returnsExpectedStrings() {
        assertEquals("STATE_UNKNOWN", WebviewLoadState.STATE_UNKNOWN.name());
        assertEquals("STATE_START_LOAD", WebviewLoadState.STATE_START_LOAD.name());
        assertEquals("STATE_PAGE_STARTED", WebviewLoadState.STATE_PAGE_STARTED.name());
        assertEquals("STATE_DONE", WebviewLoadState.STATE_DONE.name());
    }
}
