package com.paralumo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from its session token (bearer header first, then cookie). A missing or
 * invalid token leaves the request anonymous rather than rejecting it: public endpoints such as
 * lookup and login must keep working with a stale cookie, and protected ones answer 401 anyway.
 */
class SessionTokenFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final SessionTokens tokens;

  SessionTokenFilter(SessionTokens tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null) {
      tokens
          .verify(token)
          .ifPresent(
              userId ->
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          UsernamePasswordAuthenticationToken.authenticated(
                              new CurrentUser(userId), null, List.of())));
    }
    chain.doFilter(request, response);
  }

  private static @Nullable String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (SessionTokens.COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }
}
