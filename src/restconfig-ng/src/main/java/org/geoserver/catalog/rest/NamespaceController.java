package org.geoserver.catalog.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import freemarker.template.ObjectWrapper;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
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
public class NamespaceController extends CatalogController {
    private static final String NAMESPACE_NAME = "namespaceName";

    private static final Logger LOGGER = Logging.getLogger(NamespaceController.class);

    @Autowired
    public NamespaceController(@Qualifier("catalog") Catalog catalog) {
        super(catalog);
    }

    @GetMapping(value = "/namespaces/{namespaceName}", produces = {
            MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE,
            MediaType.APPLICATION_XML_VALUE })
    public RestWrapper<NamespaceInfo> getNamespace(@PathVariable String namespaceName) {

        NamespaceInfo namespace = catalog.getNamespaceByPrefix(namespaceName);
        if (namespace == null) {
            throw new ResourceNotFoundException("No such namespace: '" + namespaceName + "' found");
        }

        LOGGER.info("GET " + namespaceName);
        LOGGER.info("got " + namespace.getName());

        return wrapObject(namespace, NamespaceInfo.class);
    }

    @GetMapping(value = "/namespaces", produces = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_HTML_VALUE })
    public RestWrapper getNamespaces() {

        List<NamespaceInfo> wkspaces = catalog.getNamespaces();
        return wrapList(wkspaces, NamespaceInfo.class);
    }

    @PostMapping(value = "/namespaces", produces = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE, MediaType.APPLICATION_XML_VALUE }, consumes = { "text/xml",
                    MediaType.APPLICATION_XML_VALUE, TEXT_JSON, MediaType.APPLICATION_JSON_VALUE })
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<String> postNamespace(@RequestBody NamespaceInfo namepace,
            UriComponentsBuilder builder) {
        catalog.add(namepace);
        String name = namepace.getName();
        LOGGER.info("Added namespace " + name);
        // build the new path
        UriComponents uriComponents = getUriComponents(name, builder);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(uriComponents.toUri());
        return new ResponseEntity<String>(name, headers, HttpStatus.CREATED);
    }

    @PutMapping(value = "/namespaces/{prefix}", produces = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE, MediaType.APPLICATION_XML_VALUE }, consumes = { "text/xml",
                    MediaType.APPLICATION_XML_VALUE, TEXT_JSON, MediaType.APPLICATION_JSON_VALUE })
    public void putNamespace(@RequestBody NamespaceInfo namespace, @PathVariable String prefix,
            UriComponentsBuilder builder) {
        if ("default".equals(prefix)) {
            catalog.setDefaultNamespace(namespace);
        }
        // name must exist
        NamespaceInfo nsi = catalog.getNamespaceByPrefix(prefix);
        if (nsi == null) {
            throw new RestException("Can't change a non existant namespace (" + prefix + ")",
                    HttpStatus.NOT_FOUND);
        }

        String infoName = namespace.getName();
        if (infoName != null && !prefix.equals(infoName)) {
            throw new RestException("Can't change name of workspace", HttpStatus.FORBIDDEN);
        }

        new CatalogBuilder(catalog).updateNamespace(nsi, namespace);
        catalog.save(nsi);
    }

    @DeleteMapping(path = "/namespaces/{prefix}")
    protected void deleteNamespace(@PathVariable String prefix) {

        NamespaceInfo ns = catalog.getNamespaceByPrefix(prefix);
        if (prefix.equals("default")) {
            throw new RestException("Can't delete the default namespace",
                    HttpStatus.METHOD_NOT_ALLOWED);
        }
        if (ns == null) {
            throw new RestException("Namespace '" + prefix + "' not found", HttpStatus.NOT_FOUND);
        }
        if (!catalog.getResourcesByNamespace(ns, ResourceInfo.class).isEmpty()) {
            throw new RestException("Namespace not empty", HttpStatus.UNAUTHORIZED);
        }
        catalog.remove(ns);
    }

    private UriComponents getUriComponents(String name, UriComponentsBuilder builder) {
        UriComponents uriComponents;

        uriComponents = builder.path("/namespaces/{id}").buildAndExpand(name);

        return uriComponents;
    }

    @Override
    protected <T> ObjectWrapper createObjectWrapper(Class<T> clazz) {
        return new ObjectToMapWrapper<NamespaceInfo>(NamespaceInfo.class) {
            @Override
            protected void wrapInternal(Map properties, SimpleHash model, NamespaceInfo namespace) {
                if (properties == null) {
                    try {
                        properties = model.toMap();
                    } catch (TemplateModelException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                NamespaceInfo def = catalog.getDefaultNamespace();
                if (def.equals(namespace)) {
                    properties.put("isDefault", Boolean.TRUE);
                } else {
                    properties.put("isDefault", Boolean.FALSE);
                }
                List<Map<String, Map<String, String>>> resources = new ArrayList<>();
                List<ResourceInfo> res = catalog.getResourcesByNamespace(namespace,
                        ResourceInfo.class);
                for (ResourceInfo r : res) {
                    HashMap<String, String> props = new HashMap<>();
                    props.put("name", r.getName());
                    props.put("description", r.getDescription());
                    resources.add(Collections.singletonMap("properties",
                            props));
                }

                properties.put("resources", resources);
            }

            @Override
            protected void wrapInternal(SimpleHash model,
                                        @SuppressWarnings("rawtypes") Collection object) {

                for (Object w : object) {
                    NamespaceInfo ns = (NamespaceInfo) w;
                    wrapInternal(null, model, ns);
                }
            }
        };
    }

    @Override
    public void configurePersister(XStreamPersister persister, XStreamMessageConverter converter) {
        persister.setCallback(new XStreamPersister.Callback() {
            @Override
            protected Class<NamespaceInfo> getObjectClass() {
                return NamespaceInfo.class;
            }

            @Override
            protected CatalogInfo getCatalogObject() {
                Map<String, String> uriTemplateVars = (Map<String, String>) RequestContextHolder
                        .getRequestAttributes()
                        .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                                RequestAttributes.SCOPE_REQUEST);
                String prefix = uriTemplateVars.get(NAMESPACE_NAME);

                if (prefix == null) {
                    return null;
                }
                return catalog.getNamespaceByPrefix(prefix);
            }

            @Override
            protected void postEncodeNamespace(NamespaceInfo cs, HierarchicalStreamWriter writer,
                    MarshallingContext context) {

            }

            @Override
            protected void postEncodeReference(Object obj, String ref, String prefix,
                    HierarchicalStreamWriter writer, MarshallingContext context) {
                if (obj instanceof NamespaceInfo) {
                    converter.encodeLink("/namespaces/" + converter.encode(ref), writer);
                }
            }
        });
    }
}
