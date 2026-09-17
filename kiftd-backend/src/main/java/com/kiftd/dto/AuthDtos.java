package com.kiftd.dto;

public final class AuthDtos {
    private AuthDtos() {}

    public record PublicKeyDto(String publicKey, long time) {}
    public record LoginRequest(String account, String encryptedPwd, String captchaId, String captcha) {}
    public record SignUpRequest(String account, String encryptedPwd, String captchaId, String captcha) {}
    public record ChangePasswordRequest(String encryptedOldPwd, String encryptedNewPwd, String captchaId, String captcha) {}
    public record TokenResponse(String token, String account, String auth) {}
    public record SignupEnabledDto(boolean enabled) {}
    public record CaptchaDto(String captchaId, String imageBase64) {}
}
