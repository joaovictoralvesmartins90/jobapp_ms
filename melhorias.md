# Revisão técnica: jobapp_ms

Revisão dos serviços do projeto segundo boas práticas de APIs REST, uso de DTOs, código limpo e o ecossistema Spring (Boot 4.1 / Cloud 2025.1).

Cada item a melhorar segue o formato **Problema → Por que está errado → Solução**. Os itens estão ordenados por gravidade dentro de cada serviço:

- 🔴 **Bug**: comportamento incorreto hoje.
- 🟠 **Importante**: viola princípios REST ou de design e vai causar problemas.
- 🟡 **Melhoria**: limpeza, legibilidade, convenções.

## Sumário

1. [Visão geral: o que está bom](#1-visão-geral-o-que-está-bom)
2. [company_service](#2-company_service)
3. [job_service](#3-job_service)
4. [review_service](#4-review_service)
5. [api-gateway](#5-api-gateway)
6. [config-server](#6-config-server)
7. [service-registry](#7-service-registry)
8. [Pontos transversais (todos os serviços)](#8-pontos-transversais-todos-os-serviços)
9. [Ordem sugerida de execução](#9-ordem-sugerida-de-execução)

---

## 1. Visão geral: o que está bom

| Ponto | Onde |
|---|---|
| URIs com **substantivos no plural** (`/companies`, `/jobs`, `/reviews`) e sem verbos nos CRUDs | Todos os controllers |
| **Recursos aninhados** que mostram a relação de posse: `/companies/{companyId}/jobs` e `/companies/{companyId}/reviews` | job, review |
| **Verbos HTTP corretos**: GET lê, POST cria, PUT atualiza, DELETE remove | Todos |
| `POST` devolve **201 Created** com header **`Location`** montado por `ServletUriComponentsBuilder` | Todos |
| **404** quando o recurso não existe | Todos |
| **Injeção por construtor** com campos `final` na maior parte do código | Todos (exceção em `JobService`) |
| Camadas bem separadas: controller → service → repository | Todos |
| Services retornando `Optional` em vez de `null` | company, review |
| **Banco por serviço** (`companydb`, `jobdb`, `reviewdb`), sem tabelas compartilhadas entre serviços | Infra |
| **Service discovery** com Eureka + **OpenFeign** para chamadas síncronas | Todos |
| **Circuit breaker** (Resilience4j) com fallback e health indicator | job |
| **Mensageria assíncrona** (RabbitMQ) para atualizar a média, com conversor JSON | review → company |
| Mensagem do Rabbit com DTO próprio (`ReviewMessage`) em vez da entidade | review, company |
| Uso de DTOs no `job_service` (`JobDto`, `CompanyDto`, `CreateJobDto`) | job |
| **Observabilidade**: OpenTelemetry, trace/span no log, Zipkin, Actuator | Todos |
| **Dockerfile multi-stage** com cache do Maven e imagem final só com a JRE | company |
| `docker-compose` com healthcheck, portas presas a `127.0.0.1` e script de criação dos bancos | Infra |
| Perfil `docker` separado (`application-docker.properties`) | company |
| `Config Server` apontando para um repositório Git | config-server |

---

## 2. company_service

### 🔴 2.1 Falta `@EnableFeignClients`

**Problema:** `CompanyServiceApplication` não tem `@EnableFeignClients`, mas `CompanyService` depende de `ReviewClient` (uma interface `@FeignClient`).

**Por que está errado:** sem a anotação, o Spring não cria a implementação da interface. Sem o bean, a injeção em `CompanyService` falha e o contexto não sobe (`No qualifying bean of type ReviewClient`). O `job_service` tem a anotação; o `company_service` não.

**Solução:**

```java
@EnableFeignClients
@SpringBootApplication
public class CompanyServiceApplication { ... }
```

### 🔴 2.2 `updateCompany` não atualiza a descrição

**Problema:** em `CompanyService.updateCompany`, só `name` e `employees` são copiados. `description` é ignorada.

**Por que está errado:** `PUT` substitui o recurso inteiro. O cliente envia uma descrição nova, recebe "updated successfully" e o valor não muda.

**Solução:** copiar todos os campos editáveis. Com DTO (ver 2.4), isso fica explícito:

```java
company.setName(request.name());
company.setEmployees(request.employees());
company.setDescription(request.description());
```

### 🔴 2.3 Empresa duplicada devolve 201 sem criar nada

**Problema:** em `addCompany`, se o nome já existe, o service não salva e devolve o objeto recebido. O controller responde **201 Created** com `Location: /companies/null`.

**Por que está errado:** 201 diz ao cliente que um recurso foi criado, e isso não aconteceu. O `Location` aponta para uma URI inválida. Conflito de unicidade deve ser **409 Conflict**.

**Solução:** lançar uma exceção de domínio e traduzi-la num `@RestControllerAdvice` (ver 8.2):

```java
public CompanyResponse create(CompanyRequest request) {
    if (repository.existsByNameIgnoreCase(request.name())) {
        throw new CompanyAlreadyExistsException(request.name()); // → 409
    }
    return mapper.toResponse(repository.save(mapper.toEntity(request)));
}
```

Também vale uma restrição `unique` no banco (`@Column(unique = true)`): a verificação no código sozinha não impede duas requisições simultâneas de criarem o mesmo nome.

### 🟠 2.4 A entidade JPA é exposta direto na API

**Problema:** o controller recebe e devolve `Company` (entidade `@Entity`).

**Por que está errado:**
- O cliente pode enviar `id` e `averageRating` no POST/PUT. `averageRating` deveria ser calculado só pelo sistema, a partir das reviews.
- Qualquer mudança na tabela (coluna nova, relacionamento) muda o contrato da API sem querer.
- Relacionamentos `LAZY` futuros causam `LazyInitializationException` ou serialização em loop.
- Não há onde colocar validação de entrada sem misturar regras HTTP na entidade.

**Solução:** DTOs separados para entrada e saída. Com Java 21, `record` é o ideal:

```java
public record CompanyRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero Integer employees,
        @NotBlank @Size(max = 1000) String description) {}

public record CompanyResponse(
        Long id, String name, Integer employees,
        String description, Double averageRating) {}
```

E um mapper (classe com métodos estáticos, um `@Component` ou MapStruct) para converter entre DTO e entidade.

### 🟠 2.5 DELETE e PUT devolvem texto solto

**Problema:** `deleteCompany` responde `200` com `"Company deleted successfully"`. `updateCompany` responde `200` com `"Company updated successfully"`.

**Por que está errado:** o status HTTP já diz se deu certo. Uma string sem estrutura não é JSON, obriga o cliente a tratar texto e mistura idiomas/mensagens com o contrato. A convenção REST é:
- `DELETE` com sucesso → **204 No Content**, sem corpo.
- `PUT` com sucesso → **200 OK com o recurso atualizado** (ou 204).

**Solução:**

```java
@DeleteMapping("/{companyId}")
public ResponseEntity<Void> delete(@PathVariable Long companyId) {
    companyService.delete(companyId);          // lança NotFound se não existir
    return ResponseEntity.noContent().build();
}

@PutMapping("/{companyId}")
public ResponseEntity<CompanyResponse> update(@PathVariable Long companyId,
                                              @Valid @RequestBody CompanyRequest request) {
    return ResponseEntity.ok(companyService.update(companyId, request));
}
```

### 🟠 2.6 O consumer chama o review_service de volta, de forma síncrona

**Problema:** ao receber a mensagem, `updateCompanyRating` faz uma chamada Feign para `review-service` buscar a média.

**Por que está errado:** a mensageria existe para desacoplar os serviços. Com a chamada de volta, o company volta a depender do review estar no ar, sem circuit breaker nem timeout. Se o review estiver fora, a exceção volta para o listener e o Rabbit reentrega a mensagem sem parar (sem DLQ, ver 2.8).

**Solução (escolha uma):**
1. **O review_service calcula e envia a média na própria mensagem** (`companyId`, `averageRating`, `reviewCount`). O company só grava. É a opção mais simples e a mais desacoplada.
2. Guardar `reviewCount` e `ratingSum` na `Company` e atualizar de forma incremental a cada evento (exige evento também para update/delete de review).

Exemplo da opção 1:

```java
public record CompanyRatingUpdatedEvent(Long companyId, double averageRating, long reviewCount) {}
```

### 🟠 2.7 Consumer depende da classe concreta e o service mistura camadas

**Problema:** `ReviewMessageConsumer` injeta `CompanyService` (classe) em vez de `ICompanyService`. Além disso, `ICompanyService.updateCompanyRating` recebe `ReviewMessage`, um DTO da camada de mensageria.

**Por que está errado:** se a interface existe, depender da implementação anula o propósito dela (troca de implementação, proxies, mocks em teste). E a camada de serviço passa a conhecer o formato da mensagem do Rabbit: se o formato mudar, o service muda junto.

**Solução:** injetar a interface e passar só o que o service precisa:

```java
@RabbitListener(queues = "${app.messaging.rating-queue}")
public void consume(CompanyRatingUpdatedEvent event) {
    companyService.updateAverageRating(event.companyId(), event.averageRating());
}
```

### 🟠 2.8 Configuração do RabbitMQ: nome fixo, beans redundantes, sem DLQ

**Problema:**
- O nome `"companyRatingQueue"` aparece repetido como string em 4 lugares, entre os dois serviços.
- O `RabbitTemplate` é redefinido à mão. No company ele nem é usado (o serviço só consome).
- `RabbitMQConfiguration` está no pacote `messaging`, mas é uma classe de configuração.
- Não há dead-letter queue.

**Por que está errado:** uma string repetida fica divergente na primeira refatoração. O Spring Boot já cria um `RabbitTemplate` e o listener container usando o `MessageConverter` declarado como bean, então redefinir é código a mais. Sem DLQ, uma mensagem que sempre falha (ex.: `companyId` inexistente, serviço fora) é reprocessada para sempre ou perdida.

**Solução:**

```properties
app.messaging.rating-queue=company.rating.queue
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
```

```java
@Configuration
public class RabbitMQConfig {
    @Bean
    Queue ratingQueue(@Value("${app.messaging.rating-queue}") String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }

    @Bean
    Queue ratingDlq(@Value("${app.messaging.rating-queue}") String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    @Bean
    MessageConverter messageConverter() { return new JacksonJsonMessageConverter(); }
    // sem RabbitTemplate manual: o auto-configurado já usa este converter
}
```

### 🟠 2.9 Sem validação e sem tratamento global de erros

Ver 8.1 e 8.2. No company especificamente: `name`, `employees` e `description` são `nullable = false` no banco, então um POST sem esses campos estoura `DataIntegrityViolationException` e devolve **500** em vez de **400**.

### 🟠 2.10 `GET /companies` sem paginação

**Problema:** `findAll()` devolve a tabela inteira.

**Por que está errado:** a resposta cresce sem limite e o tempo de resposta acompanha.

**Solução:**

```java
@GetMapping
public Page<CompanyResponse> findAll(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return companyService.findAll(pageable);
}
```

Com `spring.data.web.pageable.serialization-mode=via_dto` (padrão nas versões recentes), o JSON da página fica estável.

### 🟡 2.11 Detalhes de código limpo

| Problema | Por que | Solução |
|---|---|---|
| `findByNameIgnoreCase` retorna `Company` e é comparado com `null` | Carrega a entidade inteira só para saber se existe, e usa `null` como sinal | `boolean existsByNameIgnoreCase(String name)` |
| `companyExists` é público na interface do service | Detalhe interno vazando para o contrato | Tornar privado |
| Métodos `deleteCompany`/`updateCompany` retornam `boolean` | O controller precisa traduzir `false` em 404 em todo lugar | Lançar `ResourceNotFoundException` e tratar no advice (8.2) |
| `findById` + `isEmpty()` + `get()` | Verboso | `repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(...))` |
| Sem `@Transactional` nos métodos de escrita | Ler, alterar e salvar fica fora de uma transação única | `@Transactional` nos métodos de escrita e `@Transactional(readOnly = true)` nas leituras. Com a entidade gerenciada, o `save` depois do setter fica desnecessário |
| Imports sem uso em `Company` (`JsonIgnore`, `ArrayList`, `List`) | Ruído | Remover |
| `@GetMapping()` / `@PostMapping()` com parênteses vazios | Ruído | `@GetMapping` |
| `this.companyService` em alguns métodos e `companyService` em outros | Inconsistência | Padronizar sem `this.` |
| `ReviewMessage` como classe com getters/setters | Boilerplate | `record` |
| `Company` sem `equals`/`hashCode` | Problemas em `Set` e comparações de entidades | Implementar baseado no `id` (ou usar a abordagem recomendada pelo Hibernate) |

---

## 3. job_service

### 🔴 3.1 O `companyId` da URL é ignorado em GET por id, PUT e DELETE

**Problema:** `findJobById`, `updateJob` e `deleteJob` recebem `companyId` no path mas não o usam. `GET /companies/1/jobs/5` devolve a vaga 5 mesmo que ela seja da empresa 2. `DELETE /companies/999/jobs/5` apaga a vaga 5.

**Por que está errado:** num recurso aninhado, a URI identifica o recurso pelo caminho inteiro. `/companies/1/jobs/5` significa "a vaga 5 **da empresa 1**". Se a vaga não pertence a ela, a resposta correta é 404.

**Solução:** filtrar pelos dois ids no repositório:

```java
Optional<Job> findByIdAndCompanyId(Long id, Long companyId);
```

```java
public JobResponse findById(Long companyId, Long jobId) {
    Job job = jobRepository.findByIdAndCompanyId(jobId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));
    ...
}
```

### 🔴 3.2 `PUT` permite trocar a vaga de empresa pelo corpo

**Problema:** `updateJob` recebe a entidade `Job` e faz `jobDb.setCompanyId(job.getCompanyId())`. Se o corpo não tiver `companyId`, a vaga fica com `companyId = null`.

**Por que está errado:** a empresa já está definida na URI. Aceitar outra no corpo cria duas fontes de verdade, e a ausência do campo apaga o vínculo.

**Solução:** usar um DTO de entrada **sem** `id` e `companyId` e nunca alterar o `companyId` no update (ver 3.5).

### 🔴 3.3 Fallback do circuit breaker devolve lista vazia com 200

**Problema:** quando o company-service falha, `companyBreakerFallback` devolve `new ArrayList<>()`, e o cliente recebe `200 []`.

**Por que está errado:** o cliente entende "esta empresa não tem vagas", o que é falso: as vagas existem no `jobdb`, só a informação da empresa ficou indisponível. O fallback esconde a falha e devolve dado errado.

**Solução (escolha uma):**
- **Degradar com elegância:** devolver as vagas com `company = null` (ou só com o `id`), já que elas estão no banco local.
- **Falhar de forma explícita:** lançar uma exceção mapeada para **503 Service Unavailable**.

```java
public List<JobResponse> companyFallback(Long companyId, Throwable ex) {
    log.warn("company-service indisponível, devolvendo vagas sem dados da empresa", ex);
    return jobRepository.findByCompanyId(companyId).stream()
            .map(job -> mapper.toResponse(job, null))
            .toList();
}
```

### 🔴 3.4 Empresa inexistente vira erro 500

**Problema:**
- `addJob` não verifica se a empresa existe. Dá para criar vaga em `/companies/999/jobs`.
- Em `findJobById`, se o company-service devolver 404, o Feign lança `FeignException.NotFound`, que vira **500**. `findJobById` também não tem circuit breaker.

**Por que está errado:** integridade referencial entre serviços tem de ser garantida pelo código, porque não há FK entre bancos. E um 404 de um serviço chamado não é erro interno do servidor.

**Solução:**
- No `addJob`, consultar o company-service antes de salvar e devolver 404 (ou 422) se a empresa não existir.
- Tratar `FeignException.NotFound` no `@RestControllerAdvice` ou com um `ErrorDecoder` do Feign.
- Aplicar `@CircuitBreaker` também em `findJobById`, ou isolar as chamadas num componente `CompanyGateway` com o circuit breaker, para não repetir a anotação em cada método.

### 🟠 3.5 DTOs com nomes e papéis confusos

**Problema:**
- `CreateJobDto` é usado como **entrada** do POST e também como **resposta** do POST. Tem `id` e `companyId`, que o cliente não deveria enviar.
- `PUT` recebe a **entidade** `Job`, não um DTO.
- `JobDto` e `CreateJobDto` têm exatamente os mesmos campos.
- O POST devolve `CreateJobDto`, mas o GET devolve `JobWithCompanyDto`: o mesmo recurso tem dois formatos.
- `CompanyDto` não tem `id`.

**Por que está errado:** um DTO de entrada define o que o cliente **pode** enviar. Com `id` nele, o contrato sugere que o cliente escolhe o id. O formato de um recurso deve ser o mesmo em todas as respostas, para o cliente ter um modelo único.

**Solução:**

```java
// entrada: POST e PUT
public record JobRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 1000) String description,
        @NotNull @PositiveOrZero BigDecimal minSalary,
        @NotNull @PositiveOrZero BigDecimal maxSalary,
        @NotBlank String location) {}

// saída: GET, POST e PUT
public record JobResponse(
        Long id, String title, String description,
        BigDecimal minSalary, BigDecimal maxSalary,
        String location, CompanySummary company) {}

public record CompanySummary(Long id, String name, Double averageRating) {}
```

### 🟠 3.6 JSON com nomes de classe: `jobDto` e `companyDto`

**Problema:** `JobWithCompanyDto` serializa como `{"jobDto": {...}, "companyDto": {...}}`.

**Por que está errado:** o sufixo `Dto` é detalhe de implementação Java vazando para o contrato público. O cliente quer `job` e `company`, ou a vaga com a empresa embutida.

**Solução:** usar o `JobResponse` do item 3.5 (vaga com `company` embutida).

### 🟠 3.7 N+1 chamadas HTTP em `findAll`

**Problema:** o loop chama `companyClient.getCompany(...)` **uma vez por vaga**, mas todas as vagas retornadas têm o mesmo `companyId` (o da URL).

**Por que está errado:** 50 vagas viram 50 requisições HTTP iguais. Cada uma soma latência e conta como falha separada para o circuit breaker.

**Solução:** buscar a empresa uma vez só:

```java
CompanySummary company = companyClient.getCompany(companyId);
return jobRepository.findByCompanyId(companyId).stream()
        .map(job -> mapper.toResponse(job, company))
        .toList();
```

Para a lista, vale também avaliar se a empresa precisa mesmo vir em cada item: o cliente já sabe de qual empresa está listando.

### 🟠 3.8 Salário como `String`

**Problema:** `minSalary` e `maxSalary` são `String` na entidade e nos DTOs.

**Por que está errado:** não dá para ordenar, filtrar por faixa nem validar `min <= max`. O banco aceita `"abc"`.

**Solução:** `BigDecimal` (com `@Column(precision = 12, scale = 2)`) e uma validação de classe:

```java
@AssertTrue(message = "minSalary deve ser menor ou igual a maxSalary")
boolean isSalaryRangeValid() {
    return minSalary == null || maxSalary == null || minSalary.compareTo(maxSalary) <= 0;
}
```

### 🟠 3.9 DELETE/PUT com texto solto e erro de digitação

Mesmo problema de 2.5, e as mensagens dizem `"succesfully"` (o certo é "successfully"). Solução: **204** no DELETE e **200 com `JobResponse`** no PUT.

### 🟡 3.10 Detalhes de código limpo

| Problema | Por que | Solução |
|---|---|---|
| `@Autowired RestTemplate restTemplate` no `JobService` sem uso, e `AppConfig` criando um `RestTemplate` `@LoadBalanced` | Código morto, injeção por campo e dependência desnecessária (o Feign já faz o trabalho) | Remover o campo e o `AppConfig` |
| `companyClient` não é `final` | Pode ser reatribuído e quebra a imutabilidade do service | `private final CompanyClient companyClient;` |
| `findById(...).stream().filter(job -> job.getId().equals(jobId)).findFirst()` | O filtro é redundante: `findById` já garante o id | `findByIdAndCompanyId(...).orElseThrow(...)` |
| Services retornam `null` (`findJobById`, `getJob`) | Obriga `if (x == null)` em quem chama e esconde o caso "não encontrado" | `Optional` ou exceção de domínio |
| `JobMapper` é `@Component` mas só tem métodos `static` | Contraditório: o bean nunca é injetado | Escolher um: classe utilitária `final` com construtor privado, ou `@Component` com métodos de instância injetado no service. MapStruct gera isso automaticamente |
| Mapeamento `Job → JobDto` feito dentro do `JobService` (`getJobWithCompanyDto`) | O mapper existe mas não é usado nesse caso; a responsabilidade fica espalhada | Mover para o mapper |
| `addJob` altera o DTO recebido (`createJobDto.setCompanyId(...)`) | Efeito colateral no parâmetro | Passar `companyId` ao mapper: `mapper.toEntity(request, companyId)` |
| Controller chama o mapper (`JobMapper.mapFromClass`) | Mistura responsabilidades: parte do mapeamento no controller, parte no service | O service devolve o DTO de resposta pronto |
| `new ResponseEntity<>(..., HttpStatus.OK)` num método e `ResponseEntity.ok(...)` nos outros | Inconsistente | `ResponseEntity.ok(...)` |
| Imports sem uso: `jakarta.persistence.Column` em `JobDto`, `Optional` em `IJobService` | Ruído. Uma anotação JPA num DTO também sugere acoplamento | Remover |
| `Job.companyId` sem `@Column(nullable = false)` | Vaga sem empresa é estado inválido | `@Column(nullable = false)` e índice em `company_id` |
| `registerHealthIndicator` e `register-health-indicator` declarados duas vezes | Mesma propriedade em duas grafias | Manter uma (kebab-case é o padrão) |
| `management.endpoint.health.show-details=ALWAYS` | Expõe detalhes internos (banco, disco, circuit breaker) para qualquer um | `when-authorized` em produção |
| Feign sem timeout configurado | Uma chamada lenta segura a thread pelo tempo padrão | `spring.cloud.openfeign.client.config.company-service.connect-timeout` / `read-timeout`, ou `@TimeLimiter` |

---

## 4. review_service

### 🔴 4.1 `updateReview` grava o título na descrição

**Problema:** `reviewUpdate.setDescription(review.getTitle());`

**Por que está errado:** erro de cópia: toda atualização sobrescreve a descrição com o título.

**Solução:** `reviewUpdate.setDescription(request.description());`

### 🔴 4.2 O `companyId` da URL é ignorado em GET por id, PUT e DELETE

Mesmo problema de 3.1. `findById`, `deleteReview` e `updateReview` nem recebem `companyId` como parâmetro. `DELETE /companies/1/reviews/7` apaga a review 7 de qualquer empresa.

**Solução:** `Optional<Review> findByIdAndCompanyId(Long id, Long companyId);` e usar os dois ids em todos os métodos.

### 🔴 4.3 PUT permite trocar a empresa e a média não é recalculada

**Problema:**
- `updateReview` copia `review.getCompanyId()` do corpo, que é `null` se o cliente não enviar.
- Só o `POST` publica mensagem. `PUT` (que muda a nota) e `DELETE` (que remove uma nota) não publicam nada, então `averageRating` da empresa fica desatualizada.

**Por que está errado:** o mesmo problema de duas fontes de verdade do item 3.2. E a média é um dado derivado: toda operação que muda o conjunto de notas precisa disparar o recálculo.

**Solução:** não aceitar `companyId` no corpo e publicar o evento em create, update e delete (ver 4.4).

### 🟠 4.4 Publicação da mensagem no controller e fora da transação

**Problema:** `ReviewController.addReview` chama `reviewMessageProducer.sendMessage(...)` depois do `addReview`.

**Por que está errado:**
- Regra de negócio ("ao criar uma review, avisar o company") no controller. O controller deveria só traduzir HTTP para chamadas ao service.
- Não há garantia de consistência: se o envio falhar, a review está salva mas a média nunca é atualizada. Se um dia houver transação envolvendo os dois, a mensagem pode sair antes do commit.

**Solução:** mover para o service e publicar só **depois do commit**:

```java
@Service
@Transactional
public class ReviewService {
    private final ApplicationEventPublisher events;

    public ReviewResponse create(Long companyId, ReviewRequest request) {
        Review saved = repository.save(mapper.toEntity(request, companyId));
        events.publishEvent(new ReviewChangedEvent(companyId));
        return mapper.toResponse(saved);
    }
}

@Component
class ReviewEventsPublisher {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReviewChangedEvent e) {
        double avg = repository.averageRatingByCompanyId(e.companyId()).orElse(0.0);
        rabbitTemplate.convertAndSend(queue, new CompanyRatingUpdatedEvent(e.companyId(), avg, ...));
    }
}
```

Para garantia total de entrega, o padrão é o **Transactional Outbox**: grava o evento numa tabela na mesma transação e um processo separado publica.

### 🟠 4.5 Endpoint `/averagerating`: nome, local do cálculo e resposta

**Problema:**
- `GET /companies/{id}/reviews/averagerating`: tudo junto e minúsculo, sem hífen.
- A média é calculada **no controller**, carregando todas as reviews na memória.
- Devolve um `Double` cru (`4.25`) em vez de um objeto JSON.
- Sem reviews, devolve `0.0`, que não se distingue de "nota média zero".
- Fica sob `/reviews/...` mas não é uma review. `/reviews/averagerating` também conflita conceitualmente com `/reviews/{reviewId}`.

**Por que está errado:** URIs REST usam `kebab-case`. Cálculo é responsabilidade do service, e agregação deve rodar no banco. Uma resposta primitiva não pode ganhar campos depois sem quebrar o cliente.

**Solução:**

```java
// repository
@Query("select avg(r.rating) from Review r where r.companyId = :companyId")
Optional<Double> averageRatingByCompanyId(Long companyId);

long countByCompanyId(Long companyId);
```

```java
// controller
@GetMapping("/rating-summary")
public RatingSummaryResponse ratingSummary(@PathVariable Long companyId) {
    return reviewService.ratingSummary(companyId);
}

public record RatingSummaryResponse(Long companyId, Double averageRating, long reviewCount) {}
```

Resultado: `GET /companies/1/reviews/rating-summary` → `{"companyId":1,"averageRating":4.25,"reviewCount":8}`, com `averageRating: null` quando não houver reviews. Se for aplicada a solução 2.6 (a média vai na mensagem), este endpoint deixa de ser necessário para o company e pode virar só leitura pública, ou ser removido.

### 🟠 4.6 Entidade exposta na API e sem validação

**Problema:** o controller recebe e devolve `Review`. Não há limite para `rating`: dá para enviar `-50` ou `1000`.

**Por que está errado:** os mesmos motivos de 2.4. O cliente também pode enviar `id` e `companyId`, e `createdAt` aparece no JSON no formato que o Jackson escolher.

**Solução:**

```java
public record ReviewRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin("1.0") @DecimalMax("5.0") Double rating) {}

public record ReviewResponse(
        Long id, String title, String description,
        double rating, Instant createdAt) {}
```

`addReview` também deve verificar se a empresa existe antes de salvar, como em 3.4.

### 🟠 4.7 Controller depende da classe concreta

**Problema:** `ReviewController` injeta `ReviewService` em vez de `IReviewService`.

**Solução:** injetar a interface (ou remover a interface, ver 8.6).

### 🟡 4.8 Detalhes de código limpo

| Problema | Por que | Solução |
|---|---|---|
| `import jakarta.websocket.server.PathParam;` sem uso | Import errado (é de WebSocket, não de Spring MVC) e sem uso | Remover |
| `@GetMapping("{reviewId}")` e `@DeleteMapping("{reviewId}")` sem `/`, mas `@PutMapping("/{reviewId}")` com | Inconsistente | Sempre `"/{reviewId}"` |
| Mensagem `"Company updated successfully"` no update de review | Texto copiado de outro controller | Devolver 200 com `ReviewResponse` (sem mensagem) |
| DELETE/PUT devolvem texto | Ver 2.5 | 204 / 200 com DTO |
| `LocalDateTime` em `createdAt` | Sem fuso: o valor depende do relógio do servidor | `Instant` ou `OffsetDateTime`, e `@CreationTimestamp` no lugar do `@PrePersist` manual |
| `rating` como `double` primitivo | Um JSON sem `rating` vira `0.0` sem erro | `Double` no DTO com `@NotNull` |
| Construtor de `Review` sem `companyId` | Cria entidade em estado incompleto | Incluir `companyId` no construtor |
| Sem `@Transactional`, `boolean` para "não encontrado", `findById` + `isEmpty` + `get` | Ver 2.11 | Mesmas soluções |
| `RabbitTemplate` redefinido à mão | Ver 2.8 | Remover; manter só `Queue` e `MessageConverter` |
| `#postgresq` no `application.properties` | Erro de digitação no comentário | `#postgresql` |

---

## 5. api-gateway

### 🟠 5.1 Rotas com `localhost:porta` em vez do Eureka

**Problema:** `routes[*].uri=http://localhost:8081` (e 8082, 8083).

**Por que está errado:** o gateway já é cliente do Eureka, mas não usa a descoberta. Não há balanceamento entre instâncias, não funciona em Docker (o `localhost` do container não é o host) e qualquer mudança de porta exige mudar o gateway.

**Solução:** usar o load balancer por nome do serviço:

```properties
spring.cloud.gateway.server.webmvc.routes[0].uri=lb://company-service
spring.cloud.gateway.server.webmvc.routes[1].uri=lb://job-service
spring.cloud.gateway.server.webmvc.routes[2].uri=lb://review-service
```

Arquivo `.yml` também ajuda: com 6 rotas, os índices `routes[3].filters[0]` ficam difíceis de manter.

### 🟠 5.2 Actuator dos serviços exposto pelo gateway

**Problema:** as rotas `/{servico}/actuator/**` deixam qualquer cliente externo acessar o Actuator de todos os serviços. No job, isso inclui `health` com `show-details=ALWAYS`.

**Por que está errado:** o Actuator é uma interface operacional. Ele mostra versão, dependências, estado do banco e do circuit breaker: informação útil para um atacante e sem utilidade para o usuário da API.

**Solução:** remover essas rotas do gateway e acessar o Actuator pela rede interna, ou proteger com autenticação (Spring Security) e restringir a `health` com `show-details=when-authorized`.

### 🟡 5.3 Outros pontos

| Problema | Por que | Solução |
|---|---|---|
| `logging.level.org.springframework.cloud.gateway=TRACE` | Log enorme e possível vazamento de headers/tokens | `INFO` por padrão; `TRACE` só num perfil `dev` |
| Endpoint interno (`/reviews/averagerating`) acessível de fora pelo padrão `/companies/*/reviews/**` | Endpoints de uso entre serviços não deveriam ser públicos | Ver 4.5; se ficar interno, bloquear no gateway |
| Sem autenticação/autorização | Qualquer um cria, altera e apaga empresas | Spring Security com OAuth2 Resource Server (JWT) no gateway, quando o projeto chegar nessa etapa |
| Sem rate limiting, CORS ou timeouts | Proteções que normalmente ficam no gateway | Avaliar conforme a evolução |
| Sem Dockerfile nem perfil `docker` | Não pode entrar no compose | Ver 8.9 |

---

## 6. config-server

**Bom:** `@EnableConfigServer`, Git como backend e registro no Eureka.

| Problema | Por que | Solução |
|---|---|---|
| Só o `job_service` usa o Config Server | Os outros repetem as mesmas propriedades (Eureka, telemetry, datasource). Uma mudança exige editar N arquivos | Adicionar `spring-cloud-starter-config` e `spring.config.import=optional:configserver:` em todos; mover as configurações comuns para `application.properties` no repositório de config, e as específicas para `{servico}.properties` |
| Sem `default-label` nem `clone-on-start` | O branch padrão fica implícito e um erro de clone só aparece na primeira requisição | `spring.cloud.config.server.git.default-label=main` e `clone-on-start=true` |
| Senhas no repositório de configuração (se forem para lá) | Segredo em Git | Variáveis de ambiente, `{cipher}` do Config Server ou um cofre (Vault) |

---

## 7. service-registry

**Bom:** configuração mínima e correta de um Eureka standalone (`register-with-eureka=false`, `fetch-registry=false`).

| Problema | Por que | Solução |
|---|---|---|
| `spring-boot-starter-webmvc` declarado explicitamente | O starter do Eureka Server já traz o web | Pode remover (opcional) |
| `eureka.instance.hostname=localhost` fixo | Não funciona dentro do Docker | Externalizar por perfil/variável |
| Sem Dockerfile | Não pode entrar no compose | Ver 8.9 |

---

## 8. Pontos transversais (todos os serviços)

### 🟠 8.1 Bean Validation ausente

**Problema:** nenhum serviço tem `spring-boot-starter-validation` nem `@Valid`.

**Por que está errado:** dado inválido chega ao banco e vira **500** (constraint violada) em vez de **400** com uma mensagem clara.

**Solução:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

E `@Valid @RequestBody XxxRequest request` nos controllers, com as anotações nos records (exemplos em 2.4, 3.5 e 4.6).

### 🟠 8.2 Tratamento de erros centralizado com `ProblemDetail`

**Problema:** cada controller trata "não encontrado" com `if (boolean)` / `Optional`, e qualquer outra exceção vira um 500 genérico. Os formatos de erro variam.

**Por que está errado:** erro é parte do contrato da API. O cliente precisa de um formato único. A RFC 9457 (Problem Details) é o padrão, e o Spring já tem suporte nativo.

**Solução:** exceções de domínio + um `@RestControllerAdvice` por serviço:

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) {
        super("%s com id %s não encontrado".formatted(resource, id));
    }
}

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
```

Estender `ResponseEntityExceptionHandler` já devolve erros de validação (`@Valid`) como `ProblemDetail` com status 400. Também dá para ligar `spring.mvc.problemdetails.enabled=true`.

Com isso, os controllers ficam lineares:

```java
@GetMapping("/{companyId}")
public CompanyResponse findById(@PathVariable Long companyId) {
    return companyService.findById(companyId); // lança 404 se não existir
}
```

### 🟠 8.3 Testes

**Problema:** cada serviço só tem o `contextLoads()` gerado pelo Initializr.

**Por que está errado:** vários dos bugs acima (2.1, 2.2, 4.1, 3.1) seriam pegos por testes simples.

**Solução:**
- **Unitários** dos services com Mockito.
- **`@WebMvcTest`** dos controllers: status, `Location`, validação, formato do erro.
- **`@DataJpaTest`** com **Testcontainers** (PostgreSQL) para os repositórios.
- **Testes de contrato** entre job → company (Spring Cloud Contract ou WireMock), para garantir que o `CompanyDto` do job continua batendo com a resposta do company.

### 🟠 8.4 Schema com `ddl-auto=update`

**Problema:** o Hibernate altera o schema sozinho em todos os serviços.

**Por que está errado:** o `update` nunca remove nem renomeia colunas, não é versionado nem revisável, e pode fazer alterações destrutivas em produção.

**Solução:** **Flyway** (`spring-boot-starter-flyway` no Boot 4) com scripts `V1__create_companies.sql` etc., e `spring.jpa.hibernate.ddl-auto=validate`.

### 🟠 8.5 Credenciais em texto puro

**Problema:** `admin/admin` e `guest/guest` no `application.properties` de todos os serviços.

**Solução:** `spring.datasource.password=${DB_PASSWORD}` com valor vindo de variável de ambiente (`.env` fora do Git, secrets do Docker/Kubernetes).

### 🟡 8.6 Prefixo `I` nas interfaces

**Problema:** `ICompanyService`, `IJobService`, `IReviewService`.

**Por que:** é convenção de C#/.NET. Em Java, o padrão é a interface com o nome do conceito (`CompanyService`) e a implementação com sufixo (`CompanyServiceImpl`), ou nenhuma interface quando só existe uma implementação: o Spring cria proxies de classe sem problema e o Mockito também faz mock de classe.

**Solução:** renomear para `CompanyService` / `CompanyServiceImpl`, ou remover as interfaces. Mais importante que o nome é ser consistente: hoje dois dos três serviços injetam a classe concreta mesmo com a interface existindo (2.7 e 4.7).

### 🟡 8.7 Documentação da API

**Problema:** não há OpenAPI/Swagger.

**Solução:** `springdoc-openapi-starter-webmvc-ui` em cada serviço (verificar a versão compatível com o Boot 4) e, opcionalmente, agregação no gateway. Os records com Bean Validation já geram um schema rico.

### 🟡 8.8 Boilerplate de getters/setters

**Problema:** DTOs e entidades com dezenas de linhas de getters/setters.

**Solução:** `record` para todos os DTOs e mensagens (Java 21). Nas entidades JPA (que não podem ser records), manter os getters/setters ou usar Lombok (`@Getter @Setter`, evitando `@Data` em entidades).

### 🟡 8.9 Containerização incompleta

**Problema:** só o `company_service` tem `Dockerfile` e perfil `docker`. O `docker-compose.yaml` só sobe a infraestrutura (Postgres, Rabbit, OTel, Zipkin), nenhum serviço da aplicação.

**Solução:**
- Replicar o `Dockerfile` (que está bom) e o `.dockerignore` nos outros 5 módulos.
- Adicionar os serviços ao compose, com `depends_on` + `condition: service_healthy` (registry → config → serviços → gateway) e `SPRING_PROFILES_ACTIVE=docker`.
- Considerar `spring-boot:build-image` (Buildpacks) como alternativa sem Dockerfile.
- No Dockerfile, rodar como usuário não-root e adicionar `HEALTHCHECK` com o Actuator.
- A rede do compose se chama `postgres` mas liga todos os containers: renomear para algo como `jobapp`.
- Fixar as tags das imagens `openzipkin/zipkin` e `otel/opentelemetry-collector-contrib` (hoje sem tag = `latest`).

### 🟡 8.10 Consistência de versionamento e estrutura

- Sem versionamento da API (`/api/v1/companies`). Não é obrigatório, mas vale definir antes que existam clientes externos.
- Nomes de módulos misturam `snake_case` (`company_service`) e `kebab-case` (`api-gateway`). O padrão Maven/Spring é kebab-case.
- O `ReviewMessage` é duplicado em review e company. Em microsserviços isso é aceitável (evita uma biblioteca compartilhada acoplando deploys), mas o contrato precisa ser documentado e testado (8.3).
- `sampling.probability=1.0` está certo para estudo. Em produção, usar algo como `0.1`.

---

## 9. Ordem sugerida de execução

1. **Bugs** (rápidos e de alto impacto): 2.1, 2.2, 2.3, 4.1, 3.1/4.2, 3.2/4.3.
2. **Base de qualidade**: validação (8.1) + `@RestControllerAdvice` com `ProblemDetail` (8.2). Isso simplifica tudo o que vem depois.
3. **DTOs** (records) em company e review, e reorganização dos DTOs do job (2.4, 3.5, 3.6, 4.6), com 204/200 nos DELETE/PUT (2.5).
4. **Comunicação entre serviços**: fallback correto (3.3), 404 do Feign (3.4), N+1 (3.7), média na mensagem + DLQ + eventos em update/delete (2.6, 2.8, 4.3, 4.4).
5. **Gateway com `lb://`** e sem Actuator público (5.1, 5.2).
6. **Testes** (8.3) cobrindo os pontos acima.
7. **Infra**: Flyway, segredos, Config Server para todos, compose completo (8.4, 8.5, 6, 8.9).
8. **Limpeza**: itens 🟡 restantes.
