package br.com.fiap.hackaton.video.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class SubjectIsUuidValidatorTest {

  private final SubjectIsUuidValidator validator = new SubjectIsUuidValidator();

  private Jwt tokenWithSubject(String subject) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))
            .claims(claims -> claims.putAll(Map.of("iss", "fiapx-auth")));

    if (subject != null) {
      builder.subject(subject);
    }
    return builder.build();
  }

  @Test
  @DisplayName("Deve aceitar sub em formato UUID")
  void deveAceitarUuid() {
    OAuth2TokenValidatorResult result =
        validator.validate(tokenWithSubject(UUID.randomUUID().toString()));

    assertThat(result.hasErrors()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"admin", "1", "3f2504e0-4f89-11d3-9a0c", "   "})
  @DisplayName("Deve recusar sub que nao seja UUID")
  void deveRecusarSubInvalido(String subject) {
    OAuth2TokenValidatorResult result = validator.validate(tokenWithSubject(subject));

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).anyMatch(error -> error.getErrorCode().equals("invalid_token"));
  }

  @Test
  @DisplayName("Deve recusar token sem sub")
  void deveRecusarTokenSemSub() {
    OAuth2TokenValidatorResult result = validator.validate(tokenWithSubject(null));

    assertThat(result.hasErrors()).isTrue();
  }
}
