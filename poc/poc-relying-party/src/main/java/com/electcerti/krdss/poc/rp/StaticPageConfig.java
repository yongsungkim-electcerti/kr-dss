package com.electcerti.krdss.poc.rp;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 문서 페이지 라우팅.
 *
 * <p>Spring Boot 의 정적 리소스 핸들러는 루트(/) 외의 디렉터리에 대해 {@code index.html} 을
 * 자동으로 서빙하지 않으므로, 착수보고 슬라이드 덱의 짧은 URL 을 명시적으로 연결한다.</p>
 *
 * <pre>
 *   /kickoff  , /kickoff/   →  classpath:/static/kickoff/index.html    (착수보고 21장)
 *   /seminar  , /seminar/   →  classpath:/static/seminar/index.html    (공동인증사업자 설명 자료)
 * </pre>
 *
 * <p>두 자료 모두 이용사 데모 허브(/)와 같은 서버에서 서빙되므로, 자료 안의
 * "라이브 PoC 데모" 링크({@code /?tab=sign} 등)가 그대로 동작한다.</p>
 */
@Configuration
public class StaticPageConfig implements WebMvcConfigurer {

    private static final String KICKOFF_DECK = "forward:/kickoff/index.html";
    private static final String SEMINAR_DECK = "forward:/seminar/index.html";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/kickoff").setViewName(KICKOFF_DECK);
        registry.addViewController("/kickoff/").setViewName(KICKOFF_DECK);
        registry.addViewController("/seminar").setViewName(SEMINAR_DECK);
        registry.addViewController("/seminar/").setViewName(SEMINAR_DECK);
    }
}
