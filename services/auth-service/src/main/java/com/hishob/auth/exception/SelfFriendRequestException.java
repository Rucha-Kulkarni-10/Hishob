package com.hishob.auth.exception;

public class SelfFriendRequestException extends RuntimeException {
    public SelfFriendRequestException(String message) {
        super(message);
    }
}
