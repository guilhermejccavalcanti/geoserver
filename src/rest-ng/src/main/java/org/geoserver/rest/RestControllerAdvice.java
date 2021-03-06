package org.geoserver.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geoserver.platform.GeoServerExtensions;
import org.geotools.util.logging.Logging;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Controller advice for geoserver rest
 *
 * A note on the exception handling here:
 *
 * The manual exception handling, using the response output stream directly and the response/request
 * directly is very much NOT RECOMMENDED. Prefer to use ResponseEntity objects to return proper
 * errors.
 *
 * BUT
 *
 * GeoServer test cases do two silly things:
 *
 * - Make requests without any accepts and then look for an exact string in the response. Without
 *   the accepts header spring has no idea what the response should be, so it tries to pick the first
 *   default based on the producible media types. This is, frequently, HTML
 *
 */
@ControllerAdvice
public class RestControllerAdvice extends ResponseEntityExceptionHandler {

    static final Logger LOGGER = Logging.getLogger(RestControllerAdvice.class);
    
    private void notifyExceptionToCallbacks(WebRequest webRequest, HttpServletResponse response, Exception ex) {
        if(!(webRequest instanceof ServletWebRequest)) {
            return;
        }
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        notifyExceptionToCallbacks(request, response, ex);
    }

    private void notifyExceptionToCallbacks(HttpServletRequest request,
            HttpServletResponse response, Exception ex) {
        List<DispatcherCallback> callbacks = GeoServerExtensions.extensions(DispatcherCallback.class);
        for (DispatcherCallback callback : callbacks) {
            callback.exception(request, response, ex);
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleResourceNotFound(ResourceNotFoundException e, HttpServletResponse response, WebRequest request, OutputStream os)
        throws IOException {
        notifyExceptionToCallbacks(request, response, e);
        
        String quietOnNotFound = request.getParameter("quietOnNotFound"); //yes this is seriously a thing
        String message = e.getMessage();
        if (Boolean.parseBoolean(quietOnNotFound)) {
            message = "";
        } else {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
        response.setStatus(404);
        StreamUtils.copy(message, Charset.forName("UTF-8"), os);
    }

    @ExceptionHandler(RestException.class)
    public void handleRestException(RestException e, HttpServletResponse response, WebRequest request, OutputStream os)
        throws IOException {
        LOGGER.log(Level.SEVERE, e.getMessage(), e);
        notifyExceptionToCallbacks(request, response, e);

        response.setStatus(e.getStatus().value());
        StreamUtils.copy(e.getMessage(), Charset.forName("UTF-8"), os);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleGeneralException(Exception e, HttpServletRequest request,
        HttpServletResponse response, OutputStream os) throws IOException {
        LOGGER.log(Level.SEVERE, e.getMessage(), e);
        notifyExceptionToCallbacks(request, response, e);
        
        response.setStatus(500);
        StreamUtils.copy(e.getMessage(), Charset.forName("UTF-8"), os);
    }
}
