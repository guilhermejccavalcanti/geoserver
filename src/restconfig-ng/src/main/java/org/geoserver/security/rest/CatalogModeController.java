/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.rest;

import java.util.Map;

import org.geoserver.catalog.rest.CatalogController;
import org.geoserver.catalog.rest.NamedMap;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.rest.ResourceNotFoundException;
import org.geoserver.rest.RestBaseController;
import org.geoserver.rest.RestException;
import org.geoserver.security.CatalogMode;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.impl.DataAccessRuleDAO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allows retrieving and modifying the catalog mode
 */
@RestController
@RequestMapping(path = RestBaseController.ROOT_PATH + "/security/acl/catalog")
public class CatalogModeController {

    static final String MODE_ELEMENT = "mode";

    static final String XML_ROOT_ELEM = "catalog";

    DataAccessRuleDAO ruleDAO;

    public CatalogModeController() {
        ruleDAO = DataAccessRuleDAO.get();
    }

    GeoServerSecurityManager getManager() {
        return GeoServerExtensions.bean(GeoServerSecurityManager.class);
    }

    protected void checkUserIsAdmin() {
        if (getManager().checkAuthenticationForAdminRole() == false) {
            throw new RestException("Amdinistrative priveleges required", HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping(produces = { MediaType.APPLICATION_JSON_VALUE, CatalogController.TEXT_JSON,
            MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE })
    @ResponseBody
    public NamedMap getMap() throws Exception {
        checkUserIsAdmin();

        CatalogMode mode = ((DataAccessRuleDAO) ruleDAO).getMode();
        NamedMap modeMap = new NamedMap(XML_ROOT_ELEM);
        modeMap.put(MODE_ELEMENT, mode.toString());
        return modeMap;
    }

    @PutMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, CatalogController.TEXT_JSON,
            MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE })
    protected void putMap(@RequestBody Map map) throws Exception {
        checkUserIsAdmin();

        String mode = (String) map.get(MODE_ELEMENT);

        if (mode == null)
            throw new ResourceNotFoundException("Element " + MODE_ELEMENT + " is missing");

        CatalogMode modeValue = null;
        for (CatalogMode m : CatalogMode.values()) {
            if (m.toString().equals(mode)) {
                modeValue = m;
                break;
            }
        }

        if (modeValue == null)
            throw new RestException("Not a valid mode: " + mode, HttpStatus.UNPROCESSABLE_ENTITY);

        ((DataAccessRuleDAO) ruleDAO).setCatalogMode(modeValue);
        ruleDAO.storeRules();

    }

}
