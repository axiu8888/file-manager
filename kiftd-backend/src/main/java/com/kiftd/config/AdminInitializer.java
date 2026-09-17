package com.kiftd.config;

import com.kiftd.entity.Account;
import com.kiftd.repository.AccountRepository;
import com.kiftd.security.AccountAuth;
import com.kiftd.util.IdUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final KiftdProperties props;

    public AdminInitializer(AccountRepository accountRepository, PasswordEncoder passwordEncoder, KiftdProperties props) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!accountRepository.existsByAccountName(props.admin().username())) {
            Account admin = new Account();
            admin.setAccountId(IdUtil.uuid());
            admin.setAccountName(props.admin().username());
            admin.setAccountPwd(passwordEncoder.encode(props.admin().password()));
            admin.setAccountAuth(AccountAuth.ALL);
            accountRepository.save(admin);
        }
    }
}
