package com.paralumo.service;

/** The request clashes with existing data. Answered with 409 and the message. */
public class ConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String message) {
    super(message);
  }
}
