package br.com.fiap.hackaton.video.infrastructure.messaging;

import br.com.fiap.hackaton.video.application.outbox.service.OutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "video.outbox.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisherScheduler {

  private final OutboxDispatcher outboxDispatcher;

  @Scheduled(
      fixedDelayString = "${video.outbox.scheduler.interval-ms:5000}",
      initialDelayString = "${video.outbox.scheduler.initial-delay-ms:10000}")
  public void publishPendingEvents() {
    try {
      outboxDispatcher.dispatchPending();
    } catch (RuntimeException e) {
      log.error("Ciclo do dispatcher de outbox falhou", e);
    }
  }
}
