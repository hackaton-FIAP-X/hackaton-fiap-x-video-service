package br.com.fiap.hackaton.video.domain.video.repository;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findById(UUID id);

  Optional<Video> findByIdForUpdate(UUID id);

  Optional<Video> findByIdAndUserId(UUID id, UUID userId);

  Page<Video> findAllByOwner(UUID userId, VideoStatus status, Pageable pageable);
}
