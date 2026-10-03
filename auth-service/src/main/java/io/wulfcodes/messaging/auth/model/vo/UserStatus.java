package io.wulfcodes.messaging.auth.model.vo;

/**
 * Lifecycle state of an account. Only ACTIVE users can log in or refresh tokens.
 */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
