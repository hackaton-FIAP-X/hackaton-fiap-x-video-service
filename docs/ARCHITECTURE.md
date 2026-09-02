# video-service — Arquitetura

API de borda do sistema de processamento de vídeos **FIAP X** (Hackathon POSTECH SOAT — Fase 5).

Este serviço é a **Trilha B** do plano do time. Ele recebe o upload, grava no object storage,
enfileira o trabalho e responde `202` em milissegundos. Quem executa o FFmpeg é o
`processing-worker`, do outro lado da fila.

---

## 1. Por que o serviço existe

O projeto base entregue pela FIAP X é um `main.go` de 500 linhas que roda o FFmpeg **dentro** do
request HTTP. O usuário fica esperando, uma réplica só atende um vídeo por vez e qualquer pico
derruba requisição.

A reescrita quebra isso em três serviços. O `video-service` é a fronteira entre o mundo síncrono
(HTTP, o usuário esperando resposta) e o mundo assíncrono (fila, worker, FFmpeg):

| Antes (projeto base) | Depois (video-service) |
| --- | --- |
| `POST /upload` roda FFmpeg e devolve o ZIP | `POST /videos` grava, enfileira e devolve `202` |
| Estado em `./uploads` e `./outputs` no disco local | Estado no PostgreSQL, artefatos no MinIO (S3) |
| Uma requisição por vez | N workers consumindo a mesma fila |
| Falha = requisição perdida | Falha = retry, DLQ e e-mail de notificação |

---

## 2. Posição no sistema

```
                    ┌──────────────┐
   cliente ────────▶│ Ingress NGINX│
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              │                         │
       ┌──────▼───────┐         ┌───────▼────────┐
       │ auth-service │         │  video-service │  ◀── este repositório
       │    :8081     │         │     :8080      │
       └──────┬───────┘         └───┬────────┬───┘
              │ JWKS                │        │
              └────────────────────▶┘        │ publica video.uploaded
                (validação offline)          │
                                    ┌────────▼────────┐
                                    │    RabbitMQ     │
                                    │ exchange topic  │
                                    │  fiapx.video    │
                                    └────────┬────────┘
                                             │ video.processing
                                    ┌────────▼─────────┐
                                    │ processing-worker│
                                    │   ×N (HPA 2–10)  │
                                    └────────┬─────────┘
                                             │ video.processed / video.failed
                                             └──▶ de volta ao video-service
```

Dependências de infraestrutura: **PostgreSQL** (estado dos vídeos), **MinIO** (vídeos e ZIPs),
**RabbitMQ** (fila), **Redis** (cache de listagem), **SMTP/Mailhog** (notificação de falha).

---

## 3. Contratos congelados

Estes contratos foram acordados com o time no Dia 1. **Mudança só com aviso explícito no grupo e
atualização deste arquivo no mesmo PR.**

### 3.1 REST

```
POST /videos          multipart: file            → 202 { videoId, status: "RECEIVED" }
GET  /videos          ?status=&page=&size=       → 200 { content: [...], totalElements }
GET  /videos/{id}                                → 200 { videoId, status, frameCount, errorMessage }
GET  /videos/{id}/zip                            → 302 Location: <presigned url, 5 min>
                                                   409 se status != COMPLETED
                                                   404 se o vídeo não for do dono do token
```

O `404` no lugar de `403` é deliberado: devolver `403` confirmaria que o vídeo existe e vazaria
informação sobre a conta de outro usuário.

### 3.2 JWT

```
alg  RS256          assimétrico — o video-service valida offline pela JWKS,
                    sem chamada síncrona ao auth-service a cada request
sub  <userId uuid>  iss  fiapx-auth   exp  +15min
claims extras: email, name
```

O `userId` **sempre** sai do claim `sub` do token. Nunca de um parâmetro de request, nunca de um
header. Toda query ao banco é escopada por ele.

Um token cujo `sub` não seja um UUID é rejeitado ainda na validação, com `401`. Isso deixa o
resto do código livre para tratar o `userId` como um UUID confiável, sem `try/catch` espalhado.

