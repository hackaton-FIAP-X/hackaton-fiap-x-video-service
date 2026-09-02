package br.com.fiap.hackaton.video.infrastructure.persistence.video;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoRepositoryImpl implements VideoRepository {

  private final VideoJpaRepository jpaRepository;

  @Override
  public Video save(Video video) {
    return jpaRepository.save(video);
  }

  @Override
  public Optional<Video> findById(UUID id) {
    return jpaRepository.findById(id);
  }

  @Override
  public Optional<Video> findByIdForUpdate(UUID id) {
    return jpaRepository.findByIdForUpdate(id);
  }

  @Override
  public Optional<Video> findByIdAndUserId(UUID id, UUID userId) {
    return jpaRepository.findByIdAndUserId(id, userId);
  }

  @Override
  public Page<Video> findAllByOwner(UUID userId, VideoStatus status, Pageable pageable) {
    return status == null
        ? jpaRepository.findByUserId(userId, pageable)
        : jpaRepository.findByUserIdAndStatus(userId, status, pageable);
  }
}
