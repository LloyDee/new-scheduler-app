package org.volunteers.scheduler.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UriComponentsBuilder;

/** Carries list context through existing mutation redirects, including validation retries. */
@Configuration
public class ListContextConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public void postHandle(HttpServletRequest request, HttpServletResponse response,
                                             Object handler, ModelAndView model) {
                if (model == null || !"POST".equals(request.getMethod())) return;
                String view = model.getViewName();
                String target = request.getParameter("returnTo");
                if (view == null || !view.startsWith("redirect:") || target == null) return;
                String destination = view.substring(9);
                String base = destination.split("[?#]", 2)[0];
                // Only permit the exact same event's list; never accept an external redirect.
                if (!base.matches("/events/\\d+/(schedule|volunteers)")
                        || !request.getRequestURI().startsWith(request.getContextPath() + base.substring(0, base.lastIndexOf('/') + 1))
                        || !(target.equals(base) || target.startsWith(base + "?"))
                        || target.contains("#") || target.contains("\\") || target.contains("\r") || target.contains("\n")) return;
                org.springframework.web.util.UriComponents context;
                try { context = UriComponentsBuilder.fromUriString(target).build(); }
                catch (IllegalArgumentException ex) { return; }
                var builder = UriComponentsBuilder.fromPath(base);
                for (String key : new String[]{"departmentId", "date", "q", "page", "view", "scroll"}) {
                    var values = context.getQueryParams().get(key);
                    if (values != null) {
                        try {
                            builder.queryParam(key, values.stream().map(value -> java.net.URLDecoder.decode(
                                    value, java.nio.charset.StandardCharsets.UTF_8)).toArray());
                        } catch (IllegalArgumentException ex) { return; }
                    }
                }
                var redirect = UriComponentsBuilder.fromUriString(destination).build();
                redirect.getQueryParams().forEach((key, values) -> builder.replaceQueryParam(key, values.toArray()));
                if (!redirect.getQueryParams().isEmpty()) builder.queryParam("returnTo", target);
                // Exact scroll restoration takes precedence over the old row anchor.
                if (!context.getQueryParams().containsKey("scroll")) builder.fragment(redirect.getFragment());
                model.setViewName("redirect:" + builder.build().encode().toUriString());
            }
        }).addPathPatterns("/events/**");
    }
}
