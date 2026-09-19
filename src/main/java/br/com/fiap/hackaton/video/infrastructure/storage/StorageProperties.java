package br.com.fiap.hackaton.video.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
    String endpoint,
    String publicEndpoint,
    String accessKey,
    String secretKey,
    String bucket,
    String region) {

  public String publicEndpointOrDefault() {
    return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
  }

  /**
   * Chaves fixas so quando as duas vierem preenchidas (MinIO no ambiente local). Sem elas, as
   * credenciais saem da cadeia padrao da AWS: variaveis de ambiente, perfil ou o papel da maquina
   * (LabRole dos nos do EKS no AWS Academy Learner Lab, cujas credenciais sao temporarias e nao
   * cabem numa chave fixa).
   */
  public boolean hasStaticCredentials() {
    return hasText(accessKey) && hasText(secretKey);
  }

  static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
