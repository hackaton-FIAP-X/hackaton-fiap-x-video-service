package br.com.fiap.hackaton.video.application.video.dto;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record VideoResponse(
    UUID videoId,
    String originalFilename,
    VideoStatus status,
    Integer frameCount,
    String errorMessage,
    boolean downloadAvailable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static VideoResponse fromEntity(Video video) {
    return new VideoResponse(
        video.getId(),
        video.getOriginalFilename(),
        video.getStatus(),
        video.getFrameCount(),
        video.getErrorMessage(),
        video.isDownloadable(),
        video.getCreatedAt(),
        video.getUpdatedAt());
  }
}
