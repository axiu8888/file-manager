package com.kiftd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KiftdApplication {
    public static void main(String[] args) {
        SpringApplication.run(KiftdApplication.class, args);
    }
}
