package br.com.fiap.hackaton.video.infrastructure.security;

import br.com.fiap.hackaton.security.commons.CurrentUserId;
import br.com.fiap.hackaton.security.commons.SubjectIsUuidValidator;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@TestConfiguration
public class SecurityTestConfig {

  @Bean
  public JwtDecoder testJwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(JwtFixture.publicKey()).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(JwtFixture.ISSUER),
            new SubjectIsUuidValidator()));
    return decoder;
  }

  @RestController
  static class ProtectedProbeController {

    @GetMapping("/test/whoami")
    String whoami(@CurrentUserId UUID userId) {
      return userId.toString();
    }
  }
}
