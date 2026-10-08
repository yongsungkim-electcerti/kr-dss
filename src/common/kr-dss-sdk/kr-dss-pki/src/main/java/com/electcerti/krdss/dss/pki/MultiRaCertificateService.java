package com.electcerti.krdss.dss.pki;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Multi-RA 인증서 발급 제어부 — 특허-B 청구항 2(다중 RA 인프라).
 *
 * <p>단일 WebAuthn 등록 결과에 대하여 복수의 RA 에 대응하는 복수의 X.509 인증서를 발급한다.
 * 모든 인증서는 <b>동일한 SubjectPublicKeyInfo 와 동일한 SubjectKeyIdentifier</b> 를 가지며,
 * RA 별로 <b>서로 다른 인증서 정책 OID</b> 를 갖는다(청구항 13).</p>
 *
 * <p>SubjectKeyIdentifier(hex) 를 키로 발급 인증서를 색인하여, 단일 등록 결과에서 발급된
 * 연관 인증서를 O(1) 로 조회한다(청구항 13). 기존 사용자가 추가 RA 와 관계를 맺을 때는
 * 동일 등록 결과로 {@link #issueForRa} 를 재호출하여 추가 발급한다(청구항 14).</p>
 */
public final class MultiRaCertificateService {

    private final RegistrationBindingService binding;
    private final ConcurrentHashMap<String, List<IssuedCertificate>> byKeyId = new ConcurrentHashMap<>();

    public MultiRaCertificateService(RegistrationBindingService binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
    }

    /**
     * 단일 등록 결과 → 복수 RA 인증서 발급(청구항 2).
     */
    public List<IssuedCertificate> issueForAll(RegistrationResult reg,
                                               List<RegistrationAuthority> ras, String subjectCn) {
        List<IssuedCertificate> issued = new ArrayList<>(ras.size());
        for (RegistrationAuthority ra : ras) {
            issued.add(issueForRa(reg, ra, subjectCn));
        }
        return issued;
    }

    /**
     * 단일 RA 발급 + SubjectKeyIdentifier 색인 등록(청구항 14 추가 발급 포함).
     */
    public IssuedCertificate issueForRa(RegistrationResult reg, RegistrationAuthority ra, String subjectCn) {
        IssuedCertificate issued = binding.bind(reg, ra, subjectCn);
        byKeyId.computeIfAbsent(issued.keyIdentifierHex(), k -> new CopyOnWriteArrayList<>()).add(issued);
        return issued;
    }

    /**
     * SubjectKeyIdentifier(hex) 기준 연관 인증서 O(1) 조회(청구항 13).
     *
     * @return 동일 등록 결과에서 발급된 인증서 목록(없으면 빈 목록)
     */
    public List<IssuedCertificate> findByKeyIdentifier(String keyIdHex) {
        return List.copyOf(byKeyId.getOrDefault(keyIdHex, List.of()));
    }
}
