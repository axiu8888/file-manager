package com.kiftd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kiftd")
public record KiftdProperties(
        boolean embeddedPg,
        Jwt jwt,
        Storage storage,
        Signup signup,
        Ffmpeg ffmpeg,
        Cors cors,
        Admin admin,
        int selectStep,
        Webdav webdav,
        @DefaultValue("/api") String apiPrefix,
        Backup backup
) {
    public record Jwt(String secret, long expireHours) {}
    public record Storage(String root, String temp) {}
    public record Signup(boolean enabled) {}
    public record Ffmpeg(String path) {}
    public record Cors(String allowedOrigins) {}
    public record Admin(String username, String password) {}
    public record Webdav(boolean enabled, String path) {}
    public record Backup(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("./data/backup") String dir,
            @DefaultValue("0 0 1 * * ?") String cron,
            @DefaultValue("Asia/Shanghai") String zone,
            @DefaultValue("30") int keepDays
    ) {}

    public String normalizedApiPrefix() {
        String raw = apiPrefix == null ? "/api" : apiPrefix.trim();
        if (raw.isEmpty() || "/".equals(raw)) {
            return "";
        }
        if (!raw.startsWith("/")) {
            raw = "/" + raw;
        }
        while (raw.length() > 1 && raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    public String apiPath(String suffix) {
        String path = suffix == null ? "" : suffix;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return normalizedApiPrefix() + path;
    }

    public Backup backupOrDefault() {
        return backup != null ? backup : new Backup(true, "./data/backup", "0 0 1 * * ?", "Asia/Shanghai", 30);
    }
}
