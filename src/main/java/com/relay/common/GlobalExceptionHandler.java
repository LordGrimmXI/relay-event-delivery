package com.relay.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.relay.destination.DestinationAlreadyExistsException;
import com.relay.destination.DestinationNotFoundException;
import com.relay.destination.DuplicateSubscriptionException;
import com.relay.destination.InvalidDestinationUrlException;
import com.relay.event.EventNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("Validation failed", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("Malformed JSON request", List.of()));
    }

    @ExceptionHandler(InvalidDestinationUrlException.class)
    public ResponseEntity<ApiError> handleInvalidDestinationUrl(InvalidDestinationUrlException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(DestinationAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleDuplicateDestination(DestinationAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(DestinationNotFoundException.class)
    public ResponseEntity<ApiError> handleDestinationNotFound(DestinationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(DuplicateSubscriptionException.class)
    public ResponseEntity<ApiError> handleDuplicateSubscription(DuplicateSubscriptionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> handleEventNotFound(EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // Spring's own client errors (405, 404, 415, ...) carry their correct status.
        if (ex instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(new ApiError(detail != null ? detail : "Request failed", List.of()));
        }

        // Everything else is our fault: log the real cause, send a generic message.
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Internal server error", List.of()));
    }
}