package br.com.fiap.hackaton.video.application.video.dto;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import java.time.LocalDateTime;
import java.util.UUID;

public record VideoUploadedEvent(
    UUID videoId,
    UUID userId,
    String storageKey,
    String originalFilename,
    int fps,
    LocalDateTime requestedAt,
    String traceId) {

  public static final String EVENT_TYPE = "video.uploaded";

  private static final int DEFAULT_FPS = 1;

  public static VideoUploadedEvent fromEntity(Video video, String traceId) {
    return new VideoUploadedEvent(
        video.getId(),
        video.getUserId(),
        video.getStorageKey(),
        video.getOriginalFilename(),
        DEFAULT_FPS,
        video.getCreatedAt(),
        traceId);
  }
}
