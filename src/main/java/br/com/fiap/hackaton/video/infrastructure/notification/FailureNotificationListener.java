package br.com.fiap.hackaton.video.infrastructure.notification;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailureNotification;
import br.com.fiap.hackaton.video.application.video.gateway.FailureNotifier;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega o aviso de falha depois do commit (PLT-8).
 *
 * <p>AFTER_COMMIT: o e-mail so sai se o status FAILED foi gravado. E o envio nunca propaga erro: um
 * SMTP fora do ar vira log e metrica, sem desfazer o status nem devolver a mensagem para a fila.
 */
@Slf4j
@Component
public class FailureNotificationListener {

  private final FailureNotifier notifier;
  private final MeterRegistry registry;

  public FailureNotificationListener(FailureNotifier notifier, MeterRegistry registry) {
    this.notifier = notifier;
    this.registry = registry;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onVideoFailed(VideoFailureNotification notification) {
    try {
      notifier.notifyFailure(notification);
      registry.counter("fiapx.notifications", "result", "sent").increment();
      log.info(
          "Aviso de falha do video {} enviado [trace={}]",
          notification.videoId(),
          notification.traceId());
    } catch (RuntimeException e) {
      registry.counter("fiapx.notifications", "result", "failed").increment();
      log.warn(
          "Aviso de falha do video {} nao pode ser enviado: {} [trace={}]",
          notification.videoId(),
          e.getMessage(),
          notification.traceId());
    }
  }
}
