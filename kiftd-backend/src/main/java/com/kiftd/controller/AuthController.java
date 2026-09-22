package com.kiftd.controller;

import com.kiftd.common.ApiResponse;
import com.kiftd.dto.AuthDtos;
import com.kiftd.service.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${kiftd.api-prefix:/api}/auth")
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/public-key")
    public ApiResponse<AuthDtos.PublicKeyDto> publicKey() {
        return ApiResponse.ok(accountService.publicKey());
    }

    @GetMapping("/captcha")
    public ApiResponse<AuthDtos.CaptchaDto> captcha() {
        return ApiResponse.ok(accountService.captcha());
    }

    @GetMapping("/signup-enabled")
    public ApiResponse<AuthDtos.SignupEnabledDto> signupEnabled() {
        return ApiResponse.ok(accountService.signupEnabled());
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.TokenResponse> login(@RequestBody AuthDtos.LoginRequest req) {
        return ApiResponse.ok(accountService.login(req));
    }

    @PostMapping("/signup")
    public ApiResponse<AuthDtos.TokenResponse> signup(@RequestBody AuthDtos.SignUpRequest req) {
        return ApiResponse.ok(accountService.signup(req));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody AuthDtos.ChangePasswordRequest req) {
        accountService.changePassword(req);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.MeResponse> me() {
        return ApiResponse.ok(accountService.currentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }
}
