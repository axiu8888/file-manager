package com.kiftd.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ContentDispositionUtil {

    private ContentDispositionUtil() {}

    /** RFC 5987 inline disposition for UTF-8 filenames (browser tab / download name). */
    public static String inline(String fileName) {
        String safe = fileName == null || fileName.isBlank() ? "file" : fileName.replace("\"", "");
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        String ascii = safe.replaceAll("[^\\x20-\\x7E]", "_");
        if (ascii.isBlank()) {
            ascii = "file";
        }
        return "inline; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
