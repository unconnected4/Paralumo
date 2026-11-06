package com.paralumo.config;

import com.paralumo.persistence.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless security: a session token (see {@link SessionTokens}) authenticates each request, and
 * our own JSON auth endpoints replace Spring's form login, because the Step 0 contract needs JSON
 * bodies, status codes and {@code {detail}} errors rather than form posts and redirects.
 *
 * <p>CSRF tokens are off. The session cookie is SameSite=Lax, so browsers don't attach it to
 * cross-site POST or DELETE requests, and every state-changing endpoint takes JSON or no body.
 * Revisit if the API is ever served same-site with an untrusted origin (e.g. a sibling subdomain).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, SessionTokens tokens, CorsProperties cors, JsonMapper json) {
    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new SessionTokenFilter(tokens), AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/cards", "/api/cards/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (request, response, ex) ->
                            writeProblem(
                                response, json, HttpStatus.UNAUTHORIZED, "Authentication required"))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            writeProblem(response, json, HttpStatus.FORBIDDEN, "Forbidden")));
    if (!cors.origins().isEmpty()) {
      http.cors(Customizer.withDefaults());
    }
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(cors.origins());
    config.addAllowedMethod("*");
    config.addAllowedHeader("*");
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  /** Loads accounts by (normalized) email for password checks. */
  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return email ->
        users
            .findByEmail(email)
            .map(
                user -> User.withUsername(user.getEmail()).password(user.getPasswordHash()).build())
            .orElseThrow(() -> new UsernameNotFoundException("Unknown email"));
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  private static void writeProblem(
      HttpServletResponse response, JsonMapper json, HttpStatus status, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
  }
}
