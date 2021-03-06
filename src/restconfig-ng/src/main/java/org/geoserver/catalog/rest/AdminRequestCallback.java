/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.rest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geoserver.rest.DispatcherCallback;
import org.geoserver.rest.DispatcherCallbackAdapter;
import org.geoserver.security.AdminRequest;
import org.springframework.stereotype.Component;

/**
 * Rest callback that sets the {@link AdminHttpServletRequest} thread local.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
@Component
public class AdminRequestCallback extends DispatcherCallbackAdapter {

    @Override
    public void dispatched(HttpServletRequest HttpServletRequest,
            HttpServletResponse HttpServletResponse, Object handler) {
        if (DispatcherCallback.getControllerBean(handler) instanceof CatalogController)
            AdminRequest.start(this);
    }

    @Override
    public void finished(HttpServletRequest HttpServletRequest,
            HttpServletResponse HttpServletResponse) {
        AdminRequest.finish();
    }

}
