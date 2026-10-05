package io.wulfcodes.messaging.ui.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JSON errors for the UI's own REST resources only (pages handle their errors in the view).
 */
@RestControllerAdvice(basePackages = "io.wulfcodes.messaging.ui.controller.resource")
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoggedInException.class)
    public ProblemDetail notLoggedIn(NotLoggedInException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthClientException.class)
    public ProblemDetail authClient(AuthClientException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }
}
