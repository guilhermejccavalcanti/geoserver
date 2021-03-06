/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.service.rest;

import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.rest.CatalogController;
import org.geoserver.config.GeoServer;
import org.geoserver.config.ServiceInfo;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.rest.converters.XStreamMessageConverter;
import org.geoserver.wms.WMSInfo;
import org.geoserver.wms.WMSXStreamLoader;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.logging.Logger;

/**
 * WMS Settings controller
 */
@RestController
@ControllerAdvice
@RequestMapping(path = "/restng/services/wms", produces = {
        MediaType.APPLICATION_JSON_VALUE,
        MediaType.APPLICATION_XML_VALUE,
        MediaType.TEXT_HTML_VALUE})
public class WMSSettingsController extends ServiceSettingsController {
    private static final Logger LOGGER = Logging.getLogger(WMSSettingsController.class);

    @Autowired
    public WMSSettingsController(GeoServer geoServer) { super(geoServer, WMSInfo.class); };

    @PutMapping( value = {"/settings", "/workspaces/{workspace}/settings"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, CatalogController.TEXT_JSON,
                    MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public void putServiceSettings(@RequestBody WMSInfo info,
                                   @PathVariable ( name = "workspace", required = false) String workspaceName) {
        super.putServiceSettings(info, workspaceName);
    }

    @Override
    public String getTemplateName(Object object) {
        return "wmsSettings";
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return WMSInfo.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public void configurePersister(XStreamPersister persister, XStreamMessageConverter converter) {
        persister.setHideFeatureTypeAttributes();
        persister.setCallback( new XStreamPersister.Callback() {
            @Override
            protected ServiceInfo getServiceObject() {
                Map<String, String> uriTemplateVars = (Map<String, String>) RequestContextHolder.getRequestAttributes().getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
                String workspace = uriTemplateVars.get("workspace");
                ServiceInfo service;
                if (workspace != null) {
                    WorkspaceInfo ws = geoServer.getCatalog().getWorkspaceByName(workspace);
                    service = geoServer.getService(ws, WMSInfo.class);
                } else {
                    service = geoServer.getService(WMSInfo.class);
                }
                return service;
            }
            @Override
            protected Class<WMSInfo> getObjectClass() {
                return WMSInfo.class;
            }
        });
        WMSXStreamLoader.initXStreamPersister(persister);
    }


}
