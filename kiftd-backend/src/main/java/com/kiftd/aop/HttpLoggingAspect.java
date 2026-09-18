package com.kiftd.aop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kiftd.util.SecurityUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller access log, modeled after spring-doughnut {@code HttpLoggingHandler}.
 */
@Aspect
@Component
@EnableConfigurationProperties(HttpLoggingProperties.class)
public class HttpLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger("HttpLoggingAspect");
    private static final long KB = 1024L;
    private static final long MB = 1024L * 1024L;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final HttpLoggingProperties properties;
    private final ObjectMapper objectMapper;

    public HttpLoggingAspect(HttpLoggingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController) && execution(public * *(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        if (!properties.enabled()) {
            return pjp.proceed();
        }
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        if (ignored(method)) {
            return pjp.proceed();
        }
        ServletRequestAttributes attrs = currentRequest();
        if (attrs == null || excluded(attrs.getRequest())) {
            return pjp.proceed();
        }

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            log.info(format(pjp, method, attrs.getRequest(), elapsedMs(start), null));
            return result;
        } catch (Throwable ex) {
            log.info(format(pjp, method, attrs.getRequest(), elapsedMs(start), ex));
            throw ex;
        }
    }

    private boolean ignored(Method method) {
        return method.isAnnotationPresent(HttpLoggingIgnore.class)
                || method.getDeclaringClass().isAnnotationPresent(HttpLoggingIgnore.class);
    }

    private boolean excluded(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (properties.excludePaths() == null) {
            return false;
        }
        return properties.excludePaths().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }

    private ServletRequestAttributes currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs;
        }
        return null;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String format(ProceedingJoinPoint pjp, Method method, HttpServletRequest request,
                          long tookMs, Throwable error) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(request.getMethod(), request.getRequestURI());
        args.put("class", method.getDeclaringClass().getSimpleName() + "." + method.getName());
        args.put("ip", clientIp(request));
        String user = SecurityUtils.currentUsername();
        if (user != null) {
            args.put("user", user);
        }
        args.put("args", mapArgs(method.getParameters(), pjp.getArgs()));
        args.put("took", tookMs + "ms");
        if (error != null) {
            args.put("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        }

        String separator = properties.multiLine() ? "\n" : ", ";
        StringBuilder sb = new StringBuilder(properties.multiLine() ? "\n" : "");
        args.forEach((key, value) -> sb.append(key).append(": ").append(toValue(value)).append(separator));
        sb.setLength(sb.length() - separator.length());
        return sb.toString();
    }

    private Map<String, Object> mapArgs(Parameter[] parameters, Object[] args) {
        if (parameters == null || parameters.length == 0) {
            return null;
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];
            if (parameter.isAnnotationPresent(HttpLoggingIgnore.class)) {
                mapped.put(parameter.getName(), "[ignored]");
                continue;
            }
            mapped.put(parameter.getName(), describeArg(arg));
        }
        return mapped;
    }

    private Object describeArg(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof ServletRequest) {
            return "[ServletRequest]";
        }
        if (arg instanceof ServletResponse) {
            return "[ServletResponse]";
        }
        if (arg instanceof MultipartFile file) {
            return String.format("[MultipartFile(%s, %s)]", file.getOriginalFilename(), fmtSize(file.getSize()));
        }
        if (arg instanceof MultipartFile[] files) {
            StringBuilder sb = new StringBuilder("MultipartFiles[");
            for (int i = 0; i < files.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                MultipartFile file = files[i];
                sb.append(String.format("(%s, %s)", file.getOriginalFilename(), fmtSize(file.getSize())));
            }
            return sb.append("]").toString();
        }
        if (arg instanceof InputStream) {
            return "[input]";
        }
        if (arg instanceof OutputStream) {
            return "[output]";
        }
        if (arg instanceof Resource || arg instanceof StreamingResponseBody) {
            return "[" + arg.getClass().getSimpleName() + "]";
        }
        if (arg instanceof byte[] bytes) {
            return "[bytes:" + fmtSize(bytes.length) + "]";
        }
        return redact(arg);
    }

    private Object redact(Object value) {
        if (value instanceof Number || value instanceof Boolean || value instanceof Character
                || value instanceof CharSequence) {
            return value;
        }
        try {
            JsonNode node = objectMapper.valueToTree(value);
            redactNode(node);
            return node;
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<String> names = object.fieldNames();
            List<String> fields = new ArrayList<>();
            names.forEachRemaining(fields::add);
            for (String name : fields) {
                if (sensitive(name)) {
                    object.put(name, "***");
                } else {
                    redactNode(object.get(name));
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::redactNode);
        }
    }

    private boolean sensitive(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return key.contains("password") || key.contains("pwd") || key.contains("token")
                || key.contains("secret") || "authorization".equals(key);
    }

    private String toValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence
                || value instanceof Character) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String fmtSize(long size) {
        if (size < KB) {
            return size + "B";
        }
        if (size < MB) {
            return String.format(Locale.ROOT, "%.2fKB", size / (double) KB);
        }
        return String.format(Locale.ROOT, "%.3fMB", size / (double) MB);
    }
}
