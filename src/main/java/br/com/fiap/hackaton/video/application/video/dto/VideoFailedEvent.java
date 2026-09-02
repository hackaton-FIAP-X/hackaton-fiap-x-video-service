package br.com.fiap.hackaton.video.application.video.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoFailedEvent(
    UUID videoId,
    UUID userId,
    String errorCode,
    String errorMessage,
    Integer attempts,
    String traceId) {

  public static final String EVENT_TYPE = "video.failed";
}
