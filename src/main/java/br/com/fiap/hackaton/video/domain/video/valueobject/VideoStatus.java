package br.com.fiap.hackaton.video.domain.video.valueobject;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum VideoStatus {
  RECEIVED,
  QUEUED,
  PROCESSING,
  COMPLETED,
  FAILED;

  private static final Map<VideoStatus, Set<VideoStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          RECEIVED, EnumSet.of(QUEUED, FAILED),
          QUEUED, EnumSet.of(PROCESSING, FAILED),
          PROCESSING, EnumSet.of(COMPLETED, FAILED),
          COMPLETED, EnumSet.noneOf(VideoStatus.class),
          FAILED, EnumSet.noneOf(VideoStatus.class));

  public boolean isFinal() {
    return ALLOWED_TRANSITIONS.get(this).isEmpty();
  }

  public boolean allowsTransitionTo(VideoStatus target) {
    return target != null && ALLOWED_TRANSITIONS.get(this).contains(target);
  }
}
