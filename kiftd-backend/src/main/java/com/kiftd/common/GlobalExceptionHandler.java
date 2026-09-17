package com.kiftd.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.ok(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.fail(msg));
    }

    /** 客户端拖动进度条/关闭预览时断开，或流超时，属常见现象 */
    @ExceptionHandler({AsyncRequestNotUsableException.class, AsyncRequestTimeoutException.class})
    public ResponseEntity<Void> handleAsyncStreamGone(Exception e, HttpServletRequest request) {
        log.debug("stream aborted: {} {}", request.getRequestURI(), e.toString());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e, HttpServletResponse response) {
        if (isClientAbort(e)) {
            log.debug("client aborted: {}", e.toString());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        if (response.isCommitted()) {
            log.debug("skip error body, response already committed: {}", e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String ct = response.getContentType();
        if (ct != null && !ct.startsWith(MediaType.APPLICATION_JSON_VALUE) && !ct.startsWith("text/")) {
            log.warn("media stream error ({}): {}", ct, e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail("服务器内部错误: " + e.getMessage()));
    }

    private static boolean isClientAbort(Throwable e) {
        while (e != null) {
            String name = e.getClass().getName();
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (name.contains("ClientAbortException")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection reset")
                    || msg.contains("Abort")
                    || msg.contains("disconnected client")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
