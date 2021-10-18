package com.capitalone.dashboard.service;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.model.AuthType;
import com.capitalone.dashboard.model.UserEntitlements;
import com.capitalone.dashboard.repository.UserEntitlementsRepository;
import com.capitalone.dashboard.settings.AuthProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Properties;

@Component
public class LdapServiceImpl implements LdapService {

    private static final Logger LOGGER = Logger.getLogger(LdapServiceImpl.class);

    private final AuthProperties authProperties;
    private final ApiSettings apiSettings;
    private final UserEntitlementsRepository userEntitlementsRepository;

    private static final String ENTITLEMENT_TYPE = "distinguishedName";

    @Autowired
    public LdapServiceImpl(AuthProperties authProperties, ApiSettings apiSettings,
                           UserEntitlementsRepository userEntitlementsRepository) {
        this.authProperties = authProperties;
        this.apiSettings = apiSettings;
        this.userEntitlementsRepository = userEntitlementsRepository;
    }

    @Override
    public String getLdapDN(String userName) {
        String result = "";
        if(StringUtils.isEmpty(userName)) return result;

        UserEntitlements entitlements = userEntitlementsRepository.findTopByAuthTypeAndEntitlementTypeAndUsername(AuthType.LDAP, ENTITLEMENT_TYPE, userName);
        if(entitlements != null) return entitlements.getEntitlements();
        try {
            InitialDirContext context = createContext(setProperties());
            String entitlementValue = getLdapDNValue(userName, context);

            UserEntitlements newEntitlement = new UserEntitlements();
            newEntitlement.setUsername(userName);
            newEntitlement.setEntitlements(entitlementValue);
            newEntitlement.setEntitlementType(ENTITLEMENT_TYPE);
            newEntitlement.setAuthType(AuthType.LDAP);
            userEntitlementsRepository.save(newEntitlement);

            return entitlementValue;

        } catch (AuthenticationException ae) {
            LOGGER.error("LDAP bind credentials are incorrect", ae);
            return result;
        } catch (NamingException ne) {
            LOGGER.error("Failed to query ldap for " + userName, ne);
            return result;
        }
    }

    private String getLdapDNValue(String searchId, InitialDirContext context) throws NamingException {

        try {
            SearchControls ctrls = new SearchControls();
            ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            String searchBase = "";
            String searchFilter = "";
            searchBase = authProperties.getAdSvcRootDn();
            searchFilter = "(&(objectClass=user)(userPrincipalName=" + searchId + "@" + authProperties.getAdDomain() + "))";

            NamingEnumeration<SearchResult> results = context.search(searchBase, searchFilter, ctrls);
            // if searchId cannot be found in service accounts, then search in users
            results = (!results.hasMore()) ? context.search(authProperties.getAdUserRootDn(), searchFilter, ctrls) : results;

            if (!results.hasMore()) {
                return "";
            }
            SearchResult result = results.next();
            Attribute distNameAttr = result.getAttributes().get("distinguishedName");
            if (distNameAttr == null) return "";
            return StringUtils.replace(distNameAttr.toString(), "distinguishedName: ", "");
        } catch (Exception e) {
            LOGGER.error("error occurred searching user=" + searchId + ", error_message=" + e.getMessage());
        } finally {
            context.close();
        }
        return "";
    }


    private Properties setProperties() {
        Properties props = new Properties();
        try {
            props.put(Context.INITIAL_CONTEXT_FACTORY, apiSettings.getContextFactory());
            props.put("java.naming.security.protocol", apiSettings.getContextProtocol());
            props.put(Context.SECURITY_AUTHENTICATION, apiSettings.getContextSecurityAuthentication());
            props.put("com.sun.jndi.ldap.connect.timeout", apiSettings.getContextConnectTimeout());
            props.put(Context.PROVIDER_URL, authProperties.getAdUrl());
            props.put(Context.SECURITY_PRINCIPAL, authProperties.getLdapBindUser() + "@" + authProperties.getAdDomain());
            props.put(Context.SECURITY_CREDENTIALS, authProperties.getLdapBindPass());
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve properties for InitialDirContext", e);
        }
        return props;
    }

    private InitialDirContext createContext(Properties props) throws NamingException {
        return new InitialDirContext(props);
    }

}
