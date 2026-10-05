package io.wulfcodes.messaging.ui.exception;

/**
 * No (valid) login in the current HttpSession.
 */
public class NotLoggedInException extends RuntimeException {

    public NotLoggedInException() {
        super("Not logged in");
    }
}
