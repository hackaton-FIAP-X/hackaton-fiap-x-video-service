package br.com.fiap.hackaton.video.infrastructure.notification;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailureNotification;
import br.com.fiap.hackaton.video.application.video.gateway.FailureNotifier;
import lombok.extern.slf4j.Slf4j;

/** Usado quando o aviso por e-mail esta desligado: so registra no log. */
@Slf4j
public class LoggingFailureNotifier implements FailureNotifier {

  @Override
  public void notifyFailure(VideoFailureNotification notification) {
    log.info(
        "Aviso de falha do video {} nao enviado (notification.email.enabled=false) [trace={}]",
        notification.videoId(),
        notification.traceId());
  }
}
