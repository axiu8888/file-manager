package com.kiftd.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * 保证大文件上传限制生效，并在启动日志打印实际值便于排查。
 */
@Configuration
public class MultipartUploadConfig {

    private static final Logger log = LoggerFactory.getLogger(MultipartUploadConfig.class);

    @Bean
    ApplicationRunner logMultipartLimits(MultipartProperties multipartProperties) {
        return (ApplicationArguments args) -> {
            DataSize maxFile = multipartProperties.getMaxFileSize();
            DataSize maxReq = multipartProperties.getMaxRequestSize();
            DataSize threshold = multipartProperties.getFileSizeThreshold();
            log.info("multipart limits: max-file-size={}, max-request-size={}, file-size-threshold={}",
                    maxFile, maxReq, threshold);
            if (maxFile != null && maxFile.toBytes() > 0 && maxFile.toBytes() < DataSize.ofMegabytes(50).toBytes()) {
                log.warn("max-file-size 过小（{}），大文件上传会失败；请检查 application.yml / 启动 -D 是否生效", maxFile);
            }
        };
    }

    /**
     * Tomcat 默认 maxPostSize≈2MB；multipart 单 part 头默认仅 512 字节。
     * 超长中文文件名会导致：Header section has more than 512 bytes（常被包装成 Maximum upload size exceeded）。
     */
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatMaxPostSizeCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setMaxPostSize(-1);
            // 放宽 multipart 每个 part 的头大小（含 Content-Disposition 文件名）
            connector.setMaxPartHeaderSize(16 * 1024);
            connector.setMaxPartCount(100);
            var handler = connector.getProtocolHandler();
            if (handler instanceof AbstractHttp11Protocol<?> protocol) {
                protocol.setMaxSwallowSize(-1);
                log.info("Tomcat connector: maxPostSize=-1, maxSwallowSize=-1, maxPartHeaderSize=16KB");
            } else {
                connector.setProperty("maxSwallowSize", "-1");
                log.info("Tomcat connector: maxPostSize=-1, maxPartHeaderSize=16KB");
            }
        });
    }
}
