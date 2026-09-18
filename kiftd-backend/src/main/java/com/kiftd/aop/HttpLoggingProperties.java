package com.kiftd.aop;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "kiftd.http-logging")
public record HttpLoggingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean multiLine,
        @DefaultValue({"/api/preview/resource/**", "/api/preview/thumb/**", "/api/system/ping", "/webdav", "/webdav/**"})
        List<String> excludePaths
) {
}
