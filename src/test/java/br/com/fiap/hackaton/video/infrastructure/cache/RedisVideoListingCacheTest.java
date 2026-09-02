package br.com.fiap.hackaton.video.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.hackaton.video.application.video.dto.VideoPageResponse;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisVideoListingCacheTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Duration TTL = Duration.ofSeconds(60);

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisVideoListingCache cache;

  @BeforeEach
  void setUp() {
    cache =
        new RedisVideoListingCache(
            redis, new ObjectMapper().registerModule(new JavaTimeModule()), TTL);
  }

  private void redisIsAvailable() {
    when(redis.opsForValue()).thenReturn(valueOperations);
  }

  private VideoPageResponse emptyPage() {
    return new VideoPageResponse(List.of(), 0, 0, 0, 20);
  }

  private String capturedKey() {
    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(key.capture(), anyString(), eq(TTL));
    return key.getValue();
  }

  @Test
  @DisplayName("Deve devolver vazio quando a chave nao esta no Redis")
  void deveDevolverVazioEmCacheMiss() {
    redisIsAvailable();
    when(valueOperations.get(anyString())).thenReturn(null);

    assertThat(cache.find(USER_ID, null, 0, 20)).isEmpty();
  }

  @Test
  @DisplayName("Deve gravar a pagina com o TTL configurado")
  void deveGravarComTtl() {
    redisIsAvailable();

    cache.store(USER_ID, null, 0, 20, emptyPage());

    verify(valueOperations).set(anyString(), anyString(), eq(TTL));
  }

  @Test
  @DisplayName("Deve isolar a chave por usuario, status, pagina e tamanho")
  void deveIsolarChavePorParametros() {
    redisIsAvailable();
    when(valueOperations.get("videos:version:" + USER_ID)).thenReturn("3");

    cache.store(USER_ID, VideoStatus.COMPLETED, 2, 50, emptyPage());

    assertThat(capturedKey()).isEqualTo("videos:page:%s:v3:COMPLETED:2:50".formatted(USER_ID));
  }

  @Test
  @DisplayName("Deve usar a versao zero enquanto o usuario nunca invalidou o cache")
  void deveUsarVersaoZeroPorPadrao() {
    redisIsAvailable();
    when(valueOperations.get("videos:version:" + USER_ID)).thenReturn(null);

    cache.store(USER_ID, null, 0, 20, emptyPage());

    assertThat(capturedKey()).contains(":v0:").contains(":all:");
  }

  @Test
  @DisplayName("Deve invalidar incrementando a versao do usuario, sem varrer chaves")
  void deveInvalidarIncrementandoVersao() {
    redisIsAvailable();

    cache.invalidate(USER_ID);

    verify(valueOperations).increment("videos:version:" + USER_ID);
  }

  @Test
  @DisplayName("Deve fazer a ida e volta de uma pagina serializada")
  void deveFazerIdaEVoltaDaPagina() throws Exception {
    redisIsAvailable();
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    when(valueOperations.get(anyString())).thenReturn(mapper.writeValueAsString(emptyPage()));

    Optional<VideoPageResponse> encontrada = cache.find(USER_ID, null, 0, 20);

    assertThat(encontrada).isPresent();
    assertThat(encontrada.get().size()).isEqualTo(20);
  }

  @Test
  @DisplayName("Deve degradar para cache miss quando o Redis falha na leitura")
  void deveDegradarQuandoRedisFalhaNaLeitura() {
    when(redis.opsForValue()).thenThrow(new QueryTimeoutException("redis fora do ar"));

    assertThat(cache.find(USER_ID, null, 0, 20)).isEmpty();
  }

  @Test
  @DisplayName("Deve engolir falha do Redis na escrita, sem quebrar a listagem")
  void deveEngolirFalhaNaEscrita() {
    when(redis.opsForValue()).thenThrow(new QueryTimeoutException("redis fora do ar"));

    cache.store(USER_ID, null, 0, 20, emptyPage());

    verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("Deve engolir falha do Redis na invalidacao, sem quebrar a escrita do video")
  void deveEngolirFalhaNaInvalidacao() {
    when(redis.opsForValue()).thenThrow(new QueryTimeoutException("redis fora do ar"));

    cache.invalidate(USER_ID);

    verify(valueOperations, never()).increment(anyString());
  }

  @Test
  @DisplayName("Deve degradar para cache miss quando o conteudo gravado esta corrompido")
  void deveDegradarComConteudoCorrompido() {
    redisIsAvailable();
    when(valueOperations.get(anyString())).thenReturn("isto nao e json");

    assertThat(cache.find(USER_ID, null, 0, 20)).isEmpty();
  }
}
