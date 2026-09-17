package com.kiftd.webdav;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import java.util.List;

@Configuration
public class WebDavConfig {

    @Bean
    public HttpFirewall webDavHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(List.of(
                "GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH",
                "PROPFIND", "PROPPATCH", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK"
        ));
        return firewall;
    }

    @Bean
    public WebSecurityCustomizer webDavSecurityCustomizer(HttpFirewall webDavHttpFirewall) {
        return web -> web.httpFirewall(webDavHttpFirewall);
    }
}
