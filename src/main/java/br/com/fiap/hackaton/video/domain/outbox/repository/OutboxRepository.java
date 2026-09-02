package br.com.fiap.hackaton.video.domain.outbox.repository;

import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;
import java.util.List;

public interface OutboxRepository {

  OutboxEvent save(OutboxEvent event);

  List<OutboxEvent> lockPendingBatch(int batchSize);

  long countPending();
}
