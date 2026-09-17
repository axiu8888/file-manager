package com.kiftd.security;

import com.kiftd.repository.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public CustomUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return accountRepository.findByAccountName(username)
                .map(a -> new UserPrincipal(a.getAccountName(), a.getAccountPwd(), a.getAccountAuth()))
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }
}
