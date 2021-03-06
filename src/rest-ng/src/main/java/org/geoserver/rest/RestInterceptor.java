package org.geoserver.rest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * Interceptor for all rest-ng requests
 *
 * Adds a {@link RequestInfo} to the request attributes
 */
public class RestInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        RequestInfo.set(new RequestInfo(request));

        return true;
    }
}
