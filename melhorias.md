# Guia de melhorias: jobapp_ms

Este documento é uma revisão do projeto, pensada para quem está começando com microsserviços e Spring. A ideia não é apontar erros, e sim explicar **o que já está bem feito**, **o que pode melhorar** e, principalmente, **por quê**. Entender o motivo é o que faz a boa prática virar hábito.

Stack considerada: Java 21, Spring Boot 4.1, Spring Cloud 2025.1.

## Como ler este guia

Cada item segue sempre o mesmo roteiro:

- **O que acontece hoje**: o comportamento atual do código.
- **Por que isso é um problema**: a explicação, em linguagem simples.
- **Como corrigir**: um caminho sugerido, geralmente com um trecho de código.

Os itens têm uma cor que indica a prioridade:

- 🔴 **Bug**: o sistema se comporta de forma errada hoje. Corrija primeiro.
- 🟠 **Importante**: funciona, mas vai trazer dor de cabeça (contrato da API ruim, acoplamento, falhas em produção).
- 🟡 **Melhoria**: deixa o código mais limpo, legível e dentro das convenções.
- ✅ **Resolvido**: já foi corrigido. Fica registrado para mostrar a evolução.

> 💡 Não se assuste com o tamanho da lista. Projeto de estudo é para isso mesmo. A seção [11. Ordem sugerida](#11-ordem-sugerida) mostra por onde começar, e o [Glossário](#glossário) no final explica os termos técnicos.

## Sumário

1. [O que já está bom](#1-o-que-já-está-bom)
2. [O que já foi corrigido](#2-o-que-já-foi-corrigido)
3. [company_service](#3-company_service)
4. [job_service](#4-job_service)
5. [review_service](#5-review_service)
6. [api-gateway](#6-api-gateway)
7. [config-server](#7-config-server)
8. [service-registry](#8-service-registry)
9. [Docker e Kubernetes](#9-docker-e-kubernetes)
10. [Pontos que valem para todos os serviços](#10-pontos-que-valem-para-todos-os-serviços)
11. [Ordem sugerida](#11-ordem-sugerida)
12. [Glossário](#glossário)

---

## 1. O que já está bom

Antes de falar do que melhorar, vale reconhecer: a base do projeto é sólida e segue várias práticas que muita aplicação em produção não segue.

| O que está bom | Por que isso importa | Onde |
|---|---|---|
| URLs com **substantivos no plural** (`/companies`, `/jobs`, `/reviews`), sem verbos como `/getCompany` | É a convenção REST. Quem usa a API adivinha os caminhos sem ler documentação | Todos os controllers |
| **Recursos aninhados**: `/companies/{companyId}/jobs` | A URL mostra que a vaga *pertence* a uma empresa | job, review |
| **Verbos HTTP corretos**: GET lê, POST cria, PUT atualiza, DELETE remove | Cada verbo tem um significado combinado entre cliente e servidor | Todos |
| `POST` devolve **201 Created** com o header **`Location`** | O cliente recebe o endereço do recurso recém-criado | Todos |
| **404** quando o recurso não existe | O cliente sabe distinguir "não existe" de "deu erro" | Todos |
| **Injeção de dependência pelo construtor**, com campos `final` | Deixa claro do que a classe precisa, impede reatribuição e facilita testes | Quase todos (exceção em `JobService`) |
| Camadas separadas: **controller → service → repository** | Cada classe tem uma responsabilidade só | Todos |
| Services devolvendo `Optional` em vez de `null` | Obriga quem chama a tratar o caso "não encontrado" | company, review |
| **Um banco por serviço** (`companydb`, `jobdb`, `reviewdb`) | Regra de ouro dos microsserviços: cada serviço é dono dos seus dados | Infra |
| **Service discovery** com Eureka + **OpenFeign** | Os serviços se encontram pelo nome, sem IP fixo no código | Todos |
| **Circuit breaker** (Resilience4j) com fallback e health indicator | Se o company-service cair, o job-service não cai junto | job |
| **Mensageria assíncrona** com RabbitMQ e conversor JSON | O review não precisa esperar o company responder para salvar uma avaliação | review → company |
| Mensagem do Rabbit com DTO próprio (`ReviewMessage`), e não a entidade | A mensagem é um contrato. Mudar a tabela não quebra o outro serviço | review, company |
| **Observabilidade**: OpenTelemetry, traceId/spanId no log, Zipkin, Actuator | Dá para seguir uma requisição passando por vários serviços | Todos |
| **Dockerfile multi-stage** com cache do Maven e imagem final só com a JRE | Imagem menor, build mais rápido e sem ferramentas de compilação em produção | Todos os módulos |
| `docker-compose` com healthcheck, `depends_on` com condição e portas do banco presas em `127.0.0.1` | Sobe tudo na ordem certa e não expõe o banco para a rede | Infra |
| Perfil `docker` separado (`application-docker.properties`) | A mesma imagem roda local ou em container só trocando o perfil | Todos |
| Manifests de **Kubernetes** com `startupProbe`, `livenessProbe`, `readinessProbe`, `resources` e `MaxRAMPercentage` | São exatamente os cuidados que uma aplicação Java precisa para rodar bem num cluster | k8s |
| `Config Server` lendo de um repositório Git | A configuração fica versionada e fora do código | config-server |

---

## 2. O que já foi corrigido

Itens apontados na revisão anterior que já foram resolvidos. Bom trabalho! 🎉

| ✅ Item | O que foi feito |
|---|---|
| `company_service` não subia por falta de `@EnableFeignClients` | Anotação adicionada em `CompanyServiceApplication` |
| `updateCompany` ignorava a descrição | Agora copia `name`, `employees` e `description` |
| Empresa duplicada devolvia 201 sem criar nada | Agora lança `CompanyAlreadyExistsException` (mas ainda falta devolver 409, ver 3.1) |
| DELETE e PUT de empresa devolviam texto solto | Agora devolvem **204 No Content** |
| Imports sem uso em `Company` | Removidos |
| DTOs de empresa | `CompanyRequest` e `CompanyResponse` criados como `record` (ainda não estão em uso, ver 3.3) |
| `service-registry` com hostname fixo | O perfil `docker` sobrescreve para `service-registry` |
| Só o company tinha Dockerfile | Todos os 6 módulos têm `Dockerfile` e `.dockerignore`, e o compose sobe a aplicação inteira |

---

## 3. company_service

### 🔴 3.1 Empresa duplicada vira erro 500

**O que acontece hoje:** o service lança `CompanyAlreadyExistsException`, mas o controller captura e embrulha numa `RuntimeException`:

```java
} catch (CompanyAlreadyExistsException e) {
    throw new RuntimeException(e);   // vira 500 Internal Server Error
}
```

**Por que isso é um problema:** 500 significa "o servidor quebrou". Mas aqui nada quebrou: o cliente pediu algo que conflita com um dado existente. O status certo é **409 Conflict**. Com 500, quem usa a API acha que é um bug do servidor e não sabe que precisa trocar o nome.

**Como corrigir:**

1. Faça a exceção herdar de `RuntimeException`. Exceções *checked* (`extends Exception`) obrigam a usar `throws` e `try/catch` em todo o caminho, e no Spring a convenção é usar exceções *unchecked* para erros de negócio.
2. Remova o `try/catch` do controller.
3. Traduza a exceção para 409 num `@RestControllerAdvice` (ver [10.2](#-102-tratamento-de-erros-num-lugar-só-com-problemdetail)).

```java
public class CompanyAlreadyExistsException extends RuntimeException {
    public CompanyAlreadyExistsException(String name) {
        super("Já existe uma empresa com o nome '%s'".formatted(name));
    }
}
```

Também vale colocar `@Column(unique = true)` no `name`. A verificação no código sozinha não impede que duas requisições simultâneas criem o mesmo nome: as duas checam "não existe", e as duas salvam. A restrição no banco é a última linha de defesa.

### 🔴 3.2 A validação ainda não funciona

**O que acontece hoje:** foram dois deslizes pequenos, mas que juntos fazem a validação não rodar:

1. O `pom.xml` recebeu a dependência `jakarta.validation-api`. Ela traz **só as anotações** (`@NotBlank`, `@Size` etc.), mas não traz ninguém para *executá-las*.
2. Em `CompanyRequest`, o campo `employees` (um `Integer`) usa `@NotBlank`. Essa anotação só serve para texto (`String`). Quando a validação estiver ativa, isso causa uma `UnexpectedTypeException`.

**Por que isso é um problema:** sem a implementação, o Spring simplesmente ignora as anotações. Dado inválido passa direto, chega ao banco e estoura como erro 500.

**Como corrigir:**

```xml
<!-- troque jakarta.validation-api por: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

O starter já traz as anotações **e** o Hibernate Validator, que é quem executa as regras.

```java
public record CompanyRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero Integer employees,   // @NotNull para números
        @NotBlank @Size(max = 1000) String description) {}
```

Regra rápida: `@NotBlank` para `String`, `@NotEmpty` para listas, `@NotNull` para o resto.

### 🟠 3.3 A API ainda recebe e devolve a entidade `Company`

**O que acontece hoje:** os DTOs `CompanyRequest` e `CompanyResponse` foram criados, mas o controller e o service continuam usando a entidade `Company` (a classe com `@Entity`).

**Por que isso é um problema:** a entidade representa a **tabela**. O DTO representa o **contrato da API**. Misturar os dois causa problemas como:

- O cliente pode mandar `id` e `averageRating` no POST. A média deveria ser calculada só pelo sistema, a partir das reviews.
- Qualquer mudança na tabela (uma coluna nova, um relacionamento) muda o JSON da API sem ninguém perceber.
- No futuro, relacionamentos `LAZY` podem causar `LazyInitializationException` ou JSON em loop infinito.

**Como corrigir:** ligar os DTOs que já existem. O controller passa a trabalhar só com DTOs, e um mapper faz a conversão:

```java
@PostMapping
public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
    CompanyResponse created = companyService.create(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
    return ResponseEntity.created(location).body(created);
}
```

```java
public final class CompanyMapper {
    private CompanyMapper() {}

    public static Company toEntity(CompanyRequest r) {
        return new Company(r.name(), r.employees(), r.description());
    }

    public static CompanyResponse toResponse(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getEmployees(),
                c.getDescription(), c.getAverageRating());
    }
}
```

Repare no `@Valid`: é ele que manda o Spring validar o corpo da requisição.

### 🟠 3.4 Ao receber a mensagem, o company chama o review de volta

**O que acontece hoje:** quando chega uma mensagem do Rabbit, `updateCompanyRating` faz uma chamada HTTP (Feign) para o review-service perguntar a média.

**Por que isso é um problema:** a mensageria serve para os serviços **não dependerem** um do outro estar no ar. Com a chamada de volta, o company volta a depender do review, e ainda sem circuit breaker nem timeout. Se o review estiver fora, o listener falha, o Rabbit reentrega a mensagem, falha de novo... e assim indefinidamente (não há DLQ, ver 3.6).

**Como corrigir (escolha uma):**

1. **Recomendado:** o review-service calcula a média e já envia na mensagem. O company só grava. É a solução mais simples e a mais desacoplada.
2. Guardar `reviewCount` e `ratingSum` na `Company` e atualizar a cada evento. Funciona, mas exige eventos também para edição e exclusão de review.

```java
public record CompanyRatingUpdatedEvent(Long companyId, Double averageRating, long reviewCount) {}
```

Com isso, o `ReviewClient` e a dependência do OpenFeign podem sair do company_service.

### 🟠 3.5 O consumer depende da classe concreta, e o service conhece o formato da mensagem

**O que acontece hoje:**

- `ReviewMessageConsumer` injeta `CompanyService` (a classe), e não `ICompanyService` (a interface).
- O método `updateCompanyRating` da interface recebe `ReviewMessage`, que é um DTO da camada de mensageria.

**Por que isso é um problema:** se existe uma interface, depender da classe concreta anula o motivo dela existir. E o service não deveria saber como é o formato da mensagem do Rabbit: se a mensagem mudar, o service muda junto. Cada camada deve conhecer só o necessário.

**Como corrigir:** o consumer "desembrulha" a mensagem e passa ao service apenas os valores:

```java
@RabbitListener(queues = "${app.messaging.rating-queue}")
public void consume(CompanyRatingUpdatedEvent event) {
    companyService.updateAverageRating(event.companyId(), event.averageRating());
}
```

### 🟠 3.6 Configuração do RabbitMQ

**O que acontece hoje:**

- O nome `"companyRatingQueue"` está escrito à mão em 4 lugares, nos dois serviços.
- O `RabbitTemplate` é criado manualmente. No company ele nem é usado, porque esse serviço só consome mensagens.
- `RabbitMQConfiguration` está no pacote `messaging`, junto com o consumer.
- Não existe **dead-letter queue (DLQ)**.

**Por que isso é um problema:**

- String repetida é um convite a erro de digitação. Basta errar uma letra em um dos lugares e as mensagens somem.
- O Spring Boot já cria o `RabbitTemplate` sozinho e usa o `MessageConverter` que você declarar como bean. Recriar à mão é código a mais para manter.
- Sem DLQ, uma mensagem que sempre falha (por exemplo, um `companyId` que não existe) fica sendo reprocessada para sempre, ou é descartada sem deixar rastro.

**Como corrigir:**

```properties
app.messaging.rating-queue=company.rating.queue
# tenta 3 vezes; se continuar falhando, manda para a DLQ em vez de reenfileirar
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.default-requeue-rejected=false
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
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
    // sem RabbitTemplate manual: o do Spring Boot já usa este converter
}
```

Uma sugestão de organização: mover a classe para um pacote `config`.

### 🟠 3.7 `GET /companies` devolve a tabela inteira

**O que acontece hoje:** `findAll()` traz todas as empresas de uma vez.

**Por que isso é um problema:** com 10 empresas, tudo bem. Com 100 mil, a resposta fica enorme, lenta e consome muita memória.

**Como corrigir:** usar paginação, que o Spring Data já oferece pronta:

```java
@GetMapping
public Page<CompanyResponse> findAll(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return companyService.findAll(pageable);
}
```

O cliente passa a pedir `GET /companies?page=0&size=20&sort=name`.

### 🟡 3.8 Detalhes de código limpo

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| Location do POST usa `company.getId()` (o parâmetro), e não o objeto devolvido pelo service | Funciona por acaso, porque o JPA preenche o id no mesmo objeto. Com DTOs, deixa de funcionar | Usar o id do objeto retornado: `created.id()` |
| DELETE e PUT declaram `ResponseEntity<String>` mas devolvem 204 sem corpo | O tipo engana quem lê o código | `ResponseEntity<Void>` |
| `findByNameIgnoreCase` devolve `Company` e é comparado com `null` | Carrega a empresa inteira só para saber se ela existe | `boolean existsByNameIgnoreCase(String name)` |
| `companyExists` é público na interface | É um detalhe interno do service | Tornar `private` |
| `deleteCompany` e `updateCompany` devolvem `boolean` | O controller precisa converter `false` em 404 em cada método | Lançar `CompanyNotFoundException` e tratar no advice (10.2) |
| `findById(...).orElse(null)` seguido de `if (x != null)` | É o que o `Optional` existe para evitar | `findById(id).orElseThrow(() -> new ResourceNotFoundException(...))` |
| Métodos de escrita sem `@Transactional` | Buscar, alterar e salvar deveriam ser uma operação só | `@Transactional` na escrita e `@Transactional(readOnly = true)` na leitura. Com a transação, o `save` depois dos setters nem é mais necessário |
| `@GetMapping()` com parênteses vazios | Ruído visual | `@GetMapping` |
| `this.companyService` em alguns métodos e `companyService` em outros | Inconsistência | Escolher um estilo (sem `this.` é o mais comum) |
| `ReviewMessage` com getters e setters | Muito código para algo que só carrega dados | Transformar em `record` |

---

## 4. job_service

### 🔴 4.1 O `companyId` da URL é ignorado

**O que acontece hoje:** `findJobById`, `updateJob` e `deleteJob` recebem `companyId` na URL, mas não usam. Resultado:

- `GET /companies/1/jobs/5` devolve a vaga 5 mesmo que ela seja da empresa 2.
- `DELETE /companies/999/jobs/5` apaga a vaga 5, de qualquer empresa.

**Por que isso é um problema:** num recurso aninhado, a URL inteira identifica o recurso. `/companies/1/jobs/5` quer dizer "a vaga 5 **da empresa 1**". Se ela não pertence à empresa 1, o certo é responder 404.

**Como corrigir:** buscar sempre pelos dois ids:

```java
// JobRepository
Optional<Job> findByIdAndCompanyId(Long id, Long companyId);
```

```java
public JobResponse findById(Long companyId, Long jobId) {
    Job job = jobRepository.findByIdAndCompanyId(jobId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Vaga", jobId));
    ...
}
```

### 🔴 4.2 O PUT permite mudar a vaga de empresa

**O que acontece hoje:** `updateJob` recebe a entidade `Job` e faz `jobDb.setCompanyId(job.getCompanyId())`. Se o JSON não tiver `companyId`, a vaga fica **sem empresa** (`null`).

**Por que isso é um problema:** a empresa já está na URL. Aceitar outra no corpo cria duas "fontes de verdade" que podem discordar. E um campo esquecido apaga o vínculo.

**Como corrigir:** usar um DTO de entrada **sem** `id` e `companyId`, e nunca alterar o `companyId` no update (ver 4.5).

### 🔴 4.3 Quando o company-service cai, a lista de vagas vem vazia

**O que acontece hoje:** o fallback do circuit breaker (`companyBreakerFallback`) devolve `new ArrayList<>()`, e o cliente recebe `200 []`.

**Por que isso é um problema:** o cliente entende "essa empresa não tem vagas". Mas as vagas existem, estão no banco do próprio job-service. Só os dados da empresa ficaram indisponíveis. O fallback escondeu a falha e entregou uma informação falsa.

**Como corrigir (escolha uma):**

- **Degradar com elegância:** devolver as vagas sem os dados da empresa.
- **Falhar de forma clara:** lançar uma exceção que vira **503 Service Unavailable**.

```java
public List<JobResponse> companyFallback(Long companyId, Throwable ex) {
    log.warn("company-service indisponível; devolvendo vagas sem dados da empresa", ex);
    return jobRepository.findByCompanyId(companyId).stream()
            .map(job -> JobMapper.toResponse(job, null))
            .toList();
}
```

### 🔴 4.4 Empresa que não existe vira erro 500 (ou vaga órfã)

**O que acontece hoje:**

- `addJob` não confere se a empresa existe. Dá para criar vaga em `/companies/999/jobs`.
- Em `findJobById`, se o company-service responder 404, o Feign lança `FeignException.NotFound`, que chega ao cliente como **500**. Esse método também não tem circuit breaker.

**Por que isso é um problema:** como cada serviço tem seu banco, não existe chave estrangeira entre `jobs` e `companies`. Quem garante que a empresa existe é o código. E um 404 de outro serviço não é um erro interno do seu servidor.

**Como corrigir:**

- No `addJob`, consultar o company-service antes de salvar e devolver 404 se a empresa não existir.
- Tratar `FeignException.NotFound` no `@RestControllerAdvice` (ou com um `ErrorDecoder` do Feign).
- Colocar as chamadas ao company num componente separado (ex.: `CompanyGateway`) com o `@CircuitBreaker`. Assim todos os métodos ficam protegidos sem repetir a anotação.

### 🟠 4.5 DTOs com nomes e papéis confusos

**O que acontece hoje:**

- `CreateJobDto` é usado como **entrada** e também como **resposta** do POST, e tem `id` e `companyId`, que o cliente não deveria enviar.
- O PUT recebe a **entidade** `Job`, e não um DTO.
- `JobDto` e `CreateJobDto` têm exatamente os mesmos campos.
- O POST devolve um formato (`CreateJobDto`) e o GET devolve outro (`JobWithCompanyDto`) para o mesmo recurso.
- `CompanyDto` não tem `id`.

**Por que isso é um problema:** o DTO de entrada é a lista do que o cliente **pode** mandar. Se ele tem `id`, parece que o cliente escolhe o id. E o mesmo recurso deveria ter sempre o mesmo formato, para quem usa a API ter um modelo só na cabeça.

**Como corrigir:** um DTO para entrada e um para saída, usados em todos os endpoints:

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

### 🟠 4.6 O JSON mostra nomes de classe Java: `jobDto` e `companyDto`

**O que acontece hoje:** a resposta tem o formato `{"jobDto": {...}, "companyDto": {...}}`.

**Por que isso é um problema:** "Dto" é um detalhe interno do Java vazando para quem consome a API. O cliente quer ver `job` e `company`, ou a vaga com a empresa dentro dela.

**Como corrigir:** o `JobResponse` do item 4.5 já resolve: a vaga com o campo `company` embutido.

### 🟠 4.7 Uma chamada HTTP por vaga em `findAll` (problema N+1)

**O que acontece hoje:** o loop chama `companyClient.getCompany(...)` **para cada vaga**. Mas todas as vagas da lista são da mesma empresa (a da URL).

**Por que isso é um problema:** 50 vagas viram 50 requisições HTTP idênticas. Cada uma soma tempo de resposta e conta como uma falha separada para o circuit breaker.

**Como corrigir:** buscar a empresa **uma vez só**:

```java
CompanySummary company = companyClient.getCompany(companyId);
return jobRepository.findByCompanyId(companyId).stream()
        .map(job -> JobMapper.toResponse(job, company))
        .toList();
```

Vale até se perguntar se a lista precisa trazer a empresa em cada item: o cliente já sabe de qual empresa está listando as vagas.

### 🟠 4.8 Salário guardado como texto

**O que acontece hoje:** `minSalary` e `maxSalary` são `String`.

**Por que isso é um problema:** não dá para ordenar por salário, filtrar por faixa nem validar que o mínimo é menor que o máximo. E o banco aceita `"abc"` como salário.

**Como corrigir:** usar `BigDecimal` (o tipo certo para dinheiro em Java; `double` tem erros de arredondamento) e validar a faixa:

```java
@Column(nullable = false, precision = 12, scale = 2)
private BigDecimal minSalary;
```

```java
// dentro do JobRequest
@AssertTrue(message = "minSalary deve ser menor ou igual a maxSalary")
boolean isSalaryRangeValid() {
    return minSalary == null || maxSalary == null || minSalary.compareTo(maxSalary) <= 0;
}
```

### 🟠 4.9 DELETE e PUT devolvem texto solto (e com erro de digitação)

**O que acontece hoje:** as respostas são `"Job deleted succesfully"` e `"Job updated succesfully"` ("successfully" tem dois "s").

**Por que isso é um problema:** o status HTTP já diz se deu certo. Uma string solta não é JSON e obriga o cliente a tratar texto.

**Como corrigir:** o mesmo que já foi feito no company: **204** no DELETE e **200 com o `JobResponse`** atualizado no PUT (ou 204).

### 🟡 4.10 Detalhes de código limpo

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| `@Autowired RestTemplate restTemplate` no `JobService`, sem uso, e o `AppConfig` criando esse `RestTemplate` | Código morto. O Feign já faz esse trabalho | Remover o campo e a classe `AppConfig` |
| `companyClient` não é `final` | Pode ser reatribuído sem querer | `private final CompanyClient companyClient;` |
| `findById(...).stream().filter(job -> job.getId().equals(jobId))` | O filtro é redundante: `findById` já garante o id | `findByIdAndCompanyId(...).orElseThrow(...)` |
| `findJobById` e `getJob` devolvem `null` | Obriga `if (x == null)` em quem chama | `Optional` ou exceção |
| `JobMapper` é `@Component`, mas só tem métodos `static` | Contraditório: o bean é criado e nunca injetado | Ou classe utilitária `final` com construtor privado, ou `@Component` com métodos normais injetado no service. O MapStruct gera isso automaticamente |
| O mapeamento `Job → JobDto` fica dentro do `JobService` (`getJobWithCompanyDto`) | O mapper existe, mas não é usado nesse caso | Mover para o mapper |
| `addJob` altera o DTO recebido (`createJobDto.setCompanyId(...)`) | Mexer no parâmetro de entrada é um efeito colateral inesperado | `JobMapper.toEntity(request, companyId)` |
| O controller chama `JobMapper.mapFromClass` | Parte do mapeamento no controller, parte no service | O service já devolve o DTO de resposta pronto |
| `new ResponseEntity<>(..., HttpStatus.OK)` em um método e `ResponseEntity.ok(...)` nos outros | Inconsistente | `ResponseEntity.ok(...)` |
| `import jakarta.persistence.Column` em `JobDto` e `Optional` em `IJobService`, sem uso | Ruído. Um import de JPA num DTO ainda sugere acoplamento | Remover |
| `Job.companyId` sem `@Column(nullable = false)` | Vaga sem empresa é um estado inválido | `@Column(nullable = false)` e um índice em `company_id` |
| `registerHealthIndicator` e `register-health-indicator` declarados ao mesmo tempo | A mesma propriedade escrita de dois jeitos | Manter só uma (kebab-case é o padrão do Spring) |
| `management.endpoint.health.show-details=ALWAYS` | Mostra detalhes internos (banco, disco, circuit breaker) para qualquer pessoa | `when-authorized` |
| Feign sem timeout | Uma chamada lenta segura a thread por muito tempo | `spring.cloud.openfeign.client.config.company-service.connect-timeout` e `read-timeout` |

---

## 5. review_service

### 🔴 5.1 Atualizar uma review apaga a descrição

**O que acontece hoje:** em `updateReview`:

```java
reviewUpdate.setDescription(review.getTitle());   // copia o título para a descrição!
```

**Por que isso é um problema:** erro de copiar e colar. Toda atualização sobrescreve a descrição com o título.

**Como corrigir:** `reviewUpdate.setDescription(request.description());`

Esse é o tipo de erro que um teste simples pegaria na hora (ver [10.3](#-103-testes)).

### 🔴 5.2 O `companyId` da URL é ignorado

O mesmo problema do item 4.1. Aqui é ainda mais visível: `findById`, `deleteReview` e `updateReview` nem recebem o `companyId`. `DELETE /companies/1/reviews/7` apaga a review 7 de qualquer empresa.

**Como corrigir:** `Optional<Review> findByIdAndCompanyId(Long id, Long companyId);` e usar os dois ids em todos os métodos.

### 🔴 5.3 O PUT pode trocar a empresa, e a média não é recalculada

**O que acontece hoje:**

- `updateReview` copia `companyId` do corpo. Se o cliente não mandar, vira `null`.
- Só o POST envia mensagem para o company. Editar a nota (PUT) ou apagar uma review (DELETE) não avisa ninguém, e a `averageRating` da empresa fica errada.

**Por que isso é um problema:** é o mesmo caso de "duas fontes de verdade" do item 4.2. E a média é um **dado derivado**: qualquer operação que muda o conjunto de notas precisa disparar o recálculo.

**Como corrigir:** não aceitar `companyId` no corpo e publicar o evento no create, no update e no delete (ver 5.4).

### 🟠 5.4 A mensagem é enviada pelo controller

**O que acontece hoje:** `ReviewController.addReview` salva a review e, em seguida, chama `reviewMessageProducer.sendMessage(...)`.

**Por que isso é um problema:**

- "Ao criar uma review, avisar o company" é uma **regra de negócio**, e regra de negócio mora no service. O controller só deveria traduzir HTTP em chamadas ao service.
- Não há garantia de consistência: se o envio falhar, a review fica salva e a média nunca é atualizada.

**Como corrigir:** mover para o service e enviar a mensagem **só depois que a transação for confirmada** (commit). O Spring tem um recurso pronto para isso:

```java
@Service
@Transactional
public class ReviewService {
    private final ApplicationEventPublisher events;

    public ReviewResponse create(Long companyId, ReviewRequest request) {
        Review saved = repository.save(ReviewMapper.toEntity(request, companyId));
        events.publishEvent(new ReviewChangedEvent(companyId)); // evento interno
        return ReviewMapper.toResponse(saved);
    }
}

@Component
class ReviewEventsPublisher {
    // só roda se o commit deu certo
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReviewChangedEvent e) {
        Double avg = repository.averageRatingByCompanyId(e.companyId()).orElse(null);
        long count = repository.countByCompanyId(e.companyId());
        rabbitTemplate.convertAndSend(queue, new CompanyRatingUpdatedEvent(e.companyId(), avg, count));
    }
}
```

> 📚 Para ir além: quando é obrigatório que nenhuma mensagem se perca, o padrão usado no mercado é o **Transactional Outbox**. O evento é gravado numa tabela na mesma transação da review, e um processo separado lê essa tabela e publica no Rabbit.

### 🟠 5.5 O endpoint `/averagerating`

**O que acontece hoje:**

- A URL é `/companies/{id}/reviews/averagerating`, tudo junto e em minúsculas.
- A média é calculada **no controller**, carregando todas as reviews na memória.
- A resposta é um número solto (`4.25`), e não um objeto JSON.
- Sem reviews, devolve `0.0`, que não dá para distinguir de "a nota média é zero".

**Por que isso é um problema:**

- URLs REST usam `kebab-case` (palavras separadas por hífen).
- Cálculo é responsabilidade do service. E conta de média é algo que o banco faz muito melhor do que trazer todos os registros para o Java.
- Um número solto não pode ganhar campos novos depois sem quebrar quem já usa.

**Como corrigir:**

```java
// ReviewRepository: o banco calcula
@Query("select avg(r.rating) from Review r where r.companyId = :companyId")
Optional<Double> averageRatingByCompanyId(Long companyId);

long countByCompanyId(Long companyId);
```

```java
// ReviewController
@GetMapping("/rating-summary")
public RatingSummaryResponse ratingSummary(@PathVariable Long companyId) {
    return reviewService.ratingSummary(companyId);
}

public record RatingSummaryResponse(Long companyId, Double averageRating, long reviewCount) {}
```

Resultado: `GET /companies/1/reviews/rating-summary` → `{"companyId":1,"averageRating":4.25,"reviewCount":8}`, com `averageRating: null` quando não houver reviews.

Se a solução do item 3.4 for aplicada (a média vai na mensagem), o company deixa de precisar desse endpoint.

### 🟠 5.6 Entidade exposta na API e sem validação

**O que acontece hoje:** o controller recebe e devolve a entidade `Review`, e não há limite para a nota: dá para enviar `-50` ou `1000`.

**Por que isso é um problema:** os mesmos motivos do item 3.3. O cliente ainda pode enviar `id` e `companyId`.

**Como corrigir:**

```java
public record ReviewRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin("1.0") @DecimalMax("5.0") Double rating) {}

public record ReviewResponse(
        Long id, String title, String description,
        Double rating, Instant createdAt) {}
```

O `addReview` também deveria confirmar que a empresa existe antes de salvar, como no item 4.4.

### 🟠 5.7 O controller depende da classe concreta

**O que acontece hoje:** `ReviewController` injeta `ReviewService`, e não `IReviewService`.

**Como corrigir:** injetar a interface, ou remover a interface de vez (ver 10.6). O importante é ser consistente.

### 🟡 5.8 Detalhes de código limpo

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| `import jakarta.websocket.server.PathParam;` | É de WebSocket, não do Spring MVC, e nem é usado | Remover |
| `@GetMapping("{reviewId}")` sem barra e `@PutMapping("/{reviewId}")` com barra | Funciona, mas é inconsistente | Sempre `"/{reviewId}"` |
| O update de review responde `"Company updated successfully"` | Texto copiado de outro controller | 200 com `ReviewResponse`, ou 204 |
| DELETE e PUT devolvem texto | Ver 4.9 | 204 / 200 com DTO |
| `createdAt` é `LocalDateTime` | Não guarda fuso horário: o valor depende do relógio do servidor | `Instant` com `@CreationTimestamp` no lugar do `@PrePersist` manual |
| `rating` é `double` (primitivo) | Se o JSON não tiver `rating`, vira `0.0` sem nenhum erro | `Double` no DTO, com `@NotNull` |
| Construtor de `Review` sem `companyId` | Permite criar uma review "pela metade" | Incluir `companyId` no construtor |
| Sem `@Transactional`, `boolean` para "não encontrado", `findById` + `isEmpty` + `get` | Ver 3.8 | Mesmas sugestões |
| `RabbitTemplate` criado à mão | Ver 3.6 | Remover; manter só `Queue` e `MessageConverter` |
| Comentário `#postgresq` no `application.properties` | Erro de digitação | `#postgresql` |

---

## 6. api-gateway

### 🟠 6.1 As rotas usam endereço fixo em vez do Eureka

**O que acontece hoje:** as rotas apontam para `http://localhost:8081` e, no perfil `docker`, para `http://company-service:8081`. Por causa disso, o `application-docker.properties` precisou **repetir todas as 6 rotas** só para trocar o endereço.

**Por que isso é um problema:** o gateway já está registrado no Eureka, mas não usa a descoberta de serviços. Na prática:

- Não há balanceamento de carga: se você subir 3 instâncias do job-service, o gateway só conhece uma.
- Qualquer mudança de porta ou de nome exige mexer no gateway.
- A duplicação entre os dois arquivos é fácil de ficar desatualizada.

**Como corrigir:** o prefixo `lb://` (load balancer) faz o gateway perguntar ao Eureka onde o serviço está:

```properties
spring.cloud.gateway.server.webmvc.routes[0].uri=lb://company-service
spring.cloud.gateway.server.webmvc.routes[1].uri=lb://job-service
spring.cloud.gateway.server.webmvc.routes[2].uri=lb://review-service
```

Bônus: como `lb://company-service` funciona igual no local e no Docker, as rotas podem sair do `application-docker.properties`. Também vale migrar para `application.yml`: com várias rotas, os índices `routes[3].filters[0]` ficam difíceis de ler.

### 🟠 6.2 O Actuator dos serviços fica acessível pelo gateway

**O que acontece hoje:** as rotas `/{servico}/actuator/**` permitem que qualquer cliente externo acesse o Actuator de todos os serviços.

**Por que isso é um problema:** o Actuator é uma ferramenta de operação. Ele mostra versão, dependências, estado do banco e do circuit breaker. Isso é útil para um atacante e não tem utilidade para quem consome a API.

**Como corrigir:** remover essas rotas e acessar o Actuator pela rede interna (no Kubernetes, por exemplo, com `kubectl port-forward`). Se precisar mesmo expor, proteger com Spring Security.

### 🟡 6.3 Outros pontos

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| `logging.level.org.springframework.cloud.gateway=TRACE` | Gera um volume enorme de log e pode expor headers e tokens | `INFO` por padrão; `TRACE` só num perfil `dev` |
| O endpoint de média (`/reviews/averagerating`) é acessível de fora | Endpoints usados só entre serviços não deveriam ser públicos | Ver 5.5 |
| Sem autenticação | Qualquer pessoa cria, altera e apaga empresas | Quando o projeto chegar nessa etapa: Spring Security com OAuth2 Resource Server (JWT) no gateway |
| Sem rate limiting, CORS e timeouts | São proteções que normalmente ficam no gateway | Avaliar conforme o projeto evoluir |

---

## 7. config-server

**O que está bom:** `@EnableConfigServer`, Git como fonte das configurações e registro no Eureka.

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| Só o `job_service` usa o Config Server | Os outros serviços repetem as mesmas propriedades (Eureka, telemetria, datasource). Uma mudança exige editar vários arquivos | Adicionar `spring-cloud-starter-config` e `spring.config.import` em todos. Configurações comuns vão para `application.properties` no repositório de config; as específicas, para `{nome-do-servico}.properties` |
| Sem `default-label` nem `clone-on-start` | O branch fica implícito, e um erro no clone só aparece na primeira requisição | `spring.cloud.config.server.git.default-label=main` e `clone-on-start=true` |
| Sem Actuator | O Kubernetes precisa usar `tcpSocket` como probe, que só confirma que a porta abriu, e não que a aplicação está saudável | Adicionar `spring-boot-starter-actuator` e usar `/actuator/health` nas probes |
| Senhas no repositório de config (se forem para lá) | Segredo em Git fica no histórico para sempre | Variáveis de ambiente, `{cipher}` do Config Server ou um cofre como o Vault |

---

## 8. service-registry

**O que está bom:** configuração mínima e correta de um Eureka standalone (`register-with-eureka=false`, `fetch-registry=false`), e o hostname já é sobrescrito no perfil `docker`. ✅

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| `spring-boot-starter-webmvc` declarado no pom | O starter do Eureka Server já traz o web | Pode remover (opcional) |
| Pastas `static` e `templates` vazias | Sobra do Initializr | Pode remover |

---

## 9. Docker e Kubernetes

Esta parte evoluiu bastante: todos os módulos têm Dockerfile, o compose sobe a aplicação inteira e já existem manifests de Kubernetes bem cuidados. Os pontos abaixo são refinamentos.

### 🟠 9.1 Senha do banco num ConfigMap

**O que acontece hoje:** `k8s/services/postgres/postgres-configmap.yml` guarda `POSTGRES_PASSWORD: admin`.

**Por que isso é um problema:** ConfigMap é para configuração comum. Para dados sensíveis, o Kubernetes tem o objeto **Secret**, que pode ser protegido com permissões (RBAC) e criptografia separadas.

**Como corrigir:**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secret
type: Opaque
stringData:
  POSTGRES_PASSWORD: admin   # em ambiente real, não versionar este arquivo
```

E nos serviços Spring, passar `SPRING_DATASOURCE_PASSWORD` a partir desse Secret (ver também 10.5).

### 🟡 9.2 Ajustes no Dockerfile

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| A aplicação roda como `root` dentro do container | Se alguém explorar uma falha na aplicação, ganha poderes de root no container | Criar um usuário e usar `USER app` antes do `ENTRYPOINT` |
| Os 6 Dockerfiles são idênticos, exceto o `EXPOSE` | Uma correção precisa ser repetida 6 vezes | Aceitável. Como alternativa, `./mvnw spring-boot:build-image` (Buildpacks) gera a imagem sem Dockerfile, já com usuário não-root |

### 🟡 9.3 Ajustes no docker-compose

| O que acontece hoje | Por que melhorar | Sugestão |
|---|---|---|
| Healthcheck dos serviços Spring via `/dev/tcp` (só confere se a porta abriu) | Porta aberta não quer dizer aplicação pronta | Usar `/actuator/health` (exige `curl` ou `wget` na imagem, ou adicionar o Actuator onde falta) |
| `company-service`, `job-service` e `review-service` não têm healthcheck | O gateway usa `depends_on` simples, sem `condition: service_healthy`, e pode subir antes deles | Adicionar healthcheck nos três e usar a condição no gateway |
| A rede se chama `postgres`, mas liga todos os containers | O nome confunde quem lê | Renomear para algo como `jobapp` |
| `openzipkin/zipkin` e `otel/opentelemetry-collector-contrib` sem versão | Sem tag = `latest`. Um dia a imagem muda e o ambiente quebra sem você ter mexido em nada | Fixar a versão, como já foi feito com `postgres:17` |
| RabbitMQ e Zipkin expostos em todas as interfaces | O Postgres está preso a `127.0.0.1`, mas esses não | `"127.0.0.1:5672:5672"` etc. |

### 🟡 9.4 Eureka dentro do Kubernetes

Isto é mais uma reflexão do que um problema. O Kubernetes já faz **service discovery** sozinho: um `Service` chamado `company-service` já resolve para os pods certos, com balanceamento. Rodar o Eureka dentro do cluster duplica essa função.

Para um projeto de estudo, manter o Eureka é ótimo: você aprende os dois mundos. Mas saiba que, em produção com Kubernetes, o mais comum é remover o Eureka e usar os Services do cluster (ou o **Spring Cloud Kubernetes**).

---

## 10. Pontos que valem para todos os serviços

### 🟠 10.1 Validação de entrada (Bean Validation)

**O que acontece hoje:** nenhum serviço valida os dados recebidos. (O company começou, mas falta o starter, ver 3.2.)

**Por que isso é um problema:** dado inválido chega ao banco e vira erro 500. O certo é responder **400 Bad Request** dizendo qual campo está errado.

**Como corrigir:** adicionar `spring-boot-starter-validation` em cada serviço, colocar as anotações nos DTOs e usar `@Valid @RequestBody` nos controllers (exemplos em 3.2, 4.5 e 5.6).

### 🟠 10.2 Tratamento de erros num lugar só, com `ProblemDetail`

**O que acontece hoje:** cada controller trata "não encontrado" com `if` e `boolean`, e qualquer outra exceção vira um 500 genérico. Cada erro tem um formato diferente.

**Por que isso é um problema:** o formato do erro faz parte do contrato da API. Quem consome precisa de um padrão único. Existe uma especificação para isso (RFC 9457, "Problem Details"), e o Spring já tem suporte pronto com a classe `ProblemDetail`.

**Como corrigir:** criar exceções de negócio e um `@RestControllerAdvice` em cada serviço. Ele "escuta" as exceções de todos os controllers e as converte na resposta HTTP certa:

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) {
        super("%s com id %s não encontrado(a)".formatted(resource, id));
    }
}
```

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CompanyAlreadyExistsException.class)
    ProblemDetail handleConflict(CompanyAlreadyExistsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
```

Ao estender `ResponseEntityExceptionHandler`, os erros de validação do `@Valid` já saem como `ProblemDetail` com status 400, sem código extra.

O ganho aparece nos controllers, que ficam bem mais simples:

```java
@GetMapping("/{companyId}")
public CompanyResponse findById(@PathVariable Long companyId) {
    return companyService.findById(companyId); // se não existir, o advice devolve 404
}
```

### 🟠 10.3 Testes

**O que acontece hoje:** cada serviço só tem o teste `contextLoads()` gerado pelo Spring Initializr.

**Por que isso é um problema:** vários bugs desta lista (4.1, 5.1, 3.1) seriam pegos por testes curtos. Testes também dão segurança para fazer as refatorações sugeridas aqui sem medo de quebrar algo.

**Como começar (do mais simples ao mais completo):**

1. **Testes unitários** dos services, com Mockito simulando o repository.
2. **`@WebMvcTest`** nos controllers: status HTTP, header `Location`, validação, formato do erro.
3. **`@DataJpaTest` com Testcontainers**, que sobe um PostgreSQL de verdade num container para testar os repositories.
4. **Testes de contrato** entre job e company (Spring Cloud Contract ou WireMock), para garantir que o `CompanyDto` do job continua batendo com a resposta do company.

### 🟠 10.4 O Hibernate altera o banco sozinho (`ddl-auto=update`)

**O que acontece hoje:** todos os serviços usam `spring.jpa.hibernate.ddl-auto=update`.

**Por que isso é um problema:** é prático no começo, mas o `update` nunca remove nem renomeia colunas, não deixa histórico das mudanças e pode fazer alterações perigosas em produção.

**Como corrigir:** usar **Flyway** (`spring-boot-starter-flyway`). Você escreve scripts SQL versionados (`V1__create_companies.sql`, `V2__add_unique_name.sql`...), e o Flyway aplica em ordem. Depois, troque para `ddl-auto=validate`: o Hibernate só confere se as entidades batem com as tabelas.

### 🟠 10.5 Senhas em texto puro

**O que acontece hoje:** `admin/admin` e `guest/guest` estão escritos no `application.properties` de todos os serviços.

**Como corrigir:** ler de variáveis de ambiente:

```properties
spring.datasource.password=${DB_PASSWORD}
```

O valor vem de um arquivo `.env` fora do Git (no compose) ou de um Secret (no Kubernetes, ver 9.1).

### 🟡 10.6 Prefixo `I` nas interfaces

**O que acontece hoje:** `ICompanyService`, `IJobService`, `IReviewService`.

**Por que mudar:** o prefixo `I` é uma convenção do C#/.NET. Em Java, o comum é a interface ter o nome do conceito (`CompanyService`) e a implementação levar o sufixo (`CompanyServiceImpl`). Outra opção bem aceita é **não ter interface** quando só existe uma implementação: o Spring e o Mockito funcionam perfeitamente com classes.

**O mais importante é ser consistente.** Hoje, em dois dos três serviços, alguém injeta a classe concreta mesmo com a interface existindo (3.5 e 5.7).

### 🟡 10.7 Documentação da API

**O que acontece hoje:** não há OpenAPI/Swagger.

**Como corrigir:** adicionar `springdoc-openapi-starter-webmvc-ui` em cada serviço (confira a versão compatível com o Boot 4). Você ganha uma página interativa em `/swagger-ui.html` para testar os endpoints, e as anotações de validação dos records aparecem automaticamente na documentação.

### 🟡 10.8 Muito getter e setter

**O que acontece hoje:** DTOs e mensagens com dezenas de linhas de getters e setters.

**Como corrigir:** usar `record` (Java 16+) em todos os DTOs e mensagens. Uma linha substitui a classe inteira. Entidades JPA não podem ser records; nelas, mantenha os getters e setters ou use Lombok (`@Getter @Setter`; evite `@Data` em entidades, porque ele gera `equals`/`hashCode` que causam problemas com o Hibernate).

### 🟡 10.9 Padronização

- **Versão na URL** (`/api/v1/companies`): não é obrigatório, mas é bom decidir antes de existirem clientes externos.
- **Nome dos módulos:** hoje há `snake_case` (`company_service`) misturado com `kebab-case` (`api-gateway`). O padrão no Maven e no Spring é kebab-case.
- **`ReviewMessage` duplicada** em review e company: em microsserviços isso é aceitável (uma biblioteca compartilhada acoplaria os deploys), mas o contrato precisa ser documentado e testado (10.3).
- **`sampling.probability=1.0`** (rastrear 100% das requisições) é ótimo para estudo. Em produção, algo como `0.1` já é suficiente.

---

## 11. Ordem sugerida

Não precisa fazer tudo de uma vez. Uma sequência que funciona bem:

1. **Bugs rápidos:** 3.1, 3.2, 5.1, 4.1/5.2, 4.2/5.3.
2. **A base:** validação (10.1) + `@RestControllerAdvice` com `ProblemDetail` (10.2). Isso simplifica todo o resto.
3. **DTOs:** ligar os do company (3.3), criar os do review (5.6), reorganizar os do job (4.5, 4.6) e padronizar as respostas de DELETE/PUT (4.9, 5.8).
4. **Comunicação entre serviços:** fallback (4.3), 404 do Feign (4.4), N+1 (4.7), média na mensagem + DLQ + eventos no update/delete (3.4, 3.6, 5.3, 5.4).
5. **Gateway:** `lb://` e Actuator fora do acesso público (6.1, 6.2).
6. **Testes** (10.3) cobrindo o que foi feito acima. Se puder, comece os testes junto com o passo 1: cada bug corrigido ganha um teste que prova a correção.
7. **Infra:** Flyway, segredos, Config Server em todos, ajustes de Docker e Kubernetes (10.4, 10.5, 7, 9).
8. **Acabamento:** os itens 🟡 que sobrarem.

---

## Glossário

| Termo | Explicação curta |
|---|---|
| **DTO** (Data Transfer Object) | Objeto que só carrega dados entre camadas ou sistemas. Define o formato do JSON da API, separado da tabela do banco |
| **Entidade** | Classe com `@Entity` que representa uma tabela do banco |
| **Record** | Tipo do Java para classes imutáveis que só guardam dados. Gera construtor, getters, `equals`, `hashCode` e `toString` automaticamente |
| **Service discovery** | Mecanismo para um serviço encontrar o outro pelo nome, sem saber IP e porta (aqui, o Eureka) |
| **Circuit breaker** | "Disjuntor": depois de várias falhas seguidas ao chamar um serviço, para de chamar por um tempo e usa um plano B (fallback) |
| **Fallback** | O plano B executado quando a chamada falha ou o circuit breaker está aberto |
| **DLQ** (Dead-Letter Queue) | Fila para onde vão as mensagens que falharam várias vezes, para análise posterior em vez de se perderem ou travarem a fila |
| **N+1** | Fazer uma consulta para buscar uma lista e depois mais uma para cada item. 1 + N chamadas em vez de 1 ou 2 |
| **Idempotente** | Operação que dá o mesmo resultado se repetida (PUT e DELETE são; POST não) |
| **ProblemDetail** | Formato padrão (RFC 9457) para respostas de erro em APIs HTTP, com suporte nativo no Spring |
| **`@Transactional`** | Faz um conjunto de operações no banco acontecer "tudo ou nada" |
| **Transactional Outbox** | Padrão que grava o evento numa tabela junto com o dado, para garantir que nenhuma mensagem se perca |
| **Probe** (Kubernetes) | Verificação periódica que o Kubernetes faz para saber se o container subiu (startup), está vivo (liveness) e pronto para receber tráfego (readiness) |
| **ConfigMap / Secret** | Objetos do Kubernetes para guardar configuração. Secret é para dados sensíveis, como senhas |
