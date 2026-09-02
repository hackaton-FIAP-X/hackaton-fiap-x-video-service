package br.com.fiap.hackaton.video.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@Import(SecurityTestConfig.class)
class JwtResourceServerTest {

  private static final UUID USER_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
  private static final String PROTECTED_ROUTE = "/test/whoami";

  @Autowired private MockMvc mockMvc;

  private String bearer(String token) {
    return "Bearer " + token;
  }

  @Test
  @DisplayName("Deve recusar requisicao sem token com 401")
  void deveRecusarRequisicaoSemToken() throws Exception {
    mockMvc.perform(get(PROTECTED_ROUTE)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Deve aceitar token valido e resolver o userId a partir do claim sub")
  void deveResolverUserIdDoClaimSub() throws Exception {
    mockMvc
        .perform(
            get(PROTECTED_ROUTE)
                .header(HttpHeaders.AUTHORIZATION, bearer(JwtFixture.validToken(USER_ID))))
        .andExpect(status().isOk())
        .andExpect(content().string(USER_ID.toString()));
  }

  @Test
  @DisplayName("Deve recusar token assinado por outra chave")
  void deveRecusarTokenDeOutraChave() throws Exception {
    mockMvc
        .perform(
            get(PROTECTED_ROUTE)
                .header(
                    HttpHeaders.AUTHORIZATION, bearer(JwtFixture.tokenSignedByIntruder(USER_ID))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Deve recusar token expirado")
  void deveRecusarTokenExpirado() throws Exception {
    mockMvc
        .perform(
            get(PROTECTED_ROUTE)
                .header(HttpHeaders.AUTHORIZATION, bearer(JwtFixture.expiredToken(USER_ID))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Deve recusar token de outro emissor")
  void deveRecusarTokenDeOutroEmissor() throws Exception {
    mockMvc
        .perform(
            get(PROTECTED_ROUTE)
                .header(
                    HttpHeaders.AUTHORIZATION, bearer(JwtFixture.tokenFromAnotherIssuer(USER_ID))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Deve recusar token cujo sub nao e um UUID")
  void deveRecusarSubQueNaoEhUuid() throws Exception {
    mockMvc
        .perform(
            get(PROTECTED_ROUTE)
                .header(HttpHeaders.AUTHORIZATION, bearer(JwtFixture.tokenWithSubject("admin"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Deve recusar header Authorization malformado")
  void deveRecusarHeaderMalformado() throws Exception {
    mockMvc
        .perform(get(PROTECTED_ROUTE).header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Deve liberar as probes do Kubernetes sem token")
  void deveLiberarProbesSemToken() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Deve manter o readiness em UP com o RabbitMQ fora do ar")
  void deveManterReadinessComBrokerForaDoAr() throws Exception {
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"UP\"")));
  }

  @Test
  @DisplayName("Deve liberar o Prometheus sem token, para o scraping")
  void deveLiberarPrometheusSemToken() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Deve liberar a documentacao OpenAPI sem token")
  void deveLiberarDocumentacaoSemToken() throws Exception {
    mockMvc.perform(get("/api-docs")).andExpect(status().isOk());
  }
}
