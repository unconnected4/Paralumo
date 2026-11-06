package com.paralumo.api;

import com.paralumo.service.ConflictException;
import com.paralumo.service.NotFoundException;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is an RFC 9457 problem body; its {@code detail} is the message the web client shows.
 * Invalid request bodies are 422, as the Step 0 contract expects, rather than Spring's 400.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler
  ProblemDetail notFound(NotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail conflict(ConflictException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unauthorized(UnauthorizedException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .distinct()
            .sorted()
            .collect(Collectors.joining("; "));
    return problem(ex, HttpStatus.UNPROCESSABLE_CONTENT, detail, headers, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problem(
        ex,
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Request body is missing or not valid JSON",
        headers,
        request);
  }

  private @Nullable ResponseEntity<Object> problem(
      Exception ex, HttpStatus status, String detail, HttpHeaders headers, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    return handleExceptionInternal(ex, body, headers, status, request);
  }
}
