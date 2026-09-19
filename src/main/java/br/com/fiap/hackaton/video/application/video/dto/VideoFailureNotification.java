package br.com.fiap.hackaton.video.application.video.dto;

import java.util.UUID;

/**
 * Aviso para o dono de um video cujo processamento falhou (PLT-8). Publicado como evento de
 * aplicacao pelo {@code VideoStatusUpdateService} e entregue so depois do commit.
 */
public record VideoFailureNotification(
    UUID videoId, String ownerEmail, String originalFilename, String reason, String traceId) {}
