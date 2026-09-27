package org.aml.resolver;

import org.aml.annotation.AuthenticatedToken;
import org.aml.utility.HeaderUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class TokenArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Determines whether the resolver supports a controller parameter.
     *
     * @param parameter controller method parameter to inspect
     * @return {@code true} for String parameters annotated with {@link AuthenticatedToken}
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedToken.class)
                && parameter.getParameterType().equals(String.class);
    }

    /**
     * Extracts the bearer token from the current request's Authorization header.
     *
     * @param parameter controller method parameter being resolved
     * @param mavContainer current model and view container
     * @param webRequest current web request
     * @param binderFactory binder factory for the current request
     * @return extracted token
     * @throws Exception if argument resolution fails
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {

        // 1. Fetch the raw Authorization header out of the web request context
        String authHeader = webRequest.getHeader(HttpHeaders.AUTHORIZATION);

        // 2. Reuse the custom HeaderUtils extraction logic we built
        return HeaderUtils.extractBearerToken(authHeader);
    }
}
