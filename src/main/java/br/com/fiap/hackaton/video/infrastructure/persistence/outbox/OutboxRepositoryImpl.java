package br.com.fiap.hackaton.video.infrastructure.persistence.outbox;

import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;
import br.com.fiap.hackaton.video.domain.outbox.repository.OutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

  private final OutboxJpaRepository jpaRepository;

  @Override
  public OutboxEvent save(OutboxEvent event) {
    return jpaRepository.save(event);
  }

  @Override
  public List<OutboxEvent> lockPendingBatch(int batchSize) {
    return jpaRepository.lockPending(PageRequest.ofSize(batchSize));
  }

  @Override
  public long countPending() {
    return jpaRepository.countByPublishedAtIsNull();
  }
}
