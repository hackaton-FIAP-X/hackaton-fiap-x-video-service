package br.com.fiap.hackaton.video.infrastructure.notification;

import br.com.fiap.hackaton.video.application.video.gateway.FailureNotifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

  /** SMTP quando o aviso esta ligado e ha servidor de e-mail configurado; senao, so log. */
  @Bean
  public FailureNotifier failureNotifier(
      NotificationProperties properties, ObjectProvider<JavaMailSender> mailSender) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (properties.enabled() && sender != null) {
      return new SmtpFailureNotifier(sender, properties.from());
    }
    return new LoggingFailureNotifier();
  }
}
