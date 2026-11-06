package com.paralumo.api;

/** Answered with 401 and the message. */
class UnauthorizedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  UnauthorizedException(String message) {
    super(message);
  }
}
