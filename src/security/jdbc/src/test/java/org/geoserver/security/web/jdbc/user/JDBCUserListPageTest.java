/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.web.jdbc.user;

import org.geoserver.security.jdbc.H2RoleServiceTest;
import org.geoserver.security.jdbc.H2UserGroupServiceTest;
import org.geoserver.security.web.user.UserListPageTest;
import org.junit.Test;

public class JDBCUserListPageTest extends UserListPageTest {
    @Override
    protected void doInitialize() throws Exception {
        initializeForJDBC();
    }
    
    //@Test
    // TODO, mcr, does not work for wicket 1.5
    public void testRemoveWithRoles() throws Exception {
        withRoles=true;
        addAdditonalData();
        doRemove(getTabbedPanelPath()+":panel:header:removeSelectedWithRoles");
    }
    
    //@Test
    // TODO, mcr, does not work for wicket 1.5
    public void testRemoveJDBC() throws Exception {
        addAdditonalData();
        doRemove(getTabbedPanelPath()+":panel:header:removeSelected");
    }

    void initializeForJDBC() throws Exception {
        initialize(new H2UserGroupServiceTest(), new H2RoleServiceTest());
    }
    
    @Override
    public String getRoleServiceName() {
        return "h2";
    }

    @Override
    public String getUserGroupServiceName() {
        return "h2";
    }

}
