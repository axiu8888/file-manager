package com.kiftd.controller;

import com.kiftd.aop.HttpLoggingIgnore;
import com.kiftd.common.ApiResponse;
import com.kiftd.service.SystemService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/os")
    public ApiResponse<String> os() {
        return ApiResponse.ok(systemService.osInfo());
    }

    @HttpLoggingIgnore
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok(systemService.ping());
    }

    @GetMapping("/notice/md5")
    public ApiResponse<Map<String, String>> noticeMd5() {
        return ApiResponse.ok(systemService.noticeMd5());
    }

    @GetMapping("/notice")
    public ApiResponse<String> notice() {
        return ApiResponse.ok(systemService.noticeContext());
    }

    @PutMapping("/notice")
    public ApiResponse<Void> updateNotice(@RequestBody Map<String, String> body) {
        systemService.updateNotice(body.getOrDefault("content", ""));
        return ApiResponse.ok();
    }
}
