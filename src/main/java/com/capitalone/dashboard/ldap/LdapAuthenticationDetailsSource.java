package com.capitalone.dashboard.ldap;

import com.capitalone.dashboard.model.AuthType;
import org.springframework.security.authentication.AuthenticationDetailsSource;

import javax.servlet.http.HttpServletRequest;

public class LdapAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, AuthType> {

    @Override
    public AuthType buildDetails(HttpServletRequest context) {
        return AuthType.LDAP;
    }

}
