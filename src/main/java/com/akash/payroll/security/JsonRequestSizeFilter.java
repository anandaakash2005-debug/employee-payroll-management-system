package com.akash.payroll.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded buffering also enforces the limit for chunked JSON requests. */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10)
public class JsonRequestSizeFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 65536;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!request.getRequestURI().startsWith("/api/") || !("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()))) {
            chain.doFilter(request, response); return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            response.setStatus(413); response.setContentType("application/json");
            response.getWriter().write("{\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"Request body must not exceed 64 KiB\"}");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream stream = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return stream.read(); }
                    @Override public boolean isFinished() { return stream.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Async request input is not supported"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
}
