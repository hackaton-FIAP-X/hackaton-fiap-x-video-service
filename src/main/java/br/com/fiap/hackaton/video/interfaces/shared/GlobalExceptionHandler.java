package br.com.fiap.hackaton.video.interfaces.shared;

import br.com.fiap.hackaton.video.application.shared.exception.BusinessException;
import br.com.fiap.hackaton.video.application.shared.exception.ConflictException;
import br.com.fiap.hackaton.video.application.shared.exception.ResourceNotFoundException;
import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler({DomainException.class, BusinessException.class})
  public ProblemDetail handleInvalidRequest(
      RuntimeException exception, HttpServletRequest request) {
    log.warn("Requisicao recusada: {}", exception.getMessage());
    return problem(
        HttpStatus.BAD_REQUEST,
        ProblemTypes.INVALID_REQUEST,
        "Requisicao invalida",
        exception.getMessage(),
        request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleInvalidParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    String detail = "Valor invalido para o parametro " + exception.getName();
    log.warn("Requisicao recusada: {}", detail);
    return problem(
        HttpStatus.BAD_REQUEST,
        ProblemTypes.INVALID_REQUEST,
        "Parametro invalido",
        detail,
        request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handleMissingParameter(
      MissingServletRequestParameterException exception, HttpServletRequest request) {
    String detail = "Parametro obrigatorio ausente: " + exception.getParameterName();
    log.warn("Requisicao recusada: {}", detail);
    return problem(
        HttpStatus.BAD_REQUEST, ProblemTypes.INVALID_REQUEST, "Parametro ausente", detail, request);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail handleNotFound(
      ResourceNotFoundException exception, HttpServletRequest request) {
    log.warn("Recurso nao encontrado: {}", exception.getMessage());
    return problem(
        HttpStatus.NOT_FOUND,
        ProblemTypes.RESOURCE_NOT_FOUND,
        "Recurso nao encontrado",
        exception.getMessage(),
        request);
  }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException exception, HttpServletRequest request) {
    log.warn("Requisicao em conflito com o estado atual: {}", exception.getMessage());
    return problem(
        HttpStatus.CONFLICT,
        ProblemTypes.VIDEO_NOT_READY,
        "Video ainda nao disponivel",
        exception.getMessage(),
        request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleUploadTooLarge(
      MaxUploadSizeExceededException exception, HttpServletRequest request) {
    log.warn("Upload recusado por tamanho: {}", exception.getMessage());
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE,
        ProblemTypes.PAYLOAD_TOO_LARGE,
        "Arquivo grande demais",
        "Arquivo de video excede o tamanho maximo aceito",
        request);
  }

  @ExceptionHandler(VideoStorageException.class)
  public ProblemDetail handleStorageFailure(
      VideoStorageException exception, HttpServletRequest request) {
    log.error("Falha no storage de videos", exception);
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        ProblemTypes.STORAGE_UNAVAILABLE,
        "Storage indisponivel",
        "Storage de videos indisponivel. Tente novamente em instantes.",
        request);
  }

  static ProblemDetail problem(
      HttpStatus status, URI type, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type);
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }
}
