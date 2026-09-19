package br.com.fiap.hackaton.video.infrastructure.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Aviso por e-mail quando o processamento falha ({@code notification.email.*}, PLT-8). */
@ConfigurationProperties(prefix = "notification.email")
public record NotificationProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("FIAP X <no-reply@fiapx.local>") String from) {}
