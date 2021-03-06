/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geoserver.rest.converters.BaseMessageConverter;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

@Component
public class MapXMLConverter extends BaseMessageConverter {
    
    @Override
    public boolean canRead(Class clazz, MediaType mediaType) {
        return Map.class.isAssignableFrom(clazz)
                && getSupportedMediaTypes().contains(mediaType);
    }

    @Override
    public boolean canWrite(Class clazz, MediaType mediaType) {
        return Map.class.isAssignableFrom(clazz)
                && getSupportedMediaTypes().contains(mediaType);
    }

    @Override
    public List getSupportedMediaTypes() {
        return Arrays.asList(MediaType.TEXT_XML, MediaType.APPLICATION_XML);
    }

    @Override
    public Object read(Class clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Object result = null;
        SAXBuilder builder = new SAXBuilder();
        Document doc;
        try {
            doc = builder.build(inputMessage.getBody());
        } catch (JDOMException e) {
            throw (IOException) new IOException("Error building document").initCause(e);
        }

        Element elem = doc.getRootElement();
        result = convert(elem);
        return result;
    }

    @Override
    public void write(Object t, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        Map map = (Map) t;
        Element root = new Element(getMapName(map));
        final Document doc = new Document(root);
        insert(root, map);
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        outputter.output(doc, outputMessage.getBody());
    }

    protected String getMapName(Map map) {
        if(map instanceof NamedMap) {
            return ((NamedMap) map).getName();
        } else {
            return "root";
        }
    }

    /**
     * Interpret XML and convert it back to a Java collection.
     *
     * @param elem a JDOM element
     * @return the Object produced by interpreting the XML
     */
    protected Object convert(Element elem) {
        List children = elem.getChildren();
        if (children.size() == 0) {
            if (elem.getContent().size() == 0) {
                return null;
            } else {
                return elem.getText();
            }
        } else if (children.get(0) instanceof Element) {
            Element child = (Element) children.get(0);
            if (child.getName().equals("entry")) {
                List l = new ArrayList();
                Iterator it = elem.getChildren("entry").iterator();
                while (it.hasNext()) {
                    Element curr = (Element) it.next();
                    l.add(convert(curr));
                }
                return l;
            } else {
                Map m = new NamedMap(child.getName());
                Iterator it = children.iterator();
                while (it.hasNext()) {
                    Element curr = (Element) it.next();
                    m.put(curr.getName(), convert(curr));
                }
                return m;
            }
        }
        throw new RuntimeException("Unable to parse XML");
    }

    /**
     * Generate the JDOM element needed to represent an object and insert it into the parent element given.
     * 
     * @todo This method is recursive and could cause stack overflow errors for large input maps.
     *
     * @param elem the parent Element into which to insert the created JDOM element
     * @param o the Object to be converted
     */
    protected void insert(Element elem, Object o) {
        if (o instanceof Map) {
            Iterator it = ((Map) o).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                Element newElem = new Element(entry.getKey().toString());
                insert(newElem, entry.getValue());
                elem.addContent(newElem);
            }
        } else if (o instanceof Collection) {
            Iterator it = ((Collection) o).iterator();
            while (it.hasNext()) {
                Element newElem = new Element("entry");
                Object entry = it.next();
                insert(newElem, entry);
                elem.addContent(newElem);
            }
        } else {
            elem.addContent(o == null ? "" : o.toString());
        }
    }

}
