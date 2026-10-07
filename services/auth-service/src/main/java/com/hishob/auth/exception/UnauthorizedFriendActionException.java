package com.hishob.auth.exception;

public class UnauthorizedFriendActionException extends RuntimeException {
    public UnauthorizedFriendActionException(String message) {
        super(message);
    }
}
