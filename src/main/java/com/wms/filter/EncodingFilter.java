package com.wms.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * EncodingFilter — Forces UTF-8 on every request/response.
 * Handles Vietnamese characters (ă, ơ, ê, etc.)
 */
public class EncodingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  req  = (HttpServletRequest)  request;

        String path = req.getRequestURI();

        if (path.startsWith("/assets/") || path.endsWith(".css") || path.endsWith(".js")
                || path.endsWith(".woff2") || path.endsWith(".woff") || path.endsWith(".ttf")
                || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")
                || path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".ico")) {
            chain.doFilter(request, response);
            return;
        }

        // Set request character encoding first thing before reading any parameters
        request.setCharacterEncoding("UTF-8");

        // Skip setting content-type for ajax/JSON endpoints — servlet owns the
        // response type. Forcing text/html here can cause Tomcat to start the
        // chunked transfer and then the servlet swaps the content-type,
        // resulting in a malformed chunked body on the client.
        String accept = req.getHeader("Accept");
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(req.getHeader("X-Requested-With"))
                || (accept != null && accept.contains("application/json"))
                || "1".equals(req.getParameter("ajax"));
        
        response.setCharacterEncoding("UTF-8");
        if (!isAjax) {
            response.setContentType("text/html; charset=UTF-8");
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
