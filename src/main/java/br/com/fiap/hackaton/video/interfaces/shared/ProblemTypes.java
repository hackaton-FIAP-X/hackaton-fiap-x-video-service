package br.com.fiap.hackaton.video.interfaces.shared;

import java.net.URI;

public final class ProblemTypes {

  private static final String BASE = "https://fiapx.com.br/problems/";

  public static final URI INVALID_REQUEST = URI.create(BASE + "invalid-request");
  public static final URI RESOURCE_NOT_FOUND = URI.create(BASE + "resource-not-found");
  public static final URI VIDEO_NOT_READY = URI.create(BASE + "video-not-ready");
  public static final URI PAYLOAD_TOO_LARGE = URI.create(BASE + "payload-too-large");
  public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");
  public static final URI FORBIDDEN = URI.create(BASE + "forbidden");
  public static final URI STORAGE_UNAVAILABLE = URI.create(BASE + "storage-unavailable");
  public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

  private ProblemTypes() {}
}
