package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.AttestationVerificationResult;
import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.CertificateAuthority;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.dss.pki.KrPkiOids;
import com.electcerti.krdss.dss.pki.RegistrationAuthority;
import com.electcerti.krdss.dss.pki.RegistrationBindingService;
import com.electcerti.krdss.dss.pki.RegistrationResult;
import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-C Layer2 서브목록 ↔ 특허-B 레지스트리 연계 + Layer1 입수(청구항 5·6·7·10).
 */
class TrustListRegistryTest {

    @Test
    void authenticator_registry_maps_grade_from_sublist() {
        var tl = TrustTestSupport.trustList();
        var registry = new TrustListAuthenticatorRegistry(tl.deviceList());

        assertThat(registry.gradeOf(TrustTestSupport.AAGUID_HIGH)).isEqualTo(AuthenticatorGrade.HIGH);
        assertThat(registry.gradeOf(new byte[16])).isEqualTo(AuthenticatorGrade.UNKNOWN); // 미등재

        // 폐지(WITHDRAWN) → UNKNOWN
        tl.deviceList().setAuthenticatorStatus(TrustTestSupport.AAGUID_HIGH, ServiceStatus.WITHDRAWN);
        assertThat(registry.gradeOf(TrustTestSupport.AAGUID_HIGH)).isEqualTo(AuthenticatorGrade.UNKNOWN);
    }

    @Test
    void hsm_registry_maps_grade_from_sublist() {
        var tl = TrustTestSupport.trustList();
        var registry = new TrustListHsmRegistry(tl.deviceList());

        assertThat(registry.gradeOf(TrustTestSupport.HSM_DEVICE)).isEqualTo(HsmGrade.HIGH);
        assertThat(registry.gradeOf(new byte[]{0})).isEqualTo(HsmGrade.UNKNOWN);
    }

    @Test
    void patentB_registration_binding_sources_grade_from_krtl() throws Exception {
        // 특허-C Layer2 레지스트리를 특허-B RegistrationBindingService 에 주입 → 발급 등급이 KR-TL 기준
        var tl = TrustTestSupport.trustList();
        var registry = new TrustListAuthenticatorRegistry(tl.deviceList());
        var binding = new RegistrationBindingService(new CertificateAuthority("ca.kr-dss.example"), registry);

        var kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        var credential = kpg.generateKeyPair();
        var reg = new RegistrationResult(credential.getPublic(), TrustTestSupport.AAGUID_HIGH,
                new byte[]{1, 2, 3}, -7, AttestationVerificationResult.verified("packed"));
        var ra = new RegistrationAuthority("RA-A", "기관A",
                RegistrationAuthority.RaType.IDENTITY, KrPkiOids.raPolicy("1"));

        var issued = binding.bind(reg, ra, "홍길동");

        // KR-TL 에서 HIGH 등급을 조회해 발급에 반영
        assertThat(issued.grade()).isEqualTo(AuthenticatorGrade.HIGH);
    }

    @Test
    void layer1_ingest_from_krtl_model() {
        var krtl = new KrTrustList(
                new KrTrustList.SchemeInformation(1, "KISA", Instant.now(), Instant.now().plusSeconds(86400)),
                List.of(new KrTrustList.TrustServiceProvider("한국전자인증", List.of(
                        new KrTrustList.TrustService("CA/QC", "공동인증CA",
                                ServiceStatus.GRANTED, Instant.now(), new byte[]{1})))));

        var registry = new TrustServiceRegistry().ingest(krtl);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("공동인증CA")).isPresent()
                .get().extracting(TrustServiceRegistry.TrustServiceEntry::status)
                .isEqualTo(ServiceStatus.GRANTED);
    }
}
