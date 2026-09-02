package br.com.fiap.hackaton.video.application.video.service;

import br.com.fiap.hackaton.video.application.shared.exception.ResourceNotFoundException;
import br.com.fiap.hackaton.video.application.video.dto.VideoPageResponse;
import br.com.fiap.hackaton.video.application.video.dto.VideoResponse;
import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoQueryService {

  static final int MAX_PAGE_SIZE = 100;
  static final int DEFAULT_PAGE_SIZE = 20;

  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

  private final VideoRepository videoRepository;
  private final VideoListingCache listingCache;

  @Transactional(readOnly = true)
  public VideoPageResponse listOwnedBy(UUID userId, VideoStatus status, int page, int size) {
    int safePage = sanitizePage(page);
    int safeSize = sanitizeSize(size);

    return listingCache
        .find(userId, status, safePage, safeSize)
        .orElseGet(() -> queryAndCache(userId, status, safePage, safeSize));
  }

  private VideoPageResponse queryAndCache(UUID userId, VideoStatus status, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);

    VideoPageResponse response =
        VideoPageResponse.fromPage(
            videoRepository
                .findAllByOwner(userId, status, pageable)
                .map(VideoResponse::fromEntity));

    listingCache.store(userId, status, page, size, response);
    return response;
  }

  @Transactional(readOnly = true)
  public VideoResponse findOwnedBy(UUID userId, UUID videoId) {
    return VideoResponse.fromEntity(requireOwnedVideo(userId, videoId));
  }

  Video requireOwnedVideo(UUID userId, UUID videoId) {
    return videoRepository
        .findByIdAndUserId(videoId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Video nao encontrado: " + videoId));
  }

  private int sanitizePage(int page) {
    return Math.max(page, 0);
  }

  private int sanitizeSize(int size) {
    if (size <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }
}
