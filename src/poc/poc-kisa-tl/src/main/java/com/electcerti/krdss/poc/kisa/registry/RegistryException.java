package com.electcerti.krdss.poc.kisa.registry;

/** 관리 저장·편집 실패. code는 화면이 구분해서 표시하는 값이다. */
public class RegistryException extends RuntimeException {

    public enum Code {
        /** 입력으로 모델을 구성할 수 없음 */
        INVALID_INPUT,
        /** 대상 사업자·서비스·인증서 없음 */
        NOT_FOUND,
        /** 기대 draftRevision이 현재와 다름 */
        REVISION_CONFLICT,
        /** 다른 서비스와 겹치는 인증서·키, 새 키를 기존 서비스에 추가 등 */
        IDENTITY_CONFLICT,
        /** 상태 파일·revision 해석 또는 무결성 확인 실패. 관리 쓰기 중단 */
        STATE_UNAVAILABLE,
        /** 파일 기록·원자 교체 실패. 기존 상태 유지 */
        STORAGE_FAILED,
        /** 같은 requestId에 다른 입력 */
        REQUEST_CONFLICT,
        /** 같은 requestId가 처리 중 */
        REQUEST_IN_PROGRESS,
        /** 이전 시도가 중단되어 종결됨. 새 requestId로 재시도 */
        INTERRUPTED,
        /** 서명 키·인증서 파일을 읽지 못함 */
        KEY_READ_FAILED,
        /** 서명 생성 실패 */
        SIGNING_FAILED,
        /** 생성한 서명의 자체 확인 실패 */
        SIGNATURE_CHECK_FAILED
    }

    private final Code code;

    public RegistryException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public RegistryException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
