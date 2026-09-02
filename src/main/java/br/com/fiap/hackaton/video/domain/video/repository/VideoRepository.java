package br.com.fiap.hackaton.video.domain.video.repository;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findByIdAndUserId(UUID id, UUID userId);
}
