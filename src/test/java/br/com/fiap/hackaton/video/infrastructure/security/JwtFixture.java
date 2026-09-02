package br.com.fiap.hackaton.video.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

public final class JwtFixture {

  public static final String ISSUER = "fiapx-auth";

  private static final RSAKey AUTH_SERVICE_KEY = generateKey();
  private static final RSAKey INTRUDER_KEY = generateKey();

  private JwtFixture() {}

  public static RSAPublicKey publicKey() {
    try {
      return AUTH_SERVICE_KEY.toRSAPublicKey();
    } catch (JOSEException e) {
      throw new IllegalStateException("Nao foi possivel expor a chave publica de teste", e);
    }
  }

  public static String validToken(UUID userId) {
    return token(
        AUTH_SERVICE_KEY, userId.toString(), ISSUER, Instant.now().plus(15, ChronoUnit.MINUTES));
  }

  public static String tokenWithSubject(String subject) {
    return token(AUTH_SERVICE_KEY, subject, ISSUER, Instant.now().plus(15, ChronoUnit.MINUTES));
  }

  public static String tokenFromAnotherIssuer(UUID userId) {
    return token(
        AUTH_SERVICE_KEY, userId.toString(), "intruso", Instant.now().plus(15, ChronoUnit.MINUTES));
  }

  public static String expiredToken(UUID userId) {
    return token(
        AUTH_SERVICE_KEY, userId.toString(), ISSUER, Instant.now().minus(1, ChronoUnit.MINUTES));
  }

  public static String tokenSignedByIntruder(UUID userId) {
    return token(
        INTRUDER_KEY, userId.toString(), ISSUER, Instant.now().plus(15, ChronoUnit.MINUTES));
  }

  private static String token(RSAKey key, String subject, String issuer, Instant expiresAt) {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(issuer)
            .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
            .expirationTime(Date.from(expiresAt))
            .claim("email", "dev@fiapx.com.br")
            .claim("name", "Dev FIAP X")
            .build();

    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    try {
      jwt.sign(new RSASSASigner(key));
    } catch (JOSEException e) {
      throw new IllegalStateException("Nao foi possivel assinar o token de teste", e);
    }
    return jwt.serialize();
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Nao foi possivel gerar o par de chaves de teste", e);
    }
  }
}
