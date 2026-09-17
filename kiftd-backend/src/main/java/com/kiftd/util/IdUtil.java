package com.kiftd.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class IdUtil {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private IdUtil() {}

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String now() {
        return LocalDateTime.now().format(FMT);
    }
}
