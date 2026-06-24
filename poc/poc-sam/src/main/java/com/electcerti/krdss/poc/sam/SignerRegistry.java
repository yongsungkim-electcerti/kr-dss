package com.electcerti.krdss.poc.sam;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서명자 계정 레지스트리.
 *
 * <p>서명자별 2요소 인증 자격(아는 것 PIN, 가진 것 TOTP 비밀)을 보관한다. PIN 은
 * 솔트 적용 SHA-256 해시로만 저장하며 평문을 보관하지 않는다. 데모에서는 부팅 시
 * 설정값으로 1개 서명자를 등록한다.</p>
 *
 * <p>운영에서는 IdP/디렉터리 연동, PBKDF2/Argon2 등 강화 해시, HSM 보관을 적용한다.</p>
 */
@Component
public class SignerRegistry {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, SignerAccount> signers = new ConcurrentHashMap<>();

    /** 데모 서명자 설정값. */
    private final String demoSignerId;
    private final String demoPin;
    private final String demoTotpSecret;

    public SignerRegistry(
            @Value("${krdss.sam.signer.id:signer-001}") String demoSignerId,
            @Value("${krdss.sam.signer.pin:123456}") String demoPin,
            @Value("${krdss.sam.signer.totp-secret:JBSWY3DPEHPK3PXP}") String demoTotpSecret) {
        this.demoSignerId = demoSignerId;
        this.demoPin = demoPin;
        this.demoTotpSecret = demoTotpSecret;
    }

    /** PIN 해시 저장 항목. 평문 PIN 은 보관하지 않는다. */
    public record SignerAccount(String signerId, byte[] salt, String pinHashHex, String totpSecretBase32) {
    }

    @PostConstruct
    void seed() {
        register(demoSignerId, demoPin, demoTotpSecret);
    }

    public void register(String signerId, String pin, String totpSecretBase32) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        signers.put(signerId, new SignerAccount(signerId, salt, hash(pin, salt), totpSecretBase32));
    }

    public SignerAccount find(String signerId) {
        return signers.get(signerId);
    }

    /** PIN 검증(솔트 적용 해시 비교, 상수시간). */
    public boolean verifyPin(SignerAccount account, String pin) {
        if (pin == null) {
            return false;
        }
        String candidate = hash(pin, account.salt());
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                account.pinHashHex().getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String pin, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pin.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("PIN 해시 실패", e);
        }
    }
}
