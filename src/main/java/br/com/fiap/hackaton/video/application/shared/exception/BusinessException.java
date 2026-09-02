package br.com.fiap.hackaton.video.application.shared.exception;

public class BusinessException extends RuntimeException {

  public BusinessException(String message) {
    super(message);
  }
}
