package com.electcerti.krdss.dss.remote;

/** 원격전자서명 처리 중 발생하는 오류. */
public class RemoteSignException extends RuntimeException {

    public RemoteSignException(String message) {
        super(message);
    }

    public RemoteSignException(String message, Throwable cause) {
        super(message, cause);
    }
}
