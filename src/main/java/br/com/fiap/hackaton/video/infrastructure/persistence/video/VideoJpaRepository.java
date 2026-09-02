package br.com.fiap.hackaton.video.infrastructure.persistence.video;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoJpaRepository extends JpaRepository<Video, UUID> {

  Optional<Video> findByIdAndUserId(UUID id, UUID userId);
}
