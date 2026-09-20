package com.kiftd.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.ok(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({
            org.springframework.web.multipart.MaxUploadSizeExceededException.class,
            org.springframework.web.multipart.MultipartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMultipart(Exception e, HttpServletRequest request) {
        Long max = null;
        if (e instanceof org.springframework.web.multipart.MaxUploadSizeExceededException maxEx) {
            max = maxEx.getMaxUploadSize();
        }
        log.warn("upload rejected: uri={}, contentLength={}, maxUploadSize={}, msg={}, cause={}",
                request.getRequestURI(),
                request.getContentLengthLong(),
                max,
                e.getMessage(),
                e.getCause() == null ? null : e.getCause().toString());
        String msg = "上传失败";
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof org.springframework.web.multipart.MaxUploadSizeExceededException
                || raw.toLowerCase().contains("size")
                || raw.toLowerCase().contains("exceed")) {
            String cause = e.getCause() == null ? "" : e.getCause().toString();
            if (cause.contains("Header section") || cause.contains("512 bytes")) {
                msg = "上传失败：文件名过长（multipart 头部超过限制）。请缩短文件名后重试。";
            } else {
                msg = "上传失败：请求体超过限制或上传中断。请单文件重试；若文件名很长请先缩短文件名。";
            }
        } else if (!raw.isBlank()) {
            msg = "上传失败: " + raw;
        }
        return ResponseEntity.ok(ApiResponse.fail(413, msg));
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
