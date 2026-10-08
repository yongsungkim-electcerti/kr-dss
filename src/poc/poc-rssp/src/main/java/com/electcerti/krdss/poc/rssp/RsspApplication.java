package com.electcerti.krdss.poc.rssp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RSSP/SSA(원격서명 서비스 제공자) 시뮬레이터 (PoC).
 *
 * <p>이용사 서명요소(SIC)에게 <b>CSC v2 표준 API</b>를 제공하는 서버서명
 * 애플리케이션이다. 문서 다이제스트와 서명활성화데이터(SAD)를 받아 SAM·HSM 을
 * 오케스트레이션하고 서명값을 반환한다. eIDAS 원격서명 모델의 진입점 역할을 한다.</p>
 */
@SpringBootApplication
public class RsspApplication {

    public static void main(String[] args) {
        SpringApplication.run(RsspApplication.class, args);
    }
}
