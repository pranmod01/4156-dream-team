package com.resourceallocator.exception;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts API exceptions into RFC 9457 problem detail responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Maps a missing or invalid API key to 401 Unauthorized.
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(final UnauthorizedException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Maps a business rule violation to 400 Bad Request.
     *
     * @param ex the exception
     * @return the problem detail
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(
            final InvalidRequestException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Maps a request body that fails bean validation to 400 Bad Request,
     * listing each invalid field.
     *
     * @param ex the exception
     * @return the problem detail, with an {@code errors} property
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(
            final MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(ApiExceptionHandler::describe)
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body failed validation.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String describe(final FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
