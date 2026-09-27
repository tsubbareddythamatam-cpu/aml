package org.aml.config;

import org.aml.resolver.TokenArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Registers the custom resolver for authenticated-token controller parameters.
     *
     * @param resolvers Spring MVC argument resolver registry
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // Appends our token resolver component to the active Spring MVC request pipeline
        resolvers.add(new TokenArgumentResolver());
    }
}
