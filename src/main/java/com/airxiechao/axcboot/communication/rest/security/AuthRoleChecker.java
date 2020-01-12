package com.airxiechao.axcboot.communication.rest.security;

public interface AuthRoleChecker {
    boolean hasRole(AuthPrincipal principal, String[] roles);
}
