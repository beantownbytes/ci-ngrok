package io.jenkins.plugins.cingrok;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Extension
public class CrumbExclusionImpl extends CrumbExclusion {

    @Override
    public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getPathInfo();
        if (path != null && path.startsWith("/ci-ngrok/")) {
            chain.doFilter(request, response);
            return true;
        }
        return false;
    }
}
