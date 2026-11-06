package com.paralumo.config;

import java.util.UUID;

/**
 * The principal of an authenticated request, taken from a valid session token. Controllers get it
 * with {@code @AuthenticationPrincipal}.
 */
public record CurrentUser(UUID id) {}
