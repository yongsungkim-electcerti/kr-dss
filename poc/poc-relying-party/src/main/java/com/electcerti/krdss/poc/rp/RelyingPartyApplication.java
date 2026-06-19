package com.electcerti.krdss.poc.rp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 가상 이용사 서비스 (PoC).
 *
 * <p>KR-DSS SDK 를 호출해 전자문서 서명 생성·검증 전 과정을 시연하는 데모 서비스.</p>
 */
@SpringBootApplication
public class RelyingPartyApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelyingPartyApplication.class, args);
    }
}
