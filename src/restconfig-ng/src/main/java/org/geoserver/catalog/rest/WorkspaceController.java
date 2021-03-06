package org.geoserver.catalog.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import freemarker.template.ObjectWrapper;
import org.geoserver.catalog.CascadeDeleteVisitor;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.rest.ObjectToMapWrapper;
import org.geoserver.rest.ResourceNotFoundException;
import org.geoserver.rest.RestException;
import org.geoserver.rest.converters.FreemarkerHTMLMessageConverter;
import org.geoserver.rest.converters.XStreamMessageConverter;
import org.geoserver.rest.wrapper.RestWrapper;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import freemarker.template.SimpleHash;
import freemarker.template.Template;
import freemarker.template.TemplateModelException;

@RestController
@RequestMapping(path = "/restng", produces = { MediaType.APPLICATION_JSON_VALUE,
        MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_HTML_VALUE })
public class WorkspaceController extends CatalogController {

    private static final Logger LOGGER = Logging.getLogger(WorkspaceController.class);

    @Autowired
    public WorkspaceController(@Qualifier("catalog") Catalog catalog) {
        super(catalog);

    }

    @GetMapping(value = "/workspaces", produces = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_HTML_VALUE })
    public RestWrapper getWorkspaces() {

        List<WorkspaceInfo> wkspaces = catalog.getWorkspaces();
        return wrapList(wkspaces, WorkspaceInfo.class);
    }

    @GetMapping(value = "/workspaces/{workspaceName}", produces = {
            MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE,
            MediaType.APPLICATION_XML_VALUE })
    public RestWrapper<WorkspaceInfo> getWorkspace(@PathVariable String workspaceName) {

        WorkspaceInfo wkspace = catalog.getWorkspaceByName(workspaceName);
        if (wkspace == null) {
            throw new ResourceNotFoundException("No such workspace: '" + workspaceName + "' found");
        }

        LOGGER.info("GET " + workspaceName);
        LOGGER.info("got " + wkspace.getName());

        return wrapObject(wkspace, WorkspaceInfo.class);
    }

    @PostMapping(value = "/workspaces", produces = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE, MediaType.APPLICATION_XML_VALUE }, consumes = { "text/xml",
                    MediaType.APPLICATION_XML_VALUE, TEXT_JSON, MediaType.APPLICATION_JSON_VALUE })
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<String> postWorkspace(@RequestBody WorkspaceInfo workspace,
            @RequestParam(defaultValue = "false", name = "default") boolean makeDefault,
            UriComponentsBuilder builder) {
        
        if(catalog.getWorkspaceByName(workspace.getName())!=null) {
            throw new RestException("Workspace '"+workspace.getName()+"' already exists", HttpStatus.UNAUTHORIZED);
        }
        catalog.add(workspace);
        String name = workspace.getName();
        LOGGER.info("Added workspace " + name);
        if (makeDefault) {
            catalog.setDefaultWorkspace(workspace);
            LOGGER.info("made workspace " + name + " default");
        }
        LOGGER.info("POST Style " + name);

        // build the new path
        UriComponents uriComponents = getUriComponents(name, builder);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(uriComponents.toUri());
        return new ResponseEntity<String>(name, headers, HttpStatus.CREATED);
    }

    @PutMapping(value = "/workspaces/{workspaceName}", produces = {
            MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE,
            MediaType.APPLICATION_XML_VALUE }, consumes = { "text/xml",
                    MediaType.APPLICATION_XML_VALUE, TEXT_JSON, MediaType.APPLICATION_JSON_VALUE })

    public void putWorkspace(@RequestBody WorkspaceInfo workspace,
            @PathVariable String workspaceName, UriComponentsBuilder builder) {
        if ( "default".equals( workspaceName ) ) {
            catalog.setDefaultWorkspace( workspace );
        }
        // name must exist
        WorkspaceInfo wks = catalog.getWorkspaceByName(workspaceName);
        if (wks == null) {
            throw new RestException("Can't change a non existant workspace (" + workspaceName + ")",
                    HttpStatus.NOT_FOUND);
        }

        String infoName = workspace.getName();
        if (infoName != null && !workspaceName.equals(infoName)) {
            throw new RestException("Can't change name of workspace", HttpStatus.FORBIDDEN);
        }

        new CatalogBuilder(catalog).updateWorkspace(wks, workspace);
        catalog.save(wks);
    }

    @DeleteMapping(path = "/workspaces/{workspaceName}")
    protected void deleteWorkspace(@PathVariable String workspaceName,
            @RequestParam(defaultValue = "false", name = "recurse") boolean recurse) {

        WorkspaceInfo ws = catalog.getWorkspaceByName(workspaceName);
        if(workspaceName.equals("default")) {
            throw new RestException("Can't delete the default workspace", HttpStatus.METHOD_NOT_ALLOWED);
        }
        if(ws == null) {
            throw new RestException("Workspace '"+workspaceName+"' not found", HttpStatus.NOT_FOUND);
        }
        if (!recurse) {
            if (!catalog.getStoresByWorkspace(ws, StoreInfo.class).isEmpty()) {
                throw new RestException("Workspace not empty", HttpStatus.FORBIDDEN);
            }

            // check for "linked" workspace
            NamespaceInfo ns = catalog.getNamespaceByPrefix(ws.getName());
            if (ns != null) {
                if (!catalog.getFeatureTypesByNamespace(ns).isEmpty()) {
                    throw new RestException("Namespace for workspace not empty.",
                            HttpStatus.FORBIDDEN);
                }
                catalog.remove(ns);
            }

            catalog.remove(ws);
        } else {
            // recursive delete
            new CascadeDeleteVisitor(catalog).visit(ws);
        }

        LOGGER.info("DELETE workspace " + ws);
    }

    private UriComponents getUriComponents(String name, UriComponentsBuilder builder) {
        UriComponents uriComponents;

        uriComponents = builder.path("/workspaces/{id}").buildAndExpand(name);

        return uriComponents;
    }

    @Override
    protected <T> ObjectWrapper createObjectWrapper(Class<T> clazz) {
        return new ObjectToMapWrapper<WorkspaceInfo>(WorkspaceInfo.class) {
            @Override
            protected void wrapInternal(Map<String, Object> properties, SimpleHash model, WorkspaceInfo wkspace) {
                if (properties == null) {
                    try {
                        properties = model.toMap();
                    } catch (TemplateModelException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                List<Map<String, Map<String, String>>> dsProps = new ArrayList<>();

                List<DataStoreInfo> datasources = catalog.getDataStoresByWorkspace(wkspace);
                for (DataStoreInfo ds : datasources) {
                    Map<String, String> names = new HashMap<>();
                    names.put("name", ds.getName());
                    dsProps.add(Collections.singletonMap("properties", names));
                }
                if (!dsProps.isEmpty())
                    properties.putIfAbsent("dataStores", dsProps);

                dsProps = new ArrayList<>();

                List<CoverageStoreInfo> coverages = catalog.getCoverageStoresByWorkspace(wkspace);
                for (CoverageStoreInfo ds : coverages) {
                    Map<String, String> names = new HashMap<>();
                    names.put("name", ds.getName());
                    dsProps.add(Collections.singletonMap("properties", names));
                }
                if (!dsProps.isEmpty())
                    properties.putIfAbsent("coverageStores", dsProps);

                dsProps = new ArrayList<>();

                List<WMSStoreInfo> wmssources = catalog.getStoresByWorkspace(wkspace,
                        WMSStoreInfo.class);
                for (WMSStoreInfo ds : wmssources) {
                    Map<String, String> names = new HashMap<>();
                    names.put("name", ds.getName());
                    dsProps.add(Collections.singletonMap("properties", names));
                }
                if (!dsProps.isEmpty())
                    properties.putIfAbsent("wmsStores", dsProps);
                WorkspaceInfo def = catalog.getDefaultWorkspace();
                if (def.equals(wkspace)) {
                    properties.put("isDefault", Boolean.TRUE);
                } else {
                    properties.put("isDefault", Boolean.FALSE);
                }
            }

            @Override
            protected void wrapInternal(SimpleHash model, @SuppressWarnings("rawtypes") Collection object) {
                for (Object w : object) {
                    WorkspaceInfo wk = (WorkspaceInfo) w;
                    wrapInternal(null, model, wk);
                }

            }
        };
    }

    @Override
    public void configurePersister(XStreamPersister persister, XStreamMessageConverter converter) {
        persister.setCallback(new XStreamPersister.Callback() {
            @Override
            protected Class<WorkspaceInfo> getObjectClass() {
                return WorkspaceInfo.class;
            }

            @Override
            protected CatalogInfo getCatalogObject() {
                Map<String, String> uriTemplateVars = (Map<String, String>) RequestContextHolder
                        .getRequestAttributes()
                        .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                                RequestAttributes.SCOPE_REQUEST);
                String workspace = uriTemplateVars.get("workspaceName");

                if (workspace == null) {
                    return null;
                }
                return catalog.getWorkspaceByName(workspace);
            }

            @Override
            protected void postEncodeWorkspace(WorkspaceInfo cs, HierarchicalStreamWriter writer,
                    MarshallingContext context) {

                // add a link to the datastores
                writer.startNode("dataStores");
                converter.encodeCollectionLink("datastores", writer);
                writer.endNode();

                writer.startNode("coverageStores");
                converter.encodeCollectionLink("coveragestores", writer);
                writer.endNode();

                writer.startNode("wmsStores");
                converter.encodeCollectionLink("wmsstores", writer);
                writer.endNode();
            }

            @Override
            protected void postEncodeReference(Object obj, String ref, String prefix,
                    HierarchicalStreamWriter writer, MarshallingContext context) {
                if (obj instanceof WorkspaceInfo) {
                    converter.encodeLink("/workspaces/" + converter.encode(ref), writer);
                }
            }
        });
    }

    @Override
    protected String getTemplateName(Object object) {
        if (object instanceof WorkspaceInfo) {
            return "WorkspaceInfo";
        }
        return null;
    }

}
