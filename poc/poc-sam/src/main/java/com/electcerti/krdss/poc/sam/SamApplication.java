package com.electcerti.krdss.poc.sam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SAM(서명활성화모듈) 시뮬레이터 (PoC).
 *
 * <p>원격전자서명에서 서명자의 <b>단독 통제권(sole control)</b>을 보장하는 핵심
 * 구성요소다. 서명활성화데이터(SAD)를 검증하여 "이 서명자가 바로 이 문서(해시)에
 * 대해 서명을 승인했음"을 확인한 뒤에만 HSM 의 서명키를 활성화한다.
 * EN 419 241-1/-2 의 SAM 역할을 모사한다.</p>
 */
@SpringBootApplication
public class SamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SamApplication.class, args);
    }
}
