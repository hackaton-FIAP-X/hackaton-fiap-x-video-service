package br.com.fiap.hackaton.video.application.video.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoProcessedEvent(
    UUID videoId,
    UUID userId,
    String zipKey,
    Integer frameCount,
    LocalDateTime finishedAt,
    String traceId) {

  public static final String EVENT_TYPE = "video.processed";
}
