package br.com.fiap.hackaton.video.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String jwksUri, String issuer) {}
