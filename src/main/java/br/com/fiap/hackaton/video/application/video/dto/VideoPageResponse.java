package br.com.fiap.hackaton.video.application.video.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record VideoPageResponse(
    List<VideoResponse> content, long totalElements, int totalPages, int page, int size) {

  public static VideoPageResponse fromPage(Page<VideoResponse> page) {
    return new VideoPageResponse(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }
}
