package com.kiftd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        Webdav webdav
) {
    public record Jwt(String secret, long expireHours) {}
    public record Storage(String root, String temp) {}
    public record Signup(boolean enabled) {}
    public record Ffmpeg(String path) {}
    public record Cors(String allowedOrigins) {}
    public record Admin(String username, String password) {}
    public record Webdav(boolean enabled, String path) {}
}
