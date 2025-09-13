package com.kaiyikang.winter.exception;

public class BeansException extends NestedRuntimeException {
    public BeansException() {
    }

    public BeansException(String msg) {
        super(msg);
    }

    public BeansException(Throwable cause) {
        super(cause);
    }

    public BeansException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
