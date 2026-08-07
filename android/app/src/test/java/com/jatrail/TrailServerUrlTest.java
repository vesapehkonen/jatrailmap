package com.jatrail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrailServerUrlTest {
    @Test
    public void acceptsHttpAndHttpsServers() {
        assertTrue(TrailServerUrl.isValid("http://192.168.1.20:8000/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("http://homeserver.local/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("https://jatrailmap.com/api/v1/trails"));
    }

    @Test
    public void rejectsOtherSchemesAndIncompleteUrls() {
        assertFalse(TrailServerUrl.isValid("ftp://homeserver.local/trails"));
        assertFalse(TrailServerUrl.isValid("homeserver.local/trails"));
        assertFalse(TrailServerUrl.isValid("http:///api/v1/trails"));
        assertFalse(TrailServerUrl.isValid(""));
    }
}
