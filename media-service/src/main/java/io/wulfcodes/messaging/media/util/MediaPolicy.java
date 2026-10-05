package io.wulfcodes.messaging.media.util;

import io.wulfcodes.messaging.common.model.vo.ContentType;
import io.wulfcodes.messaging.media.model.vo.MediaRule;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Decides, from the MIME type and file name, what kind of attachment an upload is and how big it
 * may be. The SERVER decides the content type: the client's own labelling is never trusted.
 */
public final class MediaPolicy {

    static final long MB = 1024L * 1024;

    private static final Set<String> IMAGE = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> VIDEO = Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final Set<String> AUDIO = Set.of("audio/mpeg", "audio/ogg", "audio/wav", "audio/webm",
            "audio/mp4", "audio/x-m4a", "audio/aac");

    /** Executables/scripts are refused outright, whatever MIME type the browser claims. */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "msi", "bat", "cmd", "com", "scr",
            "ps1", "sh", "jar", "apk", "dll", "vbs", "js");
    private static final Set<String> BLOCKED_MIME = Set.of("application/x-msdownload", "application/x-sh",
            "application/x-executable", "application/java-archive", "application/x-msdos-program",
            "text/javascript", "application/javascript");

    private MediaPolicy() {
    }

    /** @return the rule for this upload, or empty if the file type is not allowed at all */
    public static Optional<MediaRule> classify(String mimeType, String fileName) {
        String mime = mimeType.toLowerCase(Locale.ROOT).trim();
        if (BLOCKED_MIME.contains(mime) || BLOCKED_EXTENSIONS.contains(extension(fileName))) {
            return Optional.empty();
        }
        if (IMAGE.contains(mime)) return Optional.of(new MediaRule(ContentType.IMAGE, 10 * MB));
        if (VIDEO.contains(mime)) return Optional.of(new MediaRule(ContentType.VIDEO, 100 * MB));
        if (AUDIO.contains(mime)) return Optional.of(new MediaRule(ContentType.AUDIO, 20 * MB));
        return Optional.of(new MediaRule(ContentType.FILE, 25 * MB));
    }

    /**
     * Makes a user-supplied file name safe as part of an object key and in a download header:
     * keeps letters, digits, dot, dash, underscore; drops paths; bounds the length.
     */
    public static String sanitizeFileName(String fileName) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_");
        if (name.isBlank() || name.chars().allMatch(c -> c == '.')) {
            name = "file";
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
