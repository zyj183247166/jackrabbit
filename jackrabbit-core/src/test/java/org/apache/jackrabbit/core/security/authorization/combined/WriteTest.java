/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.security.authorization.combined;

import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.core.security.authorization.JackrabbitAccessControlList;
import org.apache.jackrabbit.core.security.authorization.PrivilegeRegistry;
import org.apache.jackrabbit.test.NotExecutableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.PropertyType;
import javax.jcr.Session;
import java.security.Principal;
import java.util.Map;
import java.util.HashMap;

/**
 * <code>EvaluationTest</code>...
 */
public class WriteTest extends org.apache.jackrabbit.core.security.authorization.acl.WriteTest {

    private static Logger log = LoggerFactory.getLogger(WriteTest.class);

    protected void setUp() throws Exception {
        super.setUp();

        // simple test to check if proper provider is present:
        try {
            getPrincipalBasedPolicy(acMgr, path, getTestUser().getPrincipal());
        } catch (Exception e) {
            superuser.logout();
            throw e;
        }
    }

    private JackrabbitAccessControlList getPrincipalBasedPolicy(AccessControlManager acM, String path, Principal principal) throws RepositoryException, AccessDeniedException, NotExecutableException {
        if (acM instanceof JackrabbitAccessControlManager) {
            AccessControlPolicy[] tmpls = ((JackrabbitAccessControlManager) acM).getApplicablePolicies(principal);
            for (int i = 0; i < tmpls.length; i++) {
                if (tmpls[i] instanceof JackrabbitAccessControlList) {
                    JackrabbitAccessControlList acl = (JackrabbitAccessControlList) tmpls[i];
                    return acl;
                }
            }
        }
        throw new NotExecutableException();
    }

    private JackrabbitAccessControlList givePrivileges(String nPath,
                                                       Principal principal,
                                                       Privilege[] privileges,
                                                       Map restrictions,
                                                       boolean nodeBased) throws NotExecutableException, RepositoryException {
        if (nodeBased) {
            return givePrivileges(nPath, principal, privileges, getRestrictions(superuser, nPath));
        } else {
            JackrabbitAccessControlList tmpl = getPrincipalBasedPolicy(acMgr, nPath, principal);
            tmpl.addEntry(principal, privileges, true, restrictions);
            acMgr.setPolicy(tmpl.getPath(), tmpl);
            superuser.save();
            return tmpl;
        }
    }

    private JackrabbitAccessControlList withdrawPrivileges(String nPath,
                                                       Principal principal,
                                                       Privilege[] privileges,
                                                       Map restrictions,
                                                       boolean nodeBased) throws NotExecutableException, RepositoryException {
        if (nodeBased) {
            return withdrawPrivileges(nPath, principal, privileges, getRestrictions(superuser, nPath));
        } else {
            JackrabbitAccessControlList tmpl = getPrincipalBasedPolicy(acMgr, nPath, principal);
            tmpl.addEntry(principal, privileges, false, restrictions);
            acMgr.setPolicy(tmpl.getPath(), tmpl);
            superuser.save();
            return tmpl;
        }
    }

    private Map getPrincipalBasedRestrictions(String path) throws RepositoryException, NotExecutableException {
        if (superuser instanceof SessionImpl) {
            Map restr = new HashMap();
            restr.put("rep:nodePath", superuser.getValueFactory().createValue(path, PropertyType.PATH));
            return restr;
        } else {
            throw new NotExecutableException();
        }
    }

    public void testCombinedPolicies() throws RepositoryException, NotExecutableException {
        Group testGroup = getTestGroup();
        Session testSession = getTestSession();
        AccessControlManager testAcMgr = getTestACManager();

        /*
          precondition:
          testuser must have READ-only permission on test-node and below
        */
        checkReadOnly(path);

        Privilege[] readPrivs = privilegesFromName(Privilege.JCR_READ);
        // nodebased: remove READ privilege for 'testUser' at 'path'
        withdrawPrivileges(path, readPrivs, getRestrictions(superuser, path));
        // principalbased: add READ privilege for 'testGroup'
        givePrivileges(path, testGroup.getPrincipal(), readPrivs, getPrincipalBasedRestrictions(path), false);
        /*
         expected result:
         - nodebased wins over principalbased -> READ is denied
         */
        assertFalse(testSession.itemExists(path));
        assertFalse(testSession.hasPermission(path, javax.jcr.Session.ACTION_READ));
        assertFalse(testAcMgr.hasPrivileges(path, readPrivs));

        // remove the nodebased policy
        JackrabbitAccessControlList policy = getPolicy(acMgr, path, getTestUser().getPrincipal());
        acMgr.removePolicy(policy.getPath(), policy);
        superuser.save();

        /*
         expected result:
         - READ privilege is present again.
         */
        assertTrue(testSession.itemExists(path));
        assertTrue(testSession.hasPermission(path, javax.jcr.Session.ACTION_READ));
        assertTrue(testAcMgr.hasPrivileges(path, readPrivs));

        // nodebased: add WRITE privilege for 'testUser' at 'path'
        Privilege[] wrtPrivileges = privilegesFromName(PrivilegeRegistry.REP_WRITE);
        givePrivileges(path, wrtPrivileges, getRestrictions(superuser, path));
        // userbased: deny MODIFY_PROPERTIES privileges for 'testUser'
        Privilege[] modPropPrivs = privilegesFromName(Privilege.JCR_MODIFY_PROPERTIES);
        withdrawPrivileges(path, getTestUser().getPrincipal(), modPropPrivs, getPrincipalBasedRestrictions(path), false);
        /*
         expected result:
         - MODIFY_PROPERTIES privilege still present
         */
        assertTrue(testSession.hasPermission(path+"/anyproperty", javax.jcr.Session.ACTION_SET_PROPERTY));
        assertTrue(testAcMgr.hasPrivileges(path, wrtPrivileges));

        // nodebased: deny MODIFY_PROPERTIES privileges for 'testUser'
        //            on a child node.
        withdrawPrivileges(childNPath, getTestUser().getPrincipal(), modPropPrivs, getRestrictions(superuser, childNPath));
        /*
         expected result:
         - MODIFY_PROPERTIES privilege still present at 'path'
         - no-MODIFY_PROPERTIES privilege at 'childNPath'
         */
        assertTrue(testSession.hasPermission(path+"/anyproperty", javax.jcr.Session.ACTION_SET_PROPERTY));
        assertTrue(testAcMgr.hasPrivileges(path, modPropPrivs));

        assertFalse(testSession.hasPermission(childNPath+"/anyproperty", javax.jcr.Session.ACTION_SET_PROPERTY));
        assertFalse(testAcMgr.hasPrivileges(childNPath, modPropPrivs));
    }
}