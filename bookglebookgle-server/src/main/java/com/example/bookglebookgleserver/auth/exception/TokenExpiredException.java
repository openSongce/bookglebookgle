package com.example.bookglebookgleserver.auth.exception;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String msg, Throwable cause) { super(msg, cause); }
    public TokenExpiredException(String msg) { super(msg); }
}
