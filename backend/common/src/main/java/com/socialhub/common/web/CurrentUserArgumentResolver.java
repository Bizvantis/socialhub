package com.socialhub.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves {@link CurrentUser} controller parameters from the gateway identity headers. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String ROLES = "X-User-Roles";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        AuthUser ann = parameter.getParameterAnnotation(AuthUser.class);
        boolean required = ann == null || ann.required();

        String id = request.getHeader(USER_ID);
        if (id == null || id.isBlank()) {
            if (required) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
            }
            return null;
        }
        try {
            String roles = request.getHeader(ROLES);
            Set<String> roleSet = roles == null || roles.isBlank() ? Set.of()
                    : Arrays.stream(roles.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
            return new CurrentUser(Long.parseLong(id), request.getHeader(USERNAME), roleSet);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid identity");
        }
    }
}
