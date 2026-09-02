package br.com.fiap.hackaton.video.infrastructure.security;

import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@TestConfiguration
public class SecurityTestConfig {

  @Bean
  @Primary
  public JwtDecoder testJwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(JwtFixture.publicKey()).build();
    decoder.setJwtValidator(SecurityConfig.tokenValidator(JwtFixture.ISSUER));
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
