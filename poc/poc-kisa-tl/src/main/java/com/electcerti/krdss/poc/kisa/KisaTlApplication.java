package com.electcerti.krdss.poc.kisa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KISA / KR-TL 관리 서비스 (PoC).
 *
 * <p>인정사업자 신뢰정보 수집·검토, KR-TL 생성·전자서명·배포를 담당하는 데모 서버.</p>
 */
@SpringBootApplication
public class KisaTlApplication {

    public static void main(String[] args) {
        SpringApplication.run(KisaTlApplication.class, args);
    }
}
