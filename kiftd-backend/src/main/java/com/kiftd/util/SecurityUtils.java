package com.kiftd.util;

import com.kiftd.security.AccountAuth;
import com.kiftd.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static UserPrincipal currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public static String currentUsername() {
        UserPrincipal p = currentUserOrNull();
        return p == null ? null : p.getUsername();
    }

    public static void requireAuth(AccountAuth auth) {
        UserPrincipal p = currentUserOrNull();
        if (p == null || !p.hasAuth(auth)) {
            throw new com.kiftd.common.BizException("权限不足: " + auth.name());
        }
    }
}
