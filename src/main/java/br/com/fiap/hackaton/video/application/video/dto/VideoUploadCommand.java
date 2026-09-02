package br.com.fiap.hackaton.video.application.video.dto;

import java.io.InputStream;

public record VideoUploadCommand(String originalFilename, long sizeInBytes, InputStream content) {}
