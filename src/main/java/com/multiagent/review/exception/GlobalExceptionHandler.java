package com.multiagent.review.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PipelineException.class)
    public ProblemDetail handlePipelineException(PipelineException ex) {
        log.error("Pipeline error: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), "/errors/pipeline-error");
    }

    @ExceptionHandler(GitHubApiException.class)
    public ProblemDetail handleGitHubApiException(GitHubApiException ex) {
        log.error("GitHub API error: {}", ex.getMessage(), ex);
        return problem(HttpStatus.BAD_GATEWAY, ex.getMessage(), "/errors/github-api-error");
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ProblemDetail handleEmailDeliveryException(EmailDeliveryException ex) {
        log.error("Email delivery error: {}", ex.getMessage(), ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery failed — please retry later",
                "/errors/email-error");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation error: {}", detail);
        return problem(HttpStatus.BAD_REQUEST, detail, "/errors/validation-error");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = String.format("Parameter '%s' has invalid value '%s'",
                ex.getName(), ex.getValue());
        log.warn("Type mismatch: {}", detail);
        return problem(HttpStatus.BAD_REQUEST, detail, "/errors/type-mismatch");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", "/errors/internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String detail, String type) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(type));
        return pd;
    }
}
