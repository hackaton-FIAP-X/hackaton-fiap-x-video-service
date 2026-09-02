package br.com.fiap.hackaton.video.interfaces.shared;

import br.com.fiap.hackaton.video.application.shared.exception.BusinessException;
import br.com.fiap.hackaton.video.application.shared.exception.ResourceNotFoundException;
import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  public record ErrorResponse(int status, String error, String message, LocalDateTime timestamp) {

    static ErrorResponse of(HttpStatus status, String message) {
      return new ErrorResponse(
          status.value(), status.getReasonPhrase(), message, LocalDateTime.now());
    }
  }

  @ExceptionHandler({DomainException.class, BusinessException.class})
  public ResponseEntity<ErrorResponse> handleInvalidRequest(RuntimeException exception) {
    log.warn("Requisicao recusada: {}", exception.getMessage());
    return ResponseEntity.badRequest()
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, exception.getMessage()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
    log.warn("Recurso nao encontrado: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(HttpStatus.NOT_FOUND, exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleInvalidParameter(
      MethodArgumentTypeMismatchException exception) {
    String message = "Valor invalido para o parametro " + exception.getName();
    log.warn("Requisicao recusada: {}", message);
    return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException exception) {
    String message = "Parametro obrigatorio ausente: " + exception.getParameterName();
    log.warn("Requisicao recusada: {}", message);
    return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleUploadTooLarge(
      MaxUploadSizeExceededException exception) {
    log.warn("Upload recusado por tamanho: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(
            ErrorResponse.of(
                HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo de video excede o tamanho maximo aceito"));
  }

  @ExceptionHandler(VideoStorageException.class)
  public ResponseEntity<ErrorResponse> handleStorageFailure(VideoStorageException exception) {
    log.error("Falha no storage de videos", exception);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Storage de videos indisponivel. Tente novamente em instantes."));
  }
}
