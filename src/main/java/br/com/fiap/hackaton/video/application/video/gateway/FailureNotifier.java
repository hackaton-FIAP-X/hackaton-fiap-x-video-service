package br.com.fiap.hackaton.video.application.video.gateway;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailureNotification;

/** Canal de aviso ao usuario quando o processamento do video falha (PLT-8). */
public interface FailureNotifier {

  void notifyFailure(VideoFailureNotification notification);
}
