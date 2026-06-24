package com.electcerti.krdss.poc.rssp;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CSC v2 API 에 서비스 인증 인터셉터를 적용한다. ({@code /oauth2/token} 은 제외)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BearerAuthInterceptor bearerAuth;

    public WebConfig(BearerAuthInterceptor bearerAuth) {
        this.bearerAuth = bearerAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bearerAuth).addPathPatterns("/csc/v2/**");
    }
}
