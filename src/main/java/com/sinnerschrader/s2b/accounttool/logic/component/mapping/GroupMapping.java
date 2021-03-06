package com.sinnerschrader.s2b.accounttool.logic.component.mapping;

import com.sinnerschrader.s2b.accounttool.config.ldap.LdapConfiguration;
import com.sinnerschrader.s2b.accounttool.logic.entity.Group;
import com.sinnerschrader.s2b.accounttool.logic.entity.GroupOfNames;
import com.sinnerschrader.s2b.accounttool.logic.entity.PosixGroup;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.defaultString;


/**
 * Converter for Groups from LDAP to Application
 */
@Service
public class GroupMapping implements ModelMaping<Group> {

    private static final Logger LOG = LoggerFactory.getLogger(GroupMapping.class);

    @Autowired
    private LdapConfiguration ldapConfiguration;

    @Override
    public Group map(SearchResultEntry entry) {
        if (entry == null)
            return null;

        try {
            final List<String> objectClasses = asList(entry.getObjectClassValues());
            final String cn = entry.getAttributeValue("cn");
            if (isPosixGroup(objectClasses)) {
                return new PosixGroup(
                    entry.getDN(),
                    cn,
                    entry.getAttributeValueAsInteger("gid"),
                    defaultString(entry.getAttributeValue("description")),
                    getGroupClassification(cn),
                    asList(defaultIfNull(entry.getAttributeValues("memberUid"), EMPTY_STRING_ARRAY))
                );
            }
            final boolean unique = isGroupOfUniqueNames(objectClasses);
            if (unique || isGroupOfNames(objectClasses)) {
                final String memberAttribute = unique
                    ? Group.GroupType.GroupOfUniqueNames.getMemberAttritube()
                    : Group.GroupType.GroupOfNames.getMemberAttritube();

                return new GroupOfNames(
                    entry.getDN(),
                    cn,
                    defaultString(entry.getAttributeValue("description")),
                    unique,
                    getGroupClassification(cn),
                    asList(defaultIfNull(entry.getAttributeValues(memberAttribute), EMPTY_STRING_ARRAY))
                );
            }
        } catch(Exception e){
            LOG.error("failed to map: " + entry.getDN(), e);
            return null;
        }
        throw new IllegalArgumentException("Provided result entry is not supported. Please call isCompatible before.");
    }

    private boolean isAdminGroup(String cn) {
        return StringUtils.containsAny(cn, "admins", "administrators") ||
            ldapConfiguration.getPermissions().getLdapAdminGroup().equals(cn) ||
            StringUtils.startsWith(cn, ldapConfiguration.getGroupPrefixes().getAdmin());
    }

    private boolean isTechnicalGroup(String cn) {
        return StringUtils.startsWith(cn, ldapConfiguration.getGroupPrefixes().getTechnical());
    }

    private boolean isTeamGroup(String cn) {
        return StringUtils.startsWith(cn, ldapConfiguration.getGroupPrefixes().getTeam());
    }

    private Group.GroupClassification getGroupClassification(String cn) {
        if (isAdminGroup(cn)) {
            return Group.GroupClassification.ADMIN;
        }
        if (isTechnicalGroup(cn)) {
            return Group.GroupClassification.TECHNICAL;
        }
        if (isTeamGroup(cn)) {
            return Group.GroupClassification.TEAM;
        }
        return Group.GroupClassification.UNKNOWN;
    }

    private boolean isGroupOfNames(Collection<String> objectClasses) {
        return objectClasses.contains(Group.GroupType.GroupOfNames.getObjectClass());
    }

    private boolean isGroupOfUniqueNames(Collection<String> objectClasses) {
        return objectClasses.contains(Group.GroupType.GroupOfUniqueNames.getObjectClass());
    }

    private boolean isPosixGroup(Collection<String> objectClasses) {
        return objectClasses.contains(Group.GroupType.Posix.getObjectClass());
    }

    @Override
    public boolean isCompatible(SearchResultEntry entry) {
        if (entry == null) {
            return false;
        }
        final List<String> objectClasses = asList(entry.getObjectClassValues());
        return isPosixGroup(objectClasses) ||
            isGroupOfNames(objectClasses) ||
            isGroupOfUniqueNames(objectClasses);
    }

}
