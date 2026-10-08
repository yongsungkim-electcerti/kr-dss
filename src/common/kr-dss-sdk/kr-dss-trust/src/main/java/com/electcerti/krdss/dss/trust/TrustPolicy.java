package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.HsmGrade;

/**
 * 검증 정책 — 미등재/등급 기준 (특허-C 청구항 12, 발명 C-1 효과 ⑥).
 *
 * <p>장치 유형별 최소 허용 보안 등급과, 미등재 장치 처리 방침을 보유한다. 정책 자동 갱신
 * (청구항 8)으로 변경될 수 있다.</p>
 *
 * <p>등급 충족 판정은 enum 선언 순서(HIGH=0 … UNKNOWN=3)를 이용한다 — {@code ordinal} 이 작을수록
 * 높은 등급이며, {@code grade.ordinal() <= min.ordinal()} 이면 정책 충족이다.</p>
 */
public final class TrustPolicy {

    private volatile AuthenticatorGrade minAuthenticatorGrade;
    private volatile HsmGrade minHsmGrade;
    private volatile VerificationStatus unregisteredDevicePolicy;

    public TrustPolicy() {
        this(AuthenticatorGrade.MEDIUM, HsmGrade.MEDIUM, VerificationStatus.INDETERMINATE);
    }

    public TrustPolicy(AuthenticatorGrade minAuthenticatorGrade, HsmGrade minHsmGrade,
                       VerificationStatus unregisteredDevicePolicy) {
        this.minAuthenticatorGrade = minAuthenticatorGrade;
        this.minHsmGrade = minHsmGrade;
        this.unregisteredDevicePolicy = unregisteredDevicePolicy;
    }

    public boolean meetsAuthenticatorGrade(AuthenticatorGrade grade) {
        return grade.ordinal() <= minAuthenticatorGrade.ordinal();
    }

    public boolean meetsHsmGrade(HsmGrade grade) {
        return grade.ordinal() <= minHsmGrade.ordinal();
    }

    /** 미등재 장치 처리 방침(INDETERMINATE 또는 TOTAL_FAILED). */
    public VerificationStatus unregisteredDevicePolicy() {
        return unregisteredDevicePolicy;
    }

    public void setMinAuthenticatorGrade(AuthenticatorGrade grade) {
        this.minAuthenticatorGrade = grade;
    }

    public void setMinHsmGrade(HsmGrade grade) {
        this.minHsmGrade = grade;
    }

    public void setUnregisteredDevicePolicy(VerificationStatus status) {
        this.unregisteredDevicePolicy = status;
    }
}
