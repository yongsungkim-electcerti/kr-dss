package com.electcerti.krdss.ades.cades.bind;

import java.util.Base64;

/**
 * 서명 결속부 — WebAuthn Challenge 파생.
 *
 * <p>특허-A 청구항 1/9: SignedAttrs 의 인코딩값에 대한 해시 연산으로 Challenge 를
 * 생성한다.</p>
 *
 * <pre>
 *   Challenge = BASE64URL( HashFunc( DER(SignedAttrs) ) )
 *   일실시예 : Challenge = BASE64URL( SHA-256( DER(SignedAttrs) ) )
 * </pre>
 *
 * <p>이 Challenge 가 WebAuthn {@code clientDataJSON.challenge}로 운반되어, 전자문서·
 * 서명 시각·서명자 인증서가 단일 Challenge 에 암호학적으로 결속된다.</p>
 */
public final class SignatureBindingService {

    private SignatureBindingService() {
    }

    /** SignedAttrs DER 와 해시 알고리즘으로부터 Challenge(Base64URL) 를 파생한다. */
    public static String deriveChallenge(byte[] signedAttrsDer, HashSuite hashSuite) {
        if (signedAttrsDer == null || signedAttrsDer.length == 0) {
            throw new IllegalArgumentException("signedAttrsDer 가 비어 있습니다");
        }
        byte[] hash = hashSuite.digest(signedAttrsDer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /** {@link SignedAttrsBuilder.SignedAttrs} 로부터 Challenge 를 파생한다. */
    public static String deriveChallenge(SignedAttrsBuilder.SignedAttrs signedAttrs) {
        return deriveChallenge(signedAttrs.der(), signedAttrs.hashSuite());
    }
}
