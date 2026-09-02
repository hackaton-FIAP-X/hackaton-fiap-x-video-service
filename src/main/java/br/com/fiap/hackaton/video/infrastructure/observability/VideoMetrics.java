package br.com.fiap.hackaton.video.infrastructure.observability;

import br.com.fiap.hackaton.video.domain.outbox.repository.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class VideoMetrics {

  public VideoMetrics(MeterRegistry registry, OutboxRepository outboxRepository) {
    Gauge.builder("fiapx.outbox.pending", outboxRepository, OutboxMetrics::countPendingQuietly)
        .description("Eventos gravados no outbox que o broker ainda nao confirmou")
        .register(registry);
  }

  private static final class OutboxMetrics {

    private static double countPendingQuietly(OutboxRepository repository) {
      try {
        return repository.countPending();
      } catch (RuntimeException databaseUnavailable) {
        return Double.NaN;
      }
    }
  }
}
