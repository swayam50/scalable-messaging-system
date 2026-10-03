package io.wulfcodes.messaging.auth.exception;

import org.springframework.http.HttpStatus;

public class AccountDisabledException extends ApiException {

    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "Account disabled", "This account is disabled");
    }
}
