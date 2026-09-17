package com.kiftd.controller;

import com.kiftd.common.ApiResponse;
import com.kiftd.service.LinkService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("/chain")
    public ApiResponse<Map<String, String>> createChain(@RequestBody Map<String, String> body) {
        String key = linkService.createChain(body.get("fileId"));
        return ApiResponse.ok(Map.of("chainKey", key));
    }

    @PostMapping("/download-key")
    public ApiResponse<Map<String, String>> createDownloadKey(@RequestBody Map<String, String> body) {
        String key = linkService.createDownloadKey(body.get("fileId"));
        return ApiResponse.ok(Map.of("downloadKey", key));
    }

    @GetMapping("/chain/{chainKey}")
    public ResponseEntity<Resource> byChain(@PathVariable String chainKey) throws IOException {
        return linkService.downloadByChain(chainKey);
    }

    @GetMapping("/download/{downloadKey}")
    public ResponseEntity<Resource> byKey(@PathVariable String downloadKey) throws IOException {
        return linkService.downloadByKey(downloadKey);
    }
}