Enquanto o `shared/security-commons` da Trilha A (AUTH-5) não existir, a configuração do resource
server vive em `infrastructure/security/` deste serviço. Quando o módulo compartilhado chegar,
o que sai daqui é o `SecurityConfig`; o `CurrentUserId` e o resolver continuam, porque são a
tradução entre o token e a assinatura dos controllers.

### 3.3 Eventos — exchange `fiapx.video` (topic)

```
video.uploaded  → fila video.processing        (produtor: video-service)
  { videoId, userId, storageKey, originalFilename, fps: 1, requestedAt, traceId }

video.processed → fila video.status            (produtor: worker)
  { videoId, userId, zipKey, frameCount, finishedAt, traceId }

video.failed    → filas video.status + video.notification
  { videoId, userId, errorCode, errorMessage, attempts, traceId }

DLX fiapx.video.dlx → video.processing.dlq   após 3 tentativas com backoff
```

### 3.4 Tabela `videos`

```
id uuid pk · user_id uuid · original_filename · storage_key
zip_key · status · frame_count · error_message · attempts
created_at · updated_at
index (user_id, status, created_at desc)
```

Máquina de estados:

```
RECEIVED ──▶ QUEUED ──▶ PROCESSING ──▶ COMPLETED
                             │
                             └────────▶ FAILED
```

`COMPLETED` e `FAILED` são estados **finais**. Reentrega de mensagem não pode sobrescrevê-los —
essa é a garantia de idempotência do VID-7.

### 3.5 Chaves no bucket

```
fiapx/inputs/{userId}/{videoId}/{originalFilename}
fiapx/outputs/{userId}/{videoId}.zip
```

---

## 4. Arquitetura hexagonal

O código é organizado em quatro camadas. A regra que governa tudo: **a dependência aponta para
dentro**. O domínio não conhece ninguém; a infraestrutura conhece o domínio.

```
interfaces      ──▶  application  ──▶  domain  ◀──  infrastructure
(entrada HTTP)       (casos de uso)    (regras)     (adaptadores)
```

### 4.1 `domain/` — o núcleo

Entidades, value objects e as **portas** (interfaces de repositório e de serviços externos).
É aqui que moram as regras de negócio: quais transições de status são válidas, o que torna um
nome de arquivo aceitável, quando um vídeo pode ser baixado.

Não importa Spring. As anotações JPA são a única concessão de framework, seguindo o padrão já
usado pelo time no projeto da oficina.

```
domain/video/entity/Video.java              agregado, dono das transições de status
domain/video/valueobject/VideoStatus.java   enum + regras de transição
domain/video/valueobject/StorageKey.java    montagem e validação das chaves do bucket
domain/video/repository/VideoRepository.java        porta de saída — persistência
domain/video/gateway/VideoStorageGateway.java       porta de saída — object storage
domain/video/gateway/VideoEventPublisher.java       porta de saída — mensageria
domain/shared/exception/DomainException.java
```

### 4.2 `application/` — os casos de uso

Orquestra o domínio e as portas. Uma classe de serviço por agregado, DTOs em `record`,
transações demarcadas aqui. Não conhece HTTP: não recebe `HttpServletRequest`, não devolve
`ResponseEntity`.

```
application/video/service/VideoService.java
application/video/dto/UploadVideoResponse.java
application/video/dto/VideoResponse.java
application/video/dto/VideoPageResponse.java
application/shared/exception/BusinessException.java
application/shared/exception/ResourceNotFoundException.java
```

### 4.3 `infrastructure/` — os adaptadores de saída

Implementações concretas das portas do domínio. Trocar MinIO por S3 da AWS, ou RabbitMQ por
Kafka, mexe **só aqui**.

