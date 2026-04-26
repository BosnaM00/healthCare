package org.example.healthcare.common;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Global exception handler producing RFC 7807 {@code application/problem+json} responses.
 *
 * <p>All error bodies include:
 * <ul>
 *   <li>{@code type} — URI identifying the problem type</li>
 *   <li>{@code title} — short human-readable summary</li>
 *   <li>{@code status} — HTTP status code</li>
 *   <li>{@code detail} — human-readable explanation</li>
 *   <li>{@code instance} — request path</li>
 *   <li>{@code traceId} — random UUID for correlation (log-searchable)</li>
 * </ul>
 *
 * <p>Raw Stripe error messages are NEVER forwarded to patients. The Stripe SDK exceptions
 * are translated via {@link StripeErrorMapper} before the detail is set.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://mediconnect.ro/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handle(ResourceNotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource Not Found",
                ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handle(BusinessException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violation",
                "Business Rule Violation", ex.getMessage(), req);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ProblemDetail> handle(OptimisticLockException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification",
                "Concurrent Modification",
                "The resource was modified concurrently. Please retry the operation.", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handle(AccessDeniedException ex, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to perform this action.", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Error", message, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAll(Exception ex, HttpServletRequest req) {
        // Stripe errors are already wrapped in BusinessException by StripePaymentService
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal Server Error",
                "An unexpected error occurred. Please contact support with the traceId.", req);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<ProblemDetail> problem(HttpStatus status,
                                                   String type,
                                                   String title,
                                                   String detail,
                                                   HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(PROBLEM_BASE + type));
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("traceId", UUID.randomUUID().toString());
        pd.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
