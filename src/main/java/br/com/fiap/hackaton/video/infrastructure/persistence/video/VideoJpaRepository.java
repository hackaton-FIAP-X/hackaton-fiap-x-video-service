package br.com.fiap.hackaton.video.infrastructure.persistence.video;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoJpaRepository extends JpaRepository<Video, UUID> {

  Optional<Video> findByIdAndUserId(UUID id, UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM Video v WHERE v.id = :id")
  Optional<Video> findByIdForUpdate(@Param("id") UUID id);

  Page<Video> findByUserId(UUID userId, Pageable pageable);

  Page<Video> findByUserIdAndStatus(UUID userId, VideoStatus status, Pageable pageable);
}
