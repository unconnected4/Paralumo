package com.paralumo.service;

/** The requested thing doesn't exist (for this user). Answered with 404 and the message. */
public class NotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(message);
  }
}
