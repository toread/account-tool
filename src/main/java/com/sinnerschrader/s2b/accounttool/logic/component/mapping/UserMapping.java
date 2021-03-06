package com.sinnerschrader.s2b.accounttool.logic.component.mapping;

import com.sinnerschrader.s2b.accounttool.config.ldap.LdapConfiguration;
import com.sinnerschrader.s2b.accounttool.logic.entity.User;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.defaultString;


/**
 * Converter for Users from LDAP to Application
 */
@Service
public class UserMapping implements ModelMaping<User> {

    private static final Logger log = LoggerFactory.getLogger(UserMapping.class);

    @Autowired
    private LdapConfiguration ldapConfiguration;

    @Override
    public User map(SearchResultEntry entry) {
        if (entry == null)
            return null;

        Map.Entry<String, String> company = getCompany(entry.getDN(), entry.getAttributeValue("o"));
        final String dn = entry.getDN();
        final LocalDate birthDate = parseDate(dn, false, 1972,
            entry.getAttributeValueAsInteger("szzBirthMonth"),
            entry.getAttributeValueAsInteger("szzBirthDay"));
        final LocalDate entryDate = parseDate(dn, true,
            entry.getAttributeValueAsInteger("szzEntryYear"),
            entry.getAttributeValueAsInteger("szzEntryMonth"),
            entry.getAttributeValueAsInteger("szzEntryDay"));
        final LocalDate exitDate = parseDate(dn, true,
            entry.getAttributeValueAsInteger("szzExitYear"),
            entry.getAttributeValueAsInteger("szzExitMonth"),
            entry.getAttributeValueAsInteger("szzExitDay"));

        try {
            return new User(
                dn,
                entry.getAttributeValue("uid"),
                entry.getAttributeValueAsInteger("uidNumber"),
                entry.getAttributeValueAsInteger("gidNumber"),
                entry.getAttributeValue("displayName"),
                entry.getAttributeValue("gecos"),
                entry.getAttributeValue("cn"),
                entry.getAttributeValue("givenName"),
                entry.getAttributeValue("sn"),
                entry.getAttributeValue("homeDirectory"),
                entry.getAttributeValue("loginShell"),
                birthDate,
                entry.getAttributeValue("sambaSID"),
                entry.getAttributeValue("sambaPasswordHistory"),
                entry.getAttributeValue("sambaAcctFlags"),
                entry.getAttributeValue("mail"),
                User.State.Companion.fromString(entry.getAttributeValue("szzStatus")),
                User.State.Companion.fromString(entry.getAttributeValue("szzMailStatus")),
                defaultIfNull(entry.getAttributeValueAsLong("sambaPwdLastSet"), 0L),
                entryDate,
                exitDate,
                entry.getAttributeValue("ou"),
                entry.getAttributeValue("description"),
                defaultString(entry.getAttributeValue("telephoneNumber")),
                defaultString(entry.getAttributeValue("mobile")),
                entry.getAttributeValue("employeeNumber"),
                defaultString(entry.getAttributeValue("title")),
                entry.getAttributeValue("l"),
                entry.getAttributeValue("szzPublicKey"),
                company != null ? company.getValue() : "",
                company != null ? company.getKey() : "",
                entry.getAttributeValue("modifiersName"),
                entry.getAttributeValue("modifytimestamp")
            );
        } catch (Exception e){
            log.error("failed to map: " + entry.getDN(), e);
            return null;
        }
    }

    private Map.Entry<String, String> getCompany(String dn, String organization) {
        for (Map.Entry<String, String> entry : ldapConfiguration.getCompanies().entrySet()) {
            if (StringUtils.equals(entry.getValue(), organization)) {
                return entry;
            }
        }
        return extractCompanyFromDn(dn);
    }

    private LocalDate parseDate(String dn, boolean required, Integer year, Integer month, Integer day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            if (required) {
                log.warn("Could not parse date [dn={},required={},year={},month={},day={}]", dn, required, year, month, day);
            }
        }
        return null;
    }

    @Deprecated
    private Map.Entry<String, String> extractCompanyFromDn(String dn) {
        String[] parts = StringUtils.split(StringUtils.trimToEmpty(dn), ',');
        String key = "";
        if (parts.length == 5) {
            key = parts[2];
        }
        final String companyPrefix = "ou=";
        if (StringUtils.startsWith(key, companyPrefix)) {
            key = StringUtils.replaceOnce(key, companyPrefix, "");
        }
        if (ldapConfiguration.getCompanies().containsKey(key)) {
            final String comKey = key;
            final String comVal = ldapConfiguration.getCompanies().get(key);
            return new Map.Entry<String, String>() {

                @Override
                public String getKey() {
                    return comKey;
                }

                @Override
                public String getValue() {
                    return comVal;
                }

                @Override
                public String setValue(String value) {
                    throw new IllegalAccessError("You cant set a new value on immutable Object.");
                }
            };
        }
        return null;
    }

    @Override
    public boolean isCompatible(SearchResultEntry entry) {
        return (entry != null) && CollectionUtils
            .containsAny(Arrays.asList(entry.getObjectClassValues()), User.Companion.getObjectClasses());
    }

}
