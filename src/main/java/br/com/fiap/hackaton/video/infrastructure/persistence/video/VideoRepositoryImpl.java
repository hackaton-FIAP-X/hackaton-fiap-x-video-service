package br.com.fiap.hackaton.video.infrastructure.persistence.video;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
  public Optional<Video> findByIdAndUserId(UUID id, UUID userId) {
    return jpaRepository.findByIdAndUserId(id, userId);
  }
}