```
infrastructure/persistence/video/VideoJpaRepository.java     Spring Data
infrastructure/persistence/video/VideoRepositoryImpl.java    implements VideoRepository
infrastructure/storage/MinioStorageAdapter.java              implements VideoStorageGateway
infrastructure/messaging/RabbitMqConfig.java                 exchange, filas quorum e DLX
infrastructure/messaging/RabbitVideoEventPublisher.java      implements VideoEventPublisher
infrastructure/messaging/OutboxPublisherScheduler.java       rede de segurança do outbox
infrastructure/messaging/VideoStatusConsumer.java            consumidor dos eventos do worker
infrastructure/persistence/outbox/                           adaptador do outbox
infrastructure/security/SecurityConfig.java                  resource server JWT + JWKS
infrastructure/security/SubjectIsUuidValidator.java          recusa token cujo sub não é UUID
infrastructure/security/CurrentUserId.java                   anotação de parâmetro de controller
infrastructure/security/CurrentUserIdArgumentResolver.java   injeta o userId do claim sub
infrastructure/config/                                       beans de configuração
infrastructure/observability/                                métricas Micrometer
```

### 4.4 `interfaces/` — os adaptadores de entrada

Controllers REST, tradução HTTP ⇄ DTO e o handler global de erro. Zero regra de negócio: um
método de controller chama o serviço e devolve.

```
interfaces/video/VideoController.java
interfaces/shared/GlobalExceptionHandler.java     RFC 7807 (application/problem+json)
```

### 4.5 Pacote isolado

```
notification/    consumidor de video.failed que dispara o e-mail (PLT-8)
```

Vive dentro deste deployable mas em pacote próprio, com arquivos próprios, para não gerar
conflito de merge com a Trilha B. Se sobrar tempo no cronograma, vira o quarto serviço.

---

## 5. As decisões que sustentam os requisitos do PDF

| Exigência do PDF | Como o video-service atende |
| --- | --- |
| Processar mais de um vídeo ao mesmo tempo | O serviço não processa: enfileira. N workers consomem em paralelo. |
| Não perder requisição em pico | Outbox transacional + publisher confirms + quorum queue durável (VID-4). |
| Protegido por usuário e senha | Toda rota exige Bearer RS256, validado offline pela JWKS (VID-2). |
| Listagem de status dos vídeos | `GET /videos` paginado e escopado pelo `sub` do token (VID-5). |
| Notificação em caso de erro | Consumidor de `video.failed` disparando e-mail (PLT-8). |
| Persistir os dados | PostgreSQL com Flyway; artefatos no MinIO. |
| Arquitetura escalável | Serviço stateless, `replicas: 2`, estado só no Postgres e no MinIO. |
| Testes que garantam qualidade | JUnit 5, Mockito, Testcontainers; gate de 80% no JaCoCo (VID-10). |

### Três decisões que valem justificar na banca

**Por que fila em vez de request síncrono.** Processar dentro do request acopla o tempo de
resposta ao tamanho do vídeo e limita a vazão ao número de threads do servidor. Com a fila, o
upload responde em milissegundos e a capacidade de processamento escala de forma independente
da capacidade de recepção.

**Por que JWT com JWKS em vez de chamar o auth-service.** Validação offline com chave pública
elimina uma chamada de rede por request e remove o `auth-service` do caminho crítico. Se ele
cair, o `video-service` continua autenticando.

**Por que outbox em vez de publicar direto no RabbitMQ.** Publicar dentro do request cria duas
escritas sem transação comum: se o commit no Postgres passar e a publicação falhar, o vídeo fica
parado para sempre; se a publicação passar e o commit falhar, o worker processa um vídeo que não
existe. Gravando o evento na mesma transação do vídeo, o Postgres vira a fonte da verdade e o
RabbitMQ passa a ser um detalhe de entrega — que pode estar fora do ar sem custar uma requisição.

**Por que o readiness não olha o RabbitMQ.** O `/actuator/health` agregado fica `DOWN` quando o
broker cai, e isso é correto para alerta. Mas se o *readiness probe* usasse esse endpoint, o
Kubernetes tiraria o pod do balanceador e o serviço pararia de aceitar upload justamente quando o
outbox existe para absorvê-lo. Por isso o grupo `readiness` inclui apenas `readinessState` e `db`.
As probes apontam para `/actuator/health/readiness` e `/actuator/health/liveness`.

