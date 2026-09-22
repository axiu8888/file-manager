package com.kiftd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiftd.common.ApiResponse;
import com.kiftd.security.JwtAuthFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final KiftdProperties props;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, KiftdProperties props, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "未登录或登录已失效，请重新登录"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, 403, "没有权限执行此操作")))
                .authorizeHttpRequests(auth -> {
                    String api = props.normalizedApiPrefix();
                    // 视频/下载用 StreamingResponseBody，Tomcat 会再做一次 ASYNC 派发。
                    // Spring Security 6 的 MVC 匹配器不覆盖这次派发，登录态也带不过去，会误报 Access Denied。
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(
                                "/auth/public-key", api + "/auth/public-key",
                                "/auth/captcha", api + "/auth/captcha",
                                "/auth/signup-enabled", api + "/auth/signup-enabled",
                                "/auth/login", api + "/auth/login",
                                "/auth/signup", api + "/auth/signup",
                                "/auth/logout", api + "/auth/logout"
                        ).permitAll()
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

    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message) throws java.io.IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
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
