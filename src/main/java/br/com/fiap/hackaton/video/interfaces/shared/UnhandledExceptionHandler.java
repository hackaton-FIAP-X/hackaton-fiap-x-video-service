package br.com.fiap.hackaton.video.interfaces.shared;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class UnhandledExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Falha nao tratada em {}", request.getRequestURI(), exception);
    return GlobalExceptionHandler.problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ProblemTypes.INTERNAL_ERROR,
        "Erro interno",
        "Nao foi possivel concluir a requisicao.",
        request);
  }
}
