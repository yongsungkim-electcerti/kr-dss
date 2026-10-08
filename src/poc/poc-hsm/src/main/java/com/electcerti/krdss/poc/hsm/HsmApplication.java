package com.electcerti.krdss.poc.hsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HSM 시뮬레이터 (PoC).
 *
 * <p>원격전자서명 아키텍처에서 <b>서명키 보관 + 서명연산</b>을 담당하는 HSM/QSCD
 * 역할을 모사한다. 키는 HSM 경계를 벗어나지 않으며, 외부에는 다이제스트에 대한
 * 서명값만 반환한다. 서명키 활성화는 SAM(서명활성화모듈)의 지시를 통해서만 이뤄진다.</p>
 */
@SpringBootApplication
public class HsmApplication {

    public static void main(String[] args) {
        SpringApplication.run(HsmApplication.class, args);
    }
}
