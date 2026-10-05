package io.wulfcodes.messaging.common.model.vo;

/**
 * Lifecycle state of an account. Only ACTIVE users can log in or refresh tokens.
 */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
