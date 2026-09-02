package br.com.fiap.hackaton.video.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

  private static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/info",
    "/actuator/prometheus",
    "/api-docs",
    "/api-docs/**",
    "/swagger-ui.html",
    "/swagger-ui/**"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtDecoder jwtDecoder, ProblemDetailSecurityResponder problemResponder)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.decoder(jwtDecoder))
                    .authenticationEntryPoint(problemResponder)
                    .accessDeniedHandler(problemResponder))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(problemResponder)
                    .accessDeniedHandler(problemResponder))
        .build();
  }

  @Bean
  public JwtDecoder jwtDecoder(JwtProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
    decoder.setJwtValidator(tokenValidator(properties.issuer()));
    return decoder;
  }

  public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer) {
    OAuth2TokenValidator<Jwt> defaults =
        StringUtils.hasText(issuer)
            ? JwtValidators.createDefaultWithIssuer(issuer)
            : JwtValidators.createDefault();
    return new DelegatingOAuth2TokenValidator<>(defaults, new SubjectIsUuidValidator());
  }
}
