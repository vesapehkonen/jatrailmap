package com.jatrail;

import java.net.URI;

final class TrailServerUrl {
    private TrailServerUrl() {}

    static boolean isValid(String value) {
        return isHttps(value) || isAllowedCleartext(value);
    }

    static boolean isHttps(String value) {
        URI uri = parse(value);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme());
    }

    static boolean isHttp(String value) {
        URI uri = parse(value);
        return uri != null && "http".equalsIgnoreCase(uri.getScheme());
    }

    static boolean isAllowedCleartext(String value) {
        URI uri = parse(value);
        if (uri == null || !"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost().toLowerCase(java.util.Locale.US);
        if ("localhost".equals(host) || host.endsWith(".local")) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        try {
            int[] octets = new int[4];
            for (int index = 0; index < parts.length; index++) {
                octets[index] = Integer.parseInt(parts[index]);
                if (octets[index] < 0 || octets[index] > 255) {
                    return false;
                }
            }
            return octets[0] == 10
                    || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                    || (octets[0] == 192 && octets[1] == 168);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static URI parse(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() == null ? null : uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
