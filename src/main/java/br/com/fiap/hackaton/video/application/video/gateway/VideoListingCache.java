package br.com.fiap.hackaton.video.application.video.gateway;

import br.com.fiap.hackaton.video.application.video.dto.VideoPageResponse;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.Optional;
import java.util.UUID;

public interface VideoListingCache {

  Optional<VideoPageResponse> find(UUID userId, VideoStatus status, int page, int size);

  void store(UUID userId, VideoStatus status, int page, int size, VideoPageResponse response);

  void invalidate(UUID userId);
}
