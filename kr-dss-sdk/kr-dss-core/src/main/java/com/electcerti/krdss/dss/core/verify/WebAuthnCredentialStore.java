package com.electcerti.krdss.dss.core.verify;

import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebAuthn 자격증명 레지스트리 (특허-A Mode 1 — 로컬 서명 경로).
 *
 * <p>특허-A 설계서 §2.4: WebAuthn 로컬 서명의 검증용 공개키는 <b>CA가 발급한 X.509
 * 인증서</b>(Credential 공개키 = SubjectPublicKeyInfo)에서 가져온다. 본 레지스트리는
 * credentialId 단위로 그 인증서와 메타데이터를 보관하며, SAM/HSM 원격서명 경로와는
 * 물리적으로 분리된 저장소다.</p>
 *
 * <p>등록 시점(특허-B Registration Binding 의 최소 브리지)에서 인증서를 적재하고,
 * 검증 시점에 {@link WebAuthnVerificationPath}가 이를 조회해 어서션 서명을 확인한다.</p>
 */
public class WebAuthnCredentialStore {

    /**
     * 저장된 WebAuthn 자격증명.
     *
     * @param certificate CA 발급 인증서(Credential 공개키 = SPKI)
     * @param coseAlg     COSE 알고리즘 식별자(-7=ES256, -257=RS256)
     * @param aaguid      인증기 모델 식별자(16바이트, 없으면 0)
     * @param signCount   마지막으로 관측된 서명 카운터(replay 탐지)
     */
    public record StoredCredential(X509Certificate certificate, int coseAlg, byte[] aaguid, long signCount) {

        public StoredCredential withSignCount(long newCount) {
            return new StoredCredential(certificate, coseAlg, aaguid, newCount);
        }
    }

    /** credentialId(Base64URL) → 자격증명. */
    private final Map<String, StoredCredential> store = new ConcurrentHashMap<>();

    /** 자격증명을 등록/갱신한다. */
    public void put(String credentialIdB64Url, StoredCredential credential) {
        store.put(credentialIdB64Url, credential);
    }

    /** 자격증명을 조회한다. */
    public Optional<StoredCredential> find(String credentialIdB64Url) {
        return Optional.ofNullable(store.get(credentialIdB64Url));
    }

    /** 검증 후 관측된 서명 카운터를 갱신한다. */
    public void updateSignCount(String credentialIdB64Url, long newCount) {
        store.computeIfPresent(credentialIdB64Url, (k, v) -> v.withSignCount(newCount));
    }
}
