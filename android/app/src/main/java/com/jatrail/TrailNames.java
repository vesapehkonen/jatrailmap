package com.jatrail;

public final class TrailNames {
    private TrailNames() {
    }

    public static String defaultName(String createdAt) {
        if (createdAt == null || createdAt.trim().isEmpty()) {
            return "Trail";
        }
        String dateTime = createdAt.trim().replace('T', ' ');
        if (dateTime.length() > 16) {
            dateTime = dateTime.substring(0, 16);
        }
        return "Trail " + dateTime;
    }

    public static String normalized(String name, String createdAt) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() ? defaultName(createdAt) : trimmed;
    }
}
