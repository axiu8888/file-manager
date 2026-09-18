package com.kiftd.config;

import com.kiftd.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final KiftdProperties props;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, KiftdProperties props) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.props = props;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    String api = props.normalizedApiPrefix();
                    auth.requestMatchers("/error").permitAll()
                        .requestMatchers("/auth/**", api + "/auth/**").permitAll()
                        .requestMatchers("/system/**", api + "/system/**").permitAll()
                        .requestMatchers("/links/**", api + "/links/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                api + "/folders/view",
                                api + "/folders/remaining",
                                api + "/folders/*/count").permitAll()
                        .requestMatchers(HttpMethod.GET, api + "/preview/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/webdav", "/webdav/**").permitAll()
                        .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            String raw = props.cors() == null || props.cors().allowedOrigins() == null
                    ? ""
                    : props.cors().allowedOrigins();
            List<String> patterns = new ArrayList<>(Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
            if (patterns.isEmpty() || patterns.stream().anyMatch("*"::equals)) {
                patterns = List.of("*");
            }
            CorsConfiguration config = new CorsConfiguration();
            // 不能用 setAllowedOrigins("*") + allowCredentials(true)，Spring 会直接抛异常
            config.setAllowedOriginPatterns(patterns);
            config.setAllowCredentials(true);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "PROPFIND", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK", "HEAD"));
            config.setAllowedHeaders(List.of("*"));
            config.setExposedHeaders(List.of("Content-Disposition", "Accept-Ranges", "Content-Range", "Content-Length"));
            return config;
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
