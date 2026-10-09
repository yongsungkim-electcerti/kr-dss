package com.electcerti.krdss.poc.kisa;

import com.electcerti.krdss.poc.kisa.issuance.IssuanceService;
import com.electcerti.krdss.poc.kisa.registry.RegistryService;
import com.electcerti.krdss.poc.kisa.registry.RegistryStore;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 관리 저장소·편집·발행 서비스 구성. 저장 루트 잠금은 애플리케이션 종료 시 해제한다. */
@Configuration
public class RegistryConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "close")
    public RegistryStore registryStore(@Value("${krtl.admin.storage-root}") String root, Clock clock) {
        return RegistryStore.open(Path.of(root), clock);
    }

    @Bean
    public RegistryService registryService(RegistryStore store, Clock clock) {
        return new RegistryService(store, clock);
    }

    @Bean
    public IssuanceService issuanceService(RegistryStore store, @Value("${krtl.admin.signer-dir}") String signerDir,
            Clock clock) {
        return new IssuanceService(store, Path.of(signerDir), clock);
    }
}
