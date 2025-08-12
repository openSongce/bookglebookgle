package com.example.bookglebookgleserver.auth.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String msg, Throwable cause) { super(msg, cause); }
    public InvalidTokenException(String msg) { super(msg); }
}

