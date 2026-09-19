package br.com.fiap.hackaton.video.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailureNotification;
import br.com.fiap.hackaton.video.application.video.gateway.FailureNotifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class FailureNotificationTest {

  private final VideoFailureNotification aviso =
      new VideoFailureNotification(
          UUID.fromString("22222222-2222-2222-2222-222222222222"),
          "dono@fiapx.com.br",
          "aula.mp4",
          "[INVALID_VIDEO] moov atom not found",
          "trace-9");

  @Test
  @DisplayName("SMTP: envia para o dono com o nome do arquivo e o motivo")
  void smtpEnviaParaODono() {
    JavaMailSender sender = mock(JavaMailSender.class);

    new SmtpFailureNotifier(sender, "FIAP X <no-reply@fiapx.local>").notifyFailure(aviso);

    ArgumentCaptor<SimpleMailMessage> mensagem = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(mensagem.capture());
    assertThat(mensagem.getValue().getTo()).containsExactly("dono@fiapx.com.br");
    assertThat(mensagem.getValue().getFrom()).isEqualTo("FIAP X <no-reply@fiapx.local>");
    assertThat(mensagem.getValue().getSubject()).contains("aula.mp4");
    assertThat(mensagem.getValue().getText())
        .contains("aula.mp4", "[INVALID_VIDEO] moov atom not found", "trace-9")
        .contains("22222222-2222-2222-2222-222222222222");
  }

  @Test
  @DisplayName("SMTP: corpo aceita trace ausente")
  void corpoSemTrace() {
    var semTrace = new VideoFailureNotification(aviso.videoId(), "a@b.c", "x.mp4", "motivo", null);

    assertThat(SmtpFailureNotifier.body(semTrace)).contains("Rastreio: -");
  }

  @Test
  @DisplayName("Listener: entrega o aviso e conta como enviado")
  void listenerEntrega() {
    FailureNotifier notifier = mock(FailureNotifier.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    new FailureNotificationListener(notifier, registry).onVideoFailed(aviso);

    verify(notifier).notifyFailure(aviso);
    assertThat(registry.counter("fiapx.notifications", "result", "sent").count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Listener: SMTP fora do ar vira metrica e log, sem propagar erro")
  void listenerNaoPropagaFalhaDoSmtp() {
    FailureNotifier notifier = mock(FailureNotifier.class);
    doThrow(new MailSendException("Connection refused")).when(notifier).notifyFailure(any());
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    new FailureNotificationListener(notifier, registry).onVideoFailed(aviso);

    assertThat(registry.counter("fiapx.notifications", "result", "failed").count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Config: SMTP so quando habilitado e com servidor; senao, log")
  @SuppressWarnings("unchecked")
  void configEscolheOCanal() {
    NotificationConfig config = new NotificationConfig();
    ObjectProvider<JavaMailSender> comSender = mock(ObjectProvider.class);
    org.mockito.Mockito.when(comSender.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
    ObjectProvider<JavaMailSender> semSender = mock(ObjectProvider.class);

    assertThat(config.failureNotifier(new NotificationProperties(true, "f"), comSender))
        .isInstanceOf(SmtpFailureNotifier.class);
    assertThat(config.failureNotifier(new NotificationProperties(false, "f"), comSender))
        .isInstanceOf(LoggingFailureNotifier.class);
    assertThat(config.failureNotifier(new NotificationProperties(true, "f"), semSender))
        .isInstanceOf(LoggingFailureNotifier.class);

    new LoggingFailureNotifier().notifyFailure(aviso);
  }
}