**Por que object storage em vez de volume.** Com múltiplas réplicas, o pod que recebeu o upload
não é o pod que serve o download, e o worker que gerou o ZIP não é nenhum dos dois. Storage
compartilhado compatível com S3 resolve, e deixa a migração para AWS trivial.

---

## 6. Roteiro de implementação

Cada item abaixo é **um commit**. A ordem é a do plano da Trilha B.

| # | Tarefa | Entrega |
| --- | --- | --- |
| VID-1 | Migration da tabela `videos`, enum de status e índice por usuário | Migration roda limpa em base vazia |
| VID-2 | Resource server JWT: toda rota exige Bearer, `userId` sai do `sub` | Request sem token devolve 401 |
| VID-3 | Upload multipart com stream direto para o MinIO | Upload de 200 MB não estoura o heap |
| VID-4 | `video.uploaded` com publisher confirms e outbox | Rabbit fora do ar não perde upload |
| VID-5 | Listagem paginada e detalhe, escopados ao dono do token | Usuário A recebe 404 no vídeo do B |
| VID-6 | Download por URL pré-assinada de 5 minutos | 409 quando o status não é `COMPLETED` |
| VID-7 | Consumidor de `video.processed` / `video.failed`, idempotente | Republicar 3× deixa a linha igual |
| VID-8 | Cache Redis da listagem com invalidação no update | — |
| VID-9 | Handler global de erro em RFC 7807 | `application/problem+json` |
| VID-10 | Testes unitários, integração com Testcontainers, contrato de evento | JaCoCo ≥ 80% |
| VID-11 | Dockerfile multi-stage e Actuator com Prometheus | Imagem abaixo de 250 MB |
| PLT-8 | Notificação por e-mail no consumidor de `video.failed` | E-mail visível no Mailhog |

---

## 7. Convenções de código

Estas regras valem para todo arquivo deste repositório.

**Sem comentários no código.** O nome da classe, do método e da variável carrega a intenção. Se
um trecho precisa de comentário para ser entendido, ele precisa ser extraído para um método com
nome. Explicação de arquitetura mora aqui neste documento, não no meio do código.

**Uma classe, uma responsabilidade.** Controller traduz HTTP. Serviço orquestra. Entidade decide.
Adaptador conversa com o mundo externo.

**Regra de negócio mora na entidade.** Se o `VideoService` está com um `if` sobre status, esse
`if` pertence ao `Video` ou ao `VideoStatus`.

**DTO é `record`.** Imutável, com `fromEntity` estático quando for de resposta.

**Validação em duas camadas.** Bean Validation no DTO protege a borda; o construtor da entidade
protege o domínio de quem chamar por dentro.

**Nunca confie no cliente para identidade.** O `userId` vem do token. Sempre.

**Teste junto com a tarefa.** Está no Definition of Done do time e é o que evita chegar no dia 11
tentando cobrir quatro serviços de uma vez.

---

## 8. Como rodar

Tudo sobe em container. Não é necessário ter Java nem Maven instalados na máquina.

```bash
make up         # sobe Postgres, RabbitMQ, MinIO, Redis, Mailhog e o serviço
make logs
make down
```

Se a porta `8080` já estiver ocupada por outro projeto na máquina, copie `.env.example` para
`.env` e ajuste `VIDEO_SERVICE_PORT`. A porta **interna** do container continua sendo `8080` em
qualquer caso — é ela que vale para o contrato e para os manifests do Kubernetes.

| Serviço | Endereço | Credenciais |
| --- | --- | --- |
| video-service | http://localhost:8080 | Bearer JWT |
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| Actuator | http://localhost:8080/actuator/health | — |
| RabbitMQ | http://localhost:15672 | `fiapx` / `fiapx` |
| MinIO | http://localhost:9001 | `fiapx` / `fiapx12345` |
| Mailhog | http://localhost:8025 | — |
| PostgreSQL | `localhost:5432` | `fiapx` / `fiapx` · base `fiapx_video` |
| Redis | `localhost:6379` | — |

Build e testes também rodam em container:

```bash
make build      # mvn verify dentro da imagem maven:3.9-eclipse-temurin-21
make test       # só os testes
```
