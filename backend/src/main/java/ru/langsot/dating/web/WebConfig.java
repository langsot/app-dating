package ru.langsot.dating.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.langsot.dating.config.AppProperties;
import ru.langsot.dating.exception.UnauthorizedException;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    /** CORS: запросы к API разрешены только с адреса мини-приложения. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.telegram().webAppUrl())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("Authorization", "Content-Type");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                          NativeWebRequest request, WebDataBinderFactory binderFactory) {
                Object id = request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR, RequestAttributes.SCOPE_REQUEST);
                if (id == null) {
                    throw new UnauthorizedException("Требуется авторизация");
                }
                return id;
            }
        });
    }
}
