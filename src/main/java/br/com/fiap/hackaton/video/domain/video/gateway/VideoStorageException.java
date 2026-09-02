package br.com.fiap.hackaton.video.domain.video.gateway;

public class VideoStorageException extends RuntimeException {

  public VideoStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
