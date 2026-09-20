package com.kiftd.service;

import com.kiftd.common.BizException;
import com.kiftd.config.KiftdProperties;
import com.kiftd.dto.AuthDtos;
import com.kiftd.entity.Account;
import com.kiftd.repository.AccountRepository;
import com.kiftd.security.AccountAuth;
import com.kiftd.security.JwtService;
import com.kiftd.util.CaptchaService;
import com.kiftd.util.IdUtil;
import com.kiftd.util.RsaKeyUtil;
import com.kiftd.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

@Service
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RsaKeyUtil rsaKeyUtil;
    private final CaptchaService captchaService;
    private final KiftdProperties props;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                          RsaKeyUtil rsaKeyUtil, CaptchaService captchaService, KiftdProperties props) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rsaKeyUtil = rsaKeyUtil;
        this.captchaService = captchaService;
        this.props = props;
    }

    public AuthDtos.PublicKeyDto publicKey() {
        return new AuthDtos.PublicKeyDto(rsaKeyUtil.getPublicKeyBase64(), System.currentTimeMillis());
    }

    public AuthDtos.CaptchaDto captcha() {
        CaptchaService.CaptchaResult r = captchaService.create();
        return new AuthDtos.CaptchaDto(r.captchaId(), Base64.getEncoder().encodeToString(r.imagePng()));
    }

    public AuthDtos.SignupEnabledDto signupEnabled() {
        return new AuthDtos.SignupEnabledDto(props.signup().enabled());
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        if (req.captchaId() != null && !req.captchaId().isBlank()) {
            if (!captchaService.verify(req.captchaId(), req.captcha())) {
                throw new BizException("验证码错误");
            }
        }
        String pwd;
        try {
            pwd = rsaKeyUtil.decryptBase64(req.encryptedPwd());
        } catch (Exception e) {
            throw new BizException("密码解密失败，请刷新页面后重试");
        }
        Account account = accountRepository.findByAccountName(req.account())
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (!passwordEncoder.matches(pwd, account.getAccountPwd())) {
            throw new BizException("账号或密码错误");
        }
        String token = jwtService.generate(account.getAccountName(), account.getAccountAuth());
        return new AuthDtos.TokenResponse(token, account.getAccountName(), account.getAccountAuth());
    }

    @Transactional
    public AuthDtos.TokenResponse signup(AuthDtos.SignUpRequest req) {
        if (!props.signup().enabled()) {
            throw new BizException("当前未开放注册");
        }
        if (!captchaService.verify(req.captchaId(), req.captcha())) {
            throw new BizException("验证码错误");
        }
        if (accountRepository.existsByAccountName(req.account())) {
            throw new BizException("账号已存在");
        }
        String pwd;
        try {
            pwd = rsaKeyUtil.decryptBase64(req.encryptedPwd());
        } catch (Exception e) {
            throw new BizException("密码解密失败，请刷新页面后重试");
        }
        if (pwd.length() < 3 || pwd.length() > 32) {
            throw new BizException("密码长度需为 3-32");
        }
        Account account = new Account();
        account.setAccountId(IdUtil.uuid());
        account.setAccountName(req.account());
        account.setAccountPwd(passwordEncoder.encode(pwd));
        account.setAccountAuth(AccountAuth.ALL);
        accountRepository.save(account);
        String token = jwtService.generate(account.getAccountName(), account.getAccountAuth());
        return new AuthDtos.TokenResponse(token, account.getAccountName(), account.getAccountAuth());
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest req) {
        String username = SecurityUtils.currentUsername();
        if (username == null) {
            throw new BizException("请先登录");
        }
        if (req.captchaId() != null && !req.captchaId().isBlank()
                && !captchaService.verify(req.captchaId(), req.captcha())) {
            throw new BizException("验证码错误");
        }
        Account account = accountRepository.findByAccountName(username)
                .orElseThrow(() -> new BizException("用户不存在"));
        String oldPwd = rsaKeyUtil.decryptBase64(req.encryptedOldPwd());
        String newPwd = rsaKeyUtil.decryptBase64(req.encryptedNewPwd());
        if (!passwordEncoder.matches(oldPwd, account.getAccountPwd())) {
            throw new BizException("原密码错误");
        }
        if (newPwd.length() < 3 || newPwd.length() > 32) {
            throw new BizException("新密码长度需为 3-32");
        }
        account.setAccountPwd(passwordEncoder.encode(newPwd));
        accountRepository.save(account);
    }
}
