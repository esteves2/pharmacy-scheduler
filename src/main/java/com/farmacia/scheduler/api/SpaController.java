package com.farmacia.scheduler.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards all non-API, non-static requests to index.html so React Router
 * can handle client-side navigation on a hard refresh or direct URL entry.
 */
@Controller
public class SpaController {

    @GetMapping(value = {"/", "/{path:[^\\.]*}", "/{path:[^\\.]*}/**"})
    public String forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Let Spring MVC handle API and static asset requests normally
        if (uri.startsWith("/api/")) {
            return null;
        }
        return "forward:/index.html";
    }
}
