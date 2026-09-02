package br.com.fiap.hackaton.video.domain.video.gateway;

public class EventPublishException extends RuntimeException {

  public EventPublishException(String message) {
    super(message);
  }

  public EventPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
