package com.electcerti.krdss.dss.remote;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

/**
 * RFC 6238 TOTP(시간 기반 일회용 비밀번호) 생성·검증 유틸리티.
 *
 * <p>서명자 2요소 인증의 "가진 것(possession)" 요소로 사용한다. 서명자 단말(SIC)은
 * 인증앱의 코드를 입력받지만, 데모에서는 동일 비밀로 현재 코드를 생성하여 자동
 * 시연한다. SAM 은 ±1 스텝 시간오차를 허용해 코드를 검증한다.</p>
 *
 * <p>HMAC-SHA1 · 30초 스텝 · 6자리(표준 기본값). 비밀키는 Base32 로 표현한다.</p>
 */
public final class Totp {

    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    private Totp() {
    }

    /** 주어진 시각(epoch 초)의 TOTP 코드를 생성한다. */
    public static String generate(String base32Secret, long epochSecond) {
        long counter = epochSecond / STEP_SECONDS;
        byte[] key = base32Decode(base32Secret);
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash = hmacSha1(key, msg);

        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    /** 현재 코드 또는 ±1 스텝 이내 코드와 일치하면 유효로 본다(시계 오차 허용). */
    public static boolean verify(String base32Secret, String code, long epochSecond) {
        if (code == null) {
            return false;
        }
        for (int drift = -1; drift <= 1; drift++) {
            if (generate(base32Secret, epochSecond + (long) drift * STEP_SECONDS).equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    private static byte[] hmacSha1(byte[] key, byte[] msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(msg);
        } catch (Exception e) {
            throw new RemoteSignException("TOTP HMAC 계산 실패", e);
        }
    }

    /** RFC 4648 Base32 디코딩(대문자/패딩 무시). */
    static byte[] base32Decode(String s) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String clean = s.trim().replace("=", "").toUpperCase();
        int outLen = clean.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : clean.toCharArray()) {
            int val = alphabet.indexOf(c);
            if (val < 0) {
                throw new RemoteSignException("Base32 문자 오류: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[idx++] = (byte) ((buffer >> bitsLeft) & 0xff);
            }
        }
        return out;
    }
}
