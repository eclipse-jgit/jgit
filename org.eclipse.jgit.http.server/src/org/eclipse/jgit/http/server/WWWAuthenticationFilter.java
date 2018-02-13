package org.eclipse.jgit.http.server;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.eclipse.jgit.util.HttpSupport.HDR_WWW_AUTHENTICATE;

/**
 * In case of HTTP 401 status, ensures WWW-Authentication header is included
 * in response header.
 */
public class WWWAuthenticationFilter implements Filter {

    private String realmName;

    public WWWAuthenticationFilter(String realmName) {
        this.realmName = realmName;
    }

    /** {@inheritDoc} */
    @Override
    public void init(FilterConfig config) throws ServletException {
        // Do nothing.
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        // Do nothing.
    }

    /** {@inheritDoc} */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        try {
            chain.doFilter(request, response);
        } finally {
            HttpServletResponse rsp = (HttpServletResponse) response;
            if (rsp.getStatus() == SC_UNAUTHORIZED && rsp.getHeader(HDR_WWW_AUTHENTICATE) == null) {
                rsp.setHeader(HDR_WWW_AUTHENTICATE, String.format("Basic realm=\"%s\"", realmName));
            }
        }
    }


}
