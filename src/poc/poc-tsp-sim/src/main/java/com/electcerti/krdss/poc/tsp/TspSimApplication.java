package com.electcerti.krdss.poc.tsp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 가상 인정사업자 (PoC) — :8082.
 *
 * <p>실증용 CA·RA·OCSP 를 모사하여 독립 테스트베드를 구성한다. 실제 운영 인증서가 아닌
 * 실증용 환경(New-KISA RootCA → 공동인증CA 체인)에서 발급·폐지·검증 전 과정을 재현한다.</p>
 *
 * <p>엔드포인트:</p>
 * <ul>
 *   <li><b>CA</b> {@code GET /ca/certificate|/chain|/certs} — CA 인증서·체인·발급 대장.</li>
 *   <li><b>RA</b> {@code POST /ra/enroll}(서버 키생성) · {@code POST /ra/enroll-csr}(PKCS#10).</li>
 *   <li><b>OCSP</b> {@code POST /ocsp}(RFC 6960) · {@code POST /ocsp/admin/{revoke,suspend,resume}}.</li>
 * </ul>
 *
 * <p>TSA(시점확인)는 후속 과제로 남긴다.</p>
 */
@SpringBootApplication
public class TspSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(TspSimApplication.class, args);
    }
}
