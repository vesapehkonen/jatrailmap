package com.jatrail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrailServerUrlTest {
    @Test
    public void acceptsHttpsAndLocalHttpServers() {
        assertTrue(TrailServerUrl.isValid("http://192.168.1.20:8000/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("http://homeserver.local/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("http://localhost:8000/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("http://10.4.5.6/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("http://172.16.0.1/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("http://172.31.255.254/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("https://jatrail.com/api/v1/trails"));
        assertTrue(TrailServerUrl.isValid("https://public.example/api/v1/trails"));
    }

    @Test
    public void rejectsPublicAndNonPrivateHttpServers() {
        assertFalse(TrailServerUrl.isValid("http://jatrail.com/api/v1/trails"));
        assertFalse(TrailServerUrl.isValid("http://8.8.8.8/api/v1/trails"));
        assertFalse(TrailServerUrl.isValid("http://172.15.0.1/api/v1/trails"));
        assertFalse(TrailServerUrl.isValid("http://172.32.0.1/api/v1/trails"));
        assertFalse(TrailServerUrl.isValid("http://192.167.1.1/api/v1/trails"));
        assertTrue(TrailServerUrl.isHttp("http://jatrail.com/api/v1/trails"));
        assertFalse(TrailServerUrl.isAllowedCleartext(
                "http://jatrail.com/api/v1/trails"));
    }

    @Test
    public void rejectsOtherSchemesAndIncompleteUrls() {
        assertFalse(TrailServerUrl.isValid("ftp://homeserver.local/trails"));
        assertFalse(TrailServerUrl.isValid("homeserver.local/trails"));
        assertFalse(TrailServerUrl.isValid("http:///api/v1/trails"));
        assertFalse(TrailServerUrl.isValid(""));
    }
}
