package com.jatrail;

import java.net.URI;

final class TrailServerUrl {
    private TrailServerUrl() {}

    static boolean isValid(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
