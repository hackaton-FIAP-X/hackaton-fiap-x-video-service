package br.com.fiap.hackaton.video.infrastructure.notification;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailureNotification;
import br.com.fiap.hackaton.video.application.video.gateway.FailureNotifier;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Envia o aviso de falha por SMTP (Mailhog no ambiente local). */
public class SmtpFailureNotifier implements FailureNotifier {

  private final JavaMailSender mailSender;
  private final String from;

  public SmtpFailureNotifier(JavaMailSender mailSender, String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public void notifyFailure(VideoFailureNotification notification) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(notification.ownerEmail());
    message.setSubject(
        "FIAP X: nao conseguimos processar o video %s".formatted(notification.originalFilename()));
    message.setText(body(notification));
    mailSender.send(message);
  }

  static String body(VideoFailureNotification notification) {
    return """
        Ola,

        O processamento do video "%s" falhou.

        Motivo: %s

        Voce pode enviar o video de novo pela API (POST /videos). Se o arquivo estiver
        corrompido ou num formato que nao e video, exporte-o novamente antes de reenviar.

        Identificador do video: %s
        Rastreio: %s

        -- FIAP X
        """
        .formatted(
            notification.originalFilename(),
            notification.reason(),
            notification.videoId(),
            notification.traceId() == null ? "-" : notification.traceId());
  }
}
