package br.com.fiap.hackaton.video.application.video.dto;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.UUID;

public record UploadVideoResponse(UUID videoId, VideoStatus status) {

  public static UploadVideoResponse fromEntity(Video video) {
    return new UploadVideoResponse(video.getId(), video.getStatus());
  }
}
