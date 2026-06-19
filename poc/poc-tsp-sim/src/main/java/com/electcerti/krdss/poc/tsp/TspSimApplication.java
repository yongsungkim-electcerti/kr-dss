package com.electcerti.krdss.poc.tsp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 가상 인정사업자 (PoC).
 *
 * <p>실증용 RootCA·OCSP·TSA 를 모사하여 독립 테스트베드를 구성한다.
 * 실제 운영 인증서가 아닌 실증용 환경에서 전 과정을 안전하게 재현한다.</p>
 */
@SpringBootApplication
public class TspSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(TspSimApplication.class, args);
    }
}
