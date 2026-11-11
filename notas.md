# Notas de estudo: APIs, Docker e Microsserviços com Spring

## Sumário

1. [APIs](#1-apis)
2. [HTTP: códigos de status e métodos](#2-http-códigos-de-status-e-métodos)
3. [Spring Boot Actuator](#3-spring-boot-actuator)
4. [Docker](#4-docker)
5. [Microsserviços](#5-microsserviços)
6. [Comunicação entre serviços](#6-comunicação-entre-serviços)
7. [Service Registry (Eureka)](#7-service-registry-eureka)
8. [OpenFeign](#8-openfeign)
9. [Tracing distribuído e telemetria](#9-tracing-distribuído-e-telemetria)
10. [Gerenciamento de configurações (Config Server)](#10-gerenciamento-de-configurações-config-server)
11. [API Gateway](#11-api-gateway)
12. [Tolerância a falhas e Circuit Breaker](#12-tolerância-a-falhas-e-circuit-breaker)

---

## 1. APIs

**API (Application Programming Interface)**: conjunto de regras ou protocolos que permite a interação entre sistemas diferentes por meio de um caminho de comunicação. Na web, isso se traduz em requisições HTTP.

> **Analogia do restaurante:** a API é o cardápio. Ele descreve cada prato, seus ingredientes e o preço. Você faz o pedido ao garçom (a chamada HTTP), que volta algum tempo depois com o prato (a resposta).

### Vantagens

1. Compartilhar dados
2. Acelerar o desenvolvimento de novos sistemas
3. Estender o alcance e a funcionalidade de um sistema

Exemplos: API do Twitter, API do Google Maps.

### Tipos

| Tipo | Descrição |
|---|---|
| **Interna** | Provê funcionalidade para produtos da própria organização (ex.: um serviço interno de notificação). Não deve ser exposta. |
| **Externa** | Funcionalidade exposta para qualquer usuário. |
| **De parceiros** | Semelhante à interna, mas liberada entre organizações parceiras. |

---

## 2. HTTP: códigos de status e métodos

### Códigos de status

Indicam o resultado de uma requisição:

```
Requisição → servidor → processamento → código de status retornado ao cliente
```

| Faixa | Significado |
|---|---|
| `1XX` | Informação |
| `2XX` | Sucesso |
| `3XX` | Redirecionamento |
| `4XX` | Erro do cliente |
| `5XX` | Erro do servidor |

| Código | Nome | Significado |
|---|---|---|
| `200` | OK | Requisição bem-sucedida |
| `201` | Created | Recurso criado |
| `204` | No Content | Nada a retornar |
| `301` | Moved Permanently | Recurso mudou de endereço |
| `400` | Bad Request | Erro de sintaxe na requisição |
| `401` | Unauthorized | Usuário não autenticado |
| `403` | Forbidden | Usuário autenticado, mas sem permissão |
| `404` | Not Found | Recurso não encontrado |
| `500` | Internal Server Error | Erro interno ao processar a requisição |

### Métodos (verbos) HTTP

| Método | Uso |
|---|---|
| `GET` | Recupera recursos (somente leitura; ex.: retorna 200 ou 404) |
| `POST` | Envia dados para criar um recurso (login, criação de registro etc.) |
| `PUT` | Atualiza um recurso existente |
| `DELETE` | Remove um recurso existente |

---

## 3. Spring Boot Actuator

Funcionalidades prontas de **monitoramento e gerenciamento** da aplicação: endpoints com informações da aplicação, métricas em tempo real etc. Podem ser personalizados.

### Dependência

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

O endpoint base é `/actuator`. Por segurança, a maioria dos endpoints vem desabilitada, porque eles expõem informações internas. Para liberar todos:

```properties
management.endpoints.web.exposure.include=*
```

### Principais endpoints

| Endpoint | Descrição |
|---|---|
| `/actuator/health` | Saúde da aplicação: conexão com o banco, espaço em disco, checagens personalizadas |
| `/actuator/health/{path}` | Saúde de um componente específico |
| `/actuator/info` | Informações da aplicação (versão, último commit etc.) |
| `/actuator/metrics` | Métricas de desempenho e comportamento |
| `/actuator/loggers` | Consulta e altera níveis de log |
| `/actuator/beans` | Lista os beans da aplicação |
| `/actuator/shutdown` | Desliga a aplicação (graceful shutdown) |

Referência: <https://docs.spring.io/spring-boot/reference/actuator/endpoints.html>

### `/health`

Para exibir os detalhes de cada componente (banco, disco etc.):

```properties
management.endpoint.health.show-details=always
```

Os detalhes podem ser ocultados ou sanitizados.

### `/info`

Exibe em JSON as propriedades com prefixo `info.`:

```properties
management.info.env.enabled=true
info.app.name=App
info.app.description=App description
info.app.version=1.0.0
```

### `/metrics`

Lista todas as métricas disponíveis. Cada uma é acessada em `/actuator/metrics/{nome}`. Exemplo: `disk.free` → `/actuator/metrics/disk.free`.

### `/loggers`

- `GET /actuator/loggers`: níveis de log de toda a aplicação.
- `GET /actuator/loggers/{nome}`: nível de um logger específico (retorna `configuredLevel` e `effectiveLevel`).
- `POST /actuator/loggers/{nome}`: altera o nível em tempo de execução.

```http
POST /actuator/loggers/com.jvam90.jobapp.JobAppApplication
Content-Type: application/json

{
  "configuredLevel": "DEBUG"
}
```

### `/beans`

Lista todos os beans, separados por contexto, com alias, escopo, dependências e tipo.

### `/shutdown`

Encerra a aplicação depois de processar as requisições pendentes (graceful shutdown). Precisa ser habilitado e só aceita `POST`:

```properties
management.endpoint.shutdown.enabled=true
```

Resposta:

```json
{
  "message": "Shutting down...bye."
}
```

---

## 4. Docker

### O problema

Em uma equipe, cada desenvolvedor tem sistema operacional, versões de bibliotecas e configurações diferentes. Daí vem o clássico "na minha máquina funciona": uma atualização que funciona para um dev quebra o ambiente de outro.

### A solução: containers

O Docker empacota **código, dependências e configurações de ambiente** em um ambiente isolado, o **container**. Basta ter um runtime capaz de executar containers, independentemente do sistema operacional. O mesmo container roda em Windows, Linux e macOS.

```
Container = código + runtime + dependências + ferramentas
```

O Docker também automatiza o **deploy, a escala e o gerenciamento** das aplicações.

### Containers vs. máquinas virtuais

```
VM:      Máquina → SO hospedeiro → Hypervisor → [VM: SO convidado + app] ...
Docker:  Máquina → SO hospedeiro → Docker Engine → [container: app] ...
```

| | Máquina virtual | Container |
|---|---|---|
| SO próprio | Sim (kernel próprio) | Não (compartilha o kernel do hospedeiro) |
| Peso / custo | Mais pesada | Mais leve |
| Isolamento | Maior | Menor: uma falha no kernel do hospedeiro pode comprometer os containers |
| Portabilidade | Depende de compatibilidade entre SOs | Independe do SO |
| Escala | Mais lenta | Mais rápida e fácil |

VMs são gerenciadas por softwares de virtualização (VMware etc.) por meio do **hypervisor**.

### Docker Engine

```
Docker Engine = Daemon + API + CLI
```

- **CLI**: linha de comando para interagir com o Docker.
- **API**: intermediária entre a CLI e o daemon. Recebe os comandos da CLI e os repassa ao daemon.
- **Daemon**: cria e gerencia containers e imagens. As imagens são obtidas de um **registry** (repositório, como o Docker Hub).

### Imagens e Dockerfile

Uma **imagem** é um molde com as instruções para criar o container que vai executá-la, junto com suas dependências. Ela é criada a partir de um **Dockerfile**:

```dockerfile
# Imagem base
FROM openjdk:11
# Volume que será criado
VOLUME /tmp
# Copia o jar da aplicação para a imagem com o nome myapp.jar
ADD target/myapp.jar myapp.jar
# Porta exposta para acessar a aplicação
EXPOSE 8080
# Comando executado quando o container inicia
ENTRYPOINT ["java", "-jar", "/myapp.jar"]
```

> A imagem `openjdk` foi descontinuada no Docker Hub. Hoje se usa, por exemplo, `eclipse-temurin:21-jre`.

### Buildpacks (Spring Boot)

O Spring Boot usa **Cloud Native Buildpacks** (implementação **Paketo**) para gerar imagens **sem Dockerfile**, através do `spring-boot-maven-plugin`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Vantagens:

- Dispensa o Dockerfile
- Ambientes consistentes
- Segurança
- **Layering e caching**: a imagem é montada em camadas, e só as camadas alteradas são reconstruídas

### Containerizando uma aplicação Spring

```bash
# Gera o .jar e cria a imagem com o nome informado
./mvnw spring-boot:build-image "-Dspring-boot.build-image.imageName=jvam90/jobappimage"

# Executa a imagem localmente
docker run -d -p 8080:8080 jvam90/jobappimage

# Publica no Docker Hub
docker login            # pede usuário e senha
docker push jvam90/jobappimage
```

### Comandos úteis

| Comando | Descrição |
|---|---|
| `docker images` | Lista as imagens locais |
| `docker pull <imagem>` | Baixa uma imagem do Docker Hub |
| `docker push <usuario/imagem>` | Envia uma imagem ao repositório |
| `docker build -t <imagem> .` | Constrói uma imagem a partir do Dockerfile |
| `docker run -it -d -p <host>:<container> --name <nome> <imagem>` | Cria e executa um container |
| `docker ps` | Lista os containers em execução |
| `docker ps -a` | Lista todos os containers, inclusive parados |
| `docker start <container>` | Inicia um container |
| `docker stop <container>` | Para um container |
| `docker rm <container>` | Remove um container parado |
| `docker exec -it <container> bash` | Abre um shell dentro do container |
| `docker logs <container>` | Exibe os logs (útil para ver erros) |
| `docker inspect <container>` | Detalhes do container |

`<container>` pode ser o nome ou o ID.

Flags do `docker run`:

- `-d` (detached): roda em segundo plano, sem prender o terminal.
- `-it`: modo interativo (ex.: abrir um bash no container).
- `-p <host-port>:<container-port>`: mapeia a porta do hospedeiro para a porta do container.
- `--name`: nome do container.

### Redes Docker

Uma rede permite que containers se comuniquem. **Na mesma rede, um container encontra o outro pelo nome ou ID.**

Sem rede em comum:

```bash
docker run -d --name db -e POSTGRES_PASSWORD=admin postgres:latest
docker run -d --name pgadmin -e PGADMIN_DEFAULT_EMAIL=admin@admin.com -e PGADMIN_DEFAULT_PASSWORD=admin dpage/pgadmin4

docker exec -it pgadmin ping db
# ping: bad address 'db'
```

Com uma rede compartilhada:

```bash
docker network create my-network

docker run -d --name db --network my-network -e POSTGRES_PASSWORD=admin postgres:latest
docker run -d --name pgadmin --network my-network -e PGADMIN_DEFAULT_EMAIL=admin@admin.com -e PGADMIN_DEFAULT_PASSWORD=admin dpage/pgadmin4

docker exec -it pgadmin ping db
# PING db (172.18.0.2): 56 data bytes
# 64 bytes from 172.18.0.2: seq=0 ttl=42 time=0.077 ms
```

Resumo:

1. Criar a rede
2. Baixar as imagens
3. Informar a rede (`--network`) ao executar os containers

Comandos completos com volumes, portas e política de reinício:

```bash
# PostgreSQL
docker run -d --name pgcontainer \
  -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin \
  -e PGDATA=/data/postgres -v postgres:/data/postgres \
  -p 5432:5432 --network network --restart unless-stopped \
  postgres:latest

# pgAdmin
docker run -d --name pgadmin \
  -e PGADMIN_DEFAULT_EMAIL=admin@admin.com -e PGADMIN_DEFAULT_PASSWORD=admin \
  -e PGADMIN_CONFIG_SERVER_MODE=False -v pgadmin:/var/lib/pgadmin \
  -p 5050:80 --network network --restart unless-stopped \
  dpage/pgadmin4
```

### Docker Compose

Um arquivo YAML que descreve **todos os containers** de que uma aplicação precisa (banco, a própria aplicação, pgAdmin etc.).

```yaml
services:
  postgres:
    image: postgres:17
    container_name: postgresdb
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-jobapp}
      POSTGRES_USER: ${POSTGRES_USER:-admin}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-admin}
    ports:
      - "127.0.0.1:5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - postgres
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-admin} -d ${POSTGRES_DB:-jobapp}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  pgadmin:
    image: dpage/pgadmin4:9
    container_name: pgadmin
    environment:
      PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL:-admin@admin.com}
      PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_DEFAULT_PASSWORD:-admin}
      PGADMIN_CONFIG_SERVER_MODE: 'False'
    volumes:
      - pgadmin_data:/var/lib/pgadmin
    ports:
      - "127.0.0.1:5050:80"
    networks:
      - postgres
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

networks:
  postgres:
    driver: bridge

volumes:
  postgres_data:
  pgadmin_data:
```

```bash
docker compose up -d            # sobe todos os serviços
docker compose up -d postgres   # sobe apenas o serviço "postgres"
```

---

## 5. Microsserviços

### Problemas do monolito

Em um monolito, todos os módulos ficam em uma única aplicação. Uma base de código única pode parecer mais fácil de gerenciar, mas traz problemas:

- **Dificuldade de mudar ou expandir**: o código fica cada vez mais acoplado, e mudanças afetam outras funcionalidades de forma inesperada.
- **Difícil de escalar**: a escala horizontal (mais servidores) é inviável. Sobra a vertical (mais hardware), que tem limite.
- **Preso a uma stack**: trocar de tecnologia no longo prazo fica quase impossível.
- **Complexidade crescente**: as interdependências atrapalham o desenvolvimento e o deploy.
- **Lentidão**: bases de código grandes demoram mais para inicializar.
- **Indisponibilidade total**: um deploy ou falha derruba a aplicação inteira.
- **Colaboração**: vários times mexendo na mesma base geram efeitos colaterais.

### A proposta

A aplicação vira uma **coleção de serviços autônomos** que se comunicam por algum protocolo, cada um geralmente com **seu próprio banco de dados**. É um estilo arquitetural.

```
serviço 1 → banco 1     (ex.: usuários)
serviço 2 → banco 2     (ex.: inventário)
serviço N → banco N     (ex.: pagamentos)
```

Desvantagens:

- Mais complexidade
- A adequação depende de cada caso
- A comunicação entre serviços varia muito: eventos, filas, assincronismo, gRPC etc.

### Princípios

- **Responsabilidade única**: cada serviço faz uma coisa só.
- **Independência**: mudanças em um serviço não devem impactar os outros.
- **Descentralização**: cada serviço tem seu próprio banco de dados.
- **Isolamento de falhas**: se um serviço cai, a aplicação não cai inteira.
- **CI/CD**: entrega e deploy contínuos.

### Como resolvem os problemas do monolito

1. **Escalabilidade**: cada serviço escala de forma independente (vertical ou horizontal, para cima ou para baixo), o que reduz custos.
2. **Flexibilidade**: cada serviço pode usar uma stack diferente, desde que consigam se comunicar.
3. **Simplicidade**: cada serviço tem escopo reduzido.

### Migrando do monolito (projeto jobapp)

Cada recurso vira um projeto Spring Boot separado, com banco próprio e porta própria:

| Serviço | Porta |
|---|---|
| company-service | 8081 |
| job-service | 8082 |
| review-service | 8083 |
| service-registry (Eureka) | 8084 |
| config-server | 8888 |

> No IntelliJ, crie um projeto vazio e adicione cada serviço como um módulo Spring Boot.

**Relacionamentos entre entidades:** no monolito, `Company` tinha uma `List<Job>`. Em microsserviços isso não é mais necessário nem recomendado, porque os serviços são independentes. A entidade guarda apenas a **chave estrangeira** (ex.: `Long companyId`). Listas agregadas podem existir nos **DTOs**, já que servem só para consulta.

---

## 6. Comunicação entre serviços

Cada microsserviço roda em seu próprio processo e tem seu próprio banco, mas todos formam uma única aplicação, então precisam se comunicar:

- **Síncrona**: HTTP no modelo requisição-resposta (RestTemplate, OpenFeign), ou gRPC.
- **Assíncrona**: message brokers, eventos, filas (ex.: SQS). Um serviço publica uma mensagem e o outro a consome quando estiver pronto.

### RestTemplate

Classe do Spring para fazer chamadas HTTP pelo código, abstraindo os detalhes de baixo nível.

- Simplifica o processo por meio de abstrações
- Converte respostas automaticamente em classes Java
- Gerencia erros de forma efetiva
- É **síncrono**: a thread espera a resposta

```java
RestTemplate restTemplate = new RestTemplate();
CompanyDto company = restTemplate.getForObject("http://localhost:8081/companies/1", CompanyDto.class);
```

Recebe a URL e a classe para a qual a resposta será convertida.

> **Atenção:** um serviço não conhece as classes de outro. O job-service não deve usar a entidade `Company` do company-service. Em vez disso, cria seu próprio **DTO**.

Exemplo: buscar uma vaga junto com os dados da empresa:

1. Busca a vaga no job-service
2. Chama o company-service
3. Monta um DTO com as informações desejadas

### DTO (Data Transfer Object)

Padrão para **transferir dados** entre subsistemas, dentro de uma aplicação ou entre aplicações. Pode conter todos os dados ou apenas parte deles. Não faz parte do domínio: não tem regras de negócio nem comportamento, apenas dados. Também evita expor diretamente as entidades do domínio.

---

## 7. Service Registry (Eureka)

Permite a **descoberta dinâmica de serviços**. Em vez de endereços fixos no código (hardcoded), os serviços se registram em um servidor central que conhece todo o ecossistema.

1. Cada serviço se registra no service registry.
2. Para chamar outro serviço, ele consulta o registry e, com o endereço obtido, faz a requisição.

Benefícios:

- Descoberta dinâmica de serviços
- Balanceamento de carga
- Tolerância a falhas e resiliência
- Elasticidade e escalabilidade
- Monitoramento da saúde dos serviços

No Spring, o servidor padrão é o **Netflix Eureka**.

### Servidor (service-registry)

Dependências: `spring-boot-starter-web` e `spring-cloud-starter-netflix-eureka-server`.

```java
@SpringBootApplication
@EnableEurekaServer // transforma a aplicação em um servidor Eureka
public class ServiceRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
```

```properties
# O próprio registry não se registra nem busca o registro
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

### Clientes (cada microsserviço)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>

<!-- em <dependencyManagement> -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>${spring-cloud.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

```properties
eureka.client.serviceUrl.defaultZone=http://localhost:8084/eureka/
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
```

### Chamando pelo nome do serviço: `@LoadBalanced`

O Eureka é um registro, não um roteador. Um `RestTemplate` comum **não resolve nomes de serviço**:

```java
// Não funciona com um RestTemplate comum
restTemplate.getForObject("http://COMPANY-SERVICE:8081/companies/" + id, CompanyDto.class);
```

A solução é declarar o `RestTemplate` como bean com `@LoadBalanced`. O Spring Cloud cria um proxy que resolve o nome do serviço via registry:

```java
@Configuration
public class AppConfig {

    @LoadBalanced
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

```java
@Autowired
RestTemplate restTemplate;

// Sem porta: o load balancer resolve
CompanyDto companyDto = restTemplate.getForObject(
        "http://company-service/companies/" + job.getCompanyId(), CompanyDto.class);
```

- `@Bean`: o Spring gerencia a instância.
- `@Autowired`: o Spring injeta essa instância em tempo de execução.
- **O nome do serviço não pode ter underscore (`_`)**, porque é um hostname inválido. Use hífen (`-`).

### Cache do Eureka

O Eureka pode manter informações antigas em cache depois de reiniciar os serviços. Se acontecer:

1. Limpe o `target` dos projetos
2. Recompile
3. Suba novamente

A atualização leva em média **30 a 60 segundos**. Em desenvolvimento (não em produção), dá para reduzir esse tempo nos clientes:

```properties
eureka.client.registry-fetch-interval-seconds=5
eureka.instance.lease-renewal-interval-in-seconds=5
spring.cloud.loadbalancer.cache.ttl=5s
```

### Como o Eureka funciona: heartbeat

1. O serviço faz o registro inicial no servidor.
2. A partir daí, envia periodicamente um sinal (**heartbeat**) indicando que está operacional.
3. O servidor monitora esses sinais.
4. Se os sinais param por um período, o serviço é marcado como indisponível.
5. Assim o servidor sempre tem as informações mais recentes de cada serviço.
6. Se o servidor Eureka estiver fora do ar, os serviços registram erros ao tentar enviar o heartbeat.

---

## 8. OpenFeign

Cliente HTTP **declarativo** que elimina o boilerplate do RestTemplate.

```java
// RestTemplate
CompanyDto dto = restTemplate.getForObject("http://company-service/companies/" + id, CompanyDto.class);

// OpenFeign
CompanyDto dto = companyClient.getCompany(id);
```

Vantagens: integração com o Eureka, load balancing embutido, suporte a circuit breakers e fallbacks, e conversão automática de JSON. OpenFeign e RestTemplate têm o mesmo propósito: fazer chamadas HTTP.

### Configuração

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

```java
@EnableFeignClients // habilita o uso de Feign no serviço
@SpringBootApplication
public class JobServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
```

### Cliente

Assim como um repositório JPA, o cliente é uma interface:

```java
@FeignClient(name = "company-service") // nome do serviço no Eureka
public interface CompanyClient {

    @GetMapping("/companies/{companyId}")
    CompanyDto getCompany(@PathVariable Long companyId);
}
```

Uso:

```java
CompanyDto companyDto = companyClient.getCompany(job.getCompanyId());
```

O Feign resolve os serviços pelo nome sem precisar de `@LoadBalanced`, então a classe `AppConfig` com o `RestTemplate` deixa de ser necessária.

> Antigamente o load balancing era feito pelo Netflix **Ribbon**. Nas versões atuais do Spring Cloud ele foi substituído pelo **Spring Cloud LoadBalancer**.

---

## 9. Tracing distribuído e telemetria

### Conceito

**Tracing** é rastrear o que acontece durante uma requisição. Em um monolito é simples. Em microsserviços a requisição passa por vários serviços e tecnologias, então é preciso um rastreamento **distribuído** que trate a requisição inteira como uma unidade.

Problemas que ele resolve:

- **Visualização** do caminho completo da requisição
- **Identificação de gargalos** de desempenho
- **Análise de erros e debug**
- **Rastreamento de dependências** e erros em cascata
- **Otimização de desempenho**

### Trace e Span

- **Span**: uma operação dentro de um único serviço. Tem seu próprio `SpanID`.
- **Trace**: a transação completa, composta por um ou mais spans. Tem um `TraceID` compartilhado por todos os seus spans.

```
job-service (Span 1) → company-service (Span 2) → review-service (Span 3)
└──────────────────────── TraceID 1 ────────────────────────┘
```

### Zipkin

Ferramenta open source de tracing distribuído, composta por quatro partes:

| Componente | Função |
|---|---|
| Coletor | Recebe dados (HTTP, Kafka etc.) |
| Armazenamento | Persiste os dados (memória, Elasticsearch, Cassandra etc.) |
| Busca | Procura traces |
| Visualização | UI para analisar os traces |

Pode rodar como jar, a partir do código-fonte ou via Docker:

```yaml
zipkin:
  image: openzipkin/zipkin
  container_name: zipkin
  ports:
    - 9411:9411
  networks:
    - postgres
```

### Micrometer

Fachada de métricas, neutra quanto a fornecedor (vendor-agnostic), que faz a ponte entre a aplicação e o sistema de coleta. O **Micrometer atua no nível da aplicação** (métricas do serviço). O **Zipkin atua no nível da requisição** (traces e spans).

### OpenTelemetry no Spring Boot 4

No Spring Boot 4, o padrão é o **OpenTelemetry (OTel)**, padrão de mercado para coleta de traces, métricas e logs. O suporte direto ao Zipkin foi deprecado. As alternativas são usar o Brave ou enviar para um collector OTel.

**Arquitetura recomendada:** `aplicação --OTLP--> OTel Collector --> Zipkin`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

O starter `spring-boot-starter-opentelemetry` substitui as dependências separadas `micrometer-tracing-bridge-otel` e `opentelemetry-exporter-otlp`.

#### Visão geral: três pontos de configuração

```
microsserviço ──OTLP/HTTP──▶ otel-collector:4318
                                ├─ /v1/traces  → batch → zipkin:9411  (UI em localhost:9411)
                                └─ /v1/metrics → batch → debug (log do collector)
```

#### 1. `application.properties` (lado da aplicação)

```properties
spring.application.name=job-service

# Fração das requisições que viram trace. O padrão do Spring é 0.1 (10%).
# 1.0 = 100%, bom em dev; em produção use algo como 0.1
management.tracing.sampling.probability=1.0

# Para onde os traces são enviados (o collector)
management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces

# Para onde o Micrometer envia as métricas
management.otlp.metrics.export.url=http://localhost:4318/v1/metrics
# Intervalo de envio (padrão: 1 minuto)
management.otlp.metrics.export.step=10s

# Adiciona [serviço,traceId,spanId] em cada linha de log
logging.pattern.correlation=[${spring.application.name:},%X{traceId:-},%X{spanId:-}]
logging.include-application-name=false
```

Com o `traceId` de uma linha de log, dá para procurar o trace correspondente no Zipkin. O `:-` define valor vazio quando não há trace ativo.

#### 2. `otel-config.yaml` (Collector)

O Collector é um pipeline: **receivers** recebem os dados, **processors** os transformam e **exporters** os enviam a um destino. A seção `service` liga as peças.

```yaml
receivers:
  otlp:                         # recebe no protocolo OTLP (o que o Spring envia)
    protocols:
      http:
        endpoint: 0.0.0.0:4318  # rotas /v1/traces, /v1/metrics, /v1/logs
      grpc:
        endpoint: 0.0.0.0:4317  # opcional: a aplicação usa HTTP

processors:
  batch:                        # agrupa em lotes (padrão: 200ms ou 8192 itens)

exporters:
  zipkin:
    endpoint: http://zipkin:9411/api/v2/spans  # "zipkin" é resolvido pelo DNS da rede Docker
  debug:
    verbosity: basic            # só escreve um resumo no log (normal/detailed mostram mais)

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [zipkin]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [debug]        # o Zipkin não aceita métricas
```

Pontos importantes:

- Use `0.0.0.0`, não `localhost`. Com `localhost`, o collector fica inacessível de fora do container.
- **Um componente só fica ativo se aparecer em algum pipeline.** Sem o pipeline `metrics`, a rota `/v1/metrics` retorna **404**.

#### 3. `docker-compose.yaml`

```yaml
zipkin:
  image: openzipkin/zipkin       # guarda traces em memória: somem ao reiniciar
  container_name: zipkin
  ports:
    - 9411:9411                  # UI e API em http://localhost:9411
  networks:
    - postgres                   # rede compartilhada entre os serviços

otel-collector:
  image: otel/opentelemetry-collector-contrib   # a distribuição "contrib" inclui o exporter zipkin
  command: [ "--config=/etc/otel/config.yaml" ]
  volumes: [ "./otel-config.yaml:/etc/otel/config.yaml" ]
  ports: [ "4318:4318", "4317:4317" ]  # os serviços rodam fora do Docker e enviam para localhost:4318
  depends_on: [ zipkin ]               # garante só a ordem de start, não que o Zipkin esteja pronto
  networks:
    - postgres                         # mesma rede do Zipkin, para resolver http://zipkin:9411
```

---

## 10. Gerenciamento de configurações (Config Server)

Com muitos serviços, gerenciar URLs externas, configurações de cache, bancos etc. fica complexo. A solução é um **servidor central de configuração**.

Benefícios:

- Configurações centralizadas e versionadas
- Atualizações dinâmicas
- Segurança
- Perfis por ambiente (dev, prod etc.)
- Fonte única de verdade
- Gerenciamento mais fácil

### Spring Cloud Config Server

```
microsserviços → config-server → repositório Git
```

Quando as configurações mudam, o servidor pode notificar os serviços para que busquem os valores atualizados.

#### Servidor

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-config-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

O Eureka client serve para registrar o config-server no service registry.

```java
@EnableConfigServer // trata a aplicação como servidor de configuração
@SpringBootApplication
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

Aponte para um repositório Git que contenha os arquivos `application.properties`:

```properties
spring.cloud.config.server.git.url=https://github.com/joaovictoralvesmartins90/jobapp_ms_config/
```

Consultando as propriedades:

| URL | Arquivo |
|---|---|
| `http://localhost:8888/application/default` | `application.properties` |
| `http://localhost:8888/application/dev` | `application-dev.properties` |

#### Cliente (cada microsserviço)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

```properties
spring.config.import=configserver:http://localhost:8888

# Perfil ativo: usa application-dev.properties
spring.profiles.active=dev
```

Para que o serviço suba mesmo com o config-server fora do ar, marque o import como opcional:

```properties
spring.config.import=optional:configserver:http://localhost:8888
```

---

## 11. API Gateway

Com o monolito quebrado em serviços, surge a pergunta: **qual é o ponto de entrada da aplicação?** Para o usuário, a aplicação deve parecer uma coisa só. O **API Gateway** é o ponto de entrada único que abstrai a complexidade do sistema.

Responsabilidades:

- Encapsular a arquitetura interna
- Tratar preocupações transversais (cross-cutting): segurança, load balancing, rate limiting, analytics
- Autenticar requisições e repassar apenas as válidas
- Rotear requisições de acordo com a URL
- Transformar requisições e respostas
- Agregar respostas de vários serviços em uma só

No Spring, o gateway padrão é o **Spring Cloud Gateway**.

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
</dependency>
```

### Configurando rotas

> **Prefixo das propriedades:** com `spring-cloud-starter-gateway-server-webmvc` no Spring Cloud 2025.1, as rotas ficam em `spring.cloud.gateway.server.webmvc.routes`. O prefixo antigo `spring.cloud.gateway.routes` é da versão WebFlux/legada e **é ignorado**, então nenhuma rota é registrada.

```properties
# id: identificador único da rota
spring.cloud.gateway.server.webmvc.routes[0].id=company-service
# uri: endereço do serviço
spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:8081
# predicates: se a requisição casar, é encaminhada para a uri
spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/companies,/companies/*
```

### Regras de path

- `**` é um curinga para qualquer quantidade de segmentos, mas **só é aceito no final do caminho**. `/companies/**/jobs/**` é inválido: o gateway falha ao subir ou nunca casa a rota.
- `*` casa **um único segmento** (ex.: o `companyId`).
- **Cuidado com rotas que se sobrepõem.** `/companies/**` também casa `/companies/1/jobs/...`. Se essa rota vier primeiro, tudo vai para o company-service. Restrinja os predicados para que as rotas não se sobreponham e a ordem não importe.
- **Confira os índices.** `id`, `uri` e `predicates` de uma rota precisam usar o mesmo índice `routes[N]`. Com índices trocados, uma rota fica sem predicado e outra sem uri.

### Rotas do projeto

| Rota | Predicado | Endpoints do controlador |
|---|---|---|
| company-service (8081) | `/companies,/companies/*` | `/companies`, `/companies/{companyId}` |
| job-service (8082) | `/companies/*/jobs/**` | `/companies/{companyId}/jobs[/{jobId}]` |
| review-service (8083) | `/companies/*/reviews/**` | `/companies/{companyId}/reviews[/{reviewId}]` |

### Usando o load balancer (`lb://`)

Em vez de host e porta, use o nome do serviço registrado no Eureka:

```properties
spring.cloud.gateway.server.webmvc.routes[1].uri=lb://JOB-SERVICE
```

---

## 12. Tolerância a falhas e Circuit Breaker

**Tolerância a falhas** é a capacidade de continuar funcionando mesmo quando ocorrem falhas. Em microsserviços, um serviço que cai (por erro na aplicação, queda de rede etc.) pode afetar todo o sistema. Por isso é preciso lidar com:

- Isolamento de erros
- Latência de rede
- Problemas de deploy
- Falhas externas ao sistema

**Resiliência** é a capacidade de se recuperar de falhas. Em Java, a biblioteca padrão é o **Resilience4j**.

### Técnicas de resiliência

| Técnica | Descrição |
|---|---|
| **Retry** | Tenta novamente a operação que falhou |
| **Rate Limiting** | Limita a quantidade de requisições a um serviço para não exceder sua capacidade |
| **Bulkhead** | Isola partes da aplicação em compartimentos para evitar falhas em cascata |
| **Circuit Breaker** | Interrompe as chamadas a um serviço com falha, como um disjuntor que desarma na sobrecarga |
| **Fallback** | Resposta padrão quando há erro (ex.: serviço indisponível) |
| **Timeout** | Desiste da chamada se não houver resposta em determinado tempo |
| **Graceful Degradation** | Continua funcionando com capacidade reduzida sob alta carga ou erro |

O Resilience4j implementa essas técnicas em módulos (Retry, RateLimiter, Bulkhead, CircuitBreaker etc.).

### Estados do Circuit Breaker

```
             taxa de falhas ≥ limite
  ┌────────┐ ──────────────────────▶ ┌────────┐
  │ CLOSED │                         │  OPEN  │
  └────────┘ ◀──┐                    └────────┘
                │                       │  após waitDurationInOpenState
   chamadas de  │                       ▼
   teste OK     │                 ┌───────────┐
                └──────────────── │ HALF_OPEN │
                                  └───────────┘
                     chamadas de teste falham → volta para OPEN
```

1. **Closed (fechado)**: funcionamento normal. As requisições passam e as falhas são monitoradas.
2. **Open (aberto)**: a taxa de falhas ultrapassou o limite. Novas requisições são bloqueadas imediatamente (erro ou fallback), para não sobrecarregar o serviço com problema.
3. **Half-Open (meio-aberto)**: depois do tempo de espera, passa um número limitado de **requisições de teste**:
   - se forem bem-sucedidas, o serviço se recuperou e o circuito volta para **Closed**;
   - se continuarem falhando, volta para **Open** e o tempo de espera recomeça.

Existem ainda dois estados especiais: **Disabled** (desabilitado) e **Forced Open** (forçado aberto).

### Dependência

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

### Propriedades

`instances.companyBreaker` é o nome da instância do circuit breaker.

```properties
# Registra o health indicator no Actuator
resilience4j.circuitbreaker.instances.companyBreaker.registerHealthIndicator=true
# Tipo de janela: COUNT_BASED considera as últimas N chamadas
resilience4j.circuitbreaker.instances.companyBreaker.sliding-window-type=count_based
# Tamanho da janela usada para calcular a taxa de falhas
resilience4j.circuitbreaker.instances.companyBreaker.slidingWindowSize=10
# Mínimo de chamadas antes de calcular a taxa de falhas
resilience4j.circuitbreaker.instances.companyBreaker.minimumNumberOfCalls=5
# Taxa de falhas (%) que abre o circuito
resilience4j.circuitbreaker.instances.companyBreaker.failureRateThreshold=50
# Tempo em OPEN antes de ir para HALF_OPEN
resilience4j.circuitbreaker.instances.companyBreaker.waitDurationInOpenState=10s
# Chamadas de teste permitidas em HALF_OPEN
resilience4j.circuitbreaker.instances.companyBreaker.permittedNumberOfCallsInHalfOpenState=3
# Passa de OPEN para HALF_OPEN automaticamente, sem esperar uma nova chamada
resilience4j.circuitbreaker.instances.companyBreaker.automatic-transition-from-open-to-half-open-enabled=true
# Quantidade de eventos guardados para /actuator/circuitbreakerevents
resilience4j.circuitbreaker.instances.companyBreaker.eventConsumerBufferSize=10

# Exibe a saúde dos circuit breakers no Actuator
management.health.circuitbreakers.enabled=true
management.endpoints.web.exposure.include=health,circuitbreakers,circuitbreakerevents
management.endpoint.health.show-details=always
```

### Uso

```java
@Override
@CircuitBreaker(name = "companyBreaker") // nome definido no application.properties
public List<JobWithCompanyDto> findAll(Long companyId) {
    List<Job> jobs = jobRepository.findByCompanyId(companyId);
    List<JobWithCompanyDto> jobWithCompanyDtos = new ArrayList<>();

    for (Job job : jobs) {
        CompanyDto companyDto = companyClient.getCompany(job.getCompanyId());
        jobWithCompanyDtos.add(getJobWithCompanyDto(job, companyDto));
    }
    return jobWithCompanyDtos;
}
```

> Sem `fallbackMethod`, quando o circuito está aberto a chamada lança `CallNotPermittedException` e o cliente recebe erro. Para uma resposta degradada, use um fallback (abaixo).

### Fallback

O `fallbackMethod` indica o método chamado no lugar do original quando a chamada falha ou o circuito está aberto:

```java
@Override
@CircuitBreaker(name = "companyBreaker", fallbackMethod = "companyBreakerFallback")
public List<JobWithCompanyDto> findAll(Long companyId) {
    List<Job> jobs = jobRepository.findByCompanyId(companyId);
    List<JobWithCompanyDto> jobWithCompanyDtos = new ArrayList<>();

    for (Job job : jobs) {
        CompanyDto companyDto = companyClient.getCompany(job.getCompanyId());
        jobWithCompanyDtos.add(getJobWithCompanyDto(job, companyDto));
    }
    return jobWithCompanyDtos;
}

// Mesmos parâmetros do método original + a exceção como último parâmetro
public List<JobWithCompanyDto> companyBreakerFallback(Long companyId, Throwable ex) {
    // Resposta degradada: aqui, uma lista vazia
    return new ArrayList<>();
}
```

Regras da assinatura do fallback:

- **Mesmo tipo de retorno** do método original.
- **Mesmos parâmetros** do método original, na mesma ordem, **mais um `Throwable`** (ou uma exceção mais específica) no final.
- Precisa estar na **mesma classe** do método anotado.

> Um fallback sem parâmetros, como `public List<String> companyBreakerFallback()`, **não funciona**. O Resilience4j não encontra um método compatível e a chamada falha com `NoSuchMethodException` ("No fallback method match found").

Também dá para criar um fallback para cada tipo de exceção. O Resilience4j escolhe o mais específico:

```java
// Circuito aberto: a chamada nem chegou ao company-service
public List<JobWithCompanyDto> companyBreakerFallback(Long companyId, CallNotPermittedException ex) { ... }

// Qualquer outra falha
public List<JobWithCompanyDto> companyBreakerFallback(Long companyId, Throwable ex) { ... }
```

### Retry

Em vez de desistir na primeira falha, o `@Retry` **repete a chamada** um número configurado de vezes, com uma espera entre as tentativas. **Se todas as tentativas falharem, o fallback é chamado.** É útil para falhas passageiras, como uma instabilidade momentânea de rede.

```java
import io.github.resilience4j.retry.annotation.Retry;

@Override
@Retry(name = "companyBreaker", fallbackMethod = "companyBreakerFallback")
public List<JobWithCompanyDto> findAll(Long companyId) {
    List<Job> jobs = jobRepository.findByCompanyId(companyId);
    List<JobWithCompanyDto> jobWithCompanyDtos = new ArrayList<>();

    for (Job job : jobs) {
        CompanyDto companyDto = companyClient.getCompany(job.getCompanyId());
        jobWithCompanyDtos.add(getJobWithCompanyDto(job, companyDto));
    }
    return jobWithCompanyDtos;
}
```

O fallback segue as mesmas regras de assinatura do circuit breaker (mesmo retorno, mesmos parâmetros + `Throwable`).

Configuração no `application.properties`:

```properties
# Número máximo de tentativas (inclui a primeira chamada). Padrão: 3
resilience4j.retry.instances.companyBreaker.max-attempts=5
# Espera entre uma tentativa e outra. Padrão: 500ms
resilience4j.retry.instances.companyBreaker.wait-duration=2s
```

> **Atenção ao prefixo:** as propriedades de retry ficam em `resilience4j.retry.instances.<nome>`, não em `resilience4j.circuitbreaker.instances.<nome>`. Com o prefixo do circuit breaker elas são ignoradas sem nenhum erro, e o retry usa os valores padrão.

O `name` da anotação precisa ser igual ao nome da instância nas propriedades. Retry e circuit breaker têm configurações separadas, então o mesmo nome (`companyBreaker`) pode ser usado nos dois. Um nome próprio, como `companyRetry`, deixa mais claro de qual instância se trata.

#### Combinando Retry e Circuit Breaker

As duas anotações podem ser usadas juntas no mesmo método:

```java
@Retry(name = "companyBreaker", fallbackMethod = "companyBreakerFallback")
@CircuitBreaker(name = "companyBreaker", fallbackMethod = "companyBreakerFallback")
public List<JobWithCompanyDto> findAll(Long companyId) { ... }
```

Por padrão, o Resilience4j aplica o **Retry por fora** do Circuit Breaker. Cada tentativa passa pelo circuit breaker e conta na taxa de falhas. Quando o circuito abre, as tentativas seguintes falham na hora com `CallNotPermittedException`.

### Rate Limiting

Limita o tráfego que chega a um recurso. Sem limite, um usuário malicioso pode enviar requisições sem parar. O servidor tenta atender todas, aloca recursos até esgotá-los e acaba ficando indisponível.

Também serve para:

- Dificultar ataques de força bruta
- Dificultar web scraping
- Limitar o acesso a APIs (ex.: cotas por cliente)

Configuração no `application.properties`:

```properties
# Quantas chamadas são permitidas por período
resilience4j.ratelimiter.instances.companyBreaker.limit-for-period=2
# Duração do período; ao fim dele o limite é renovado
resilience4j.ratelimiter.instances.companyBreaker.limit-refresh-period=4s
# Quanto tempo uma chamada espera por uma vaga; 0 = rejeita na hora
resilience4j.ratelimiter.instances.companyBreaker.timeout-duration=0
```

Com essa configuração, são permitidas **duas chamadas a cada 4 segundos**.

> **Atenção:**
> - Todas as propriedades usam o prefixo `resilience4j.ratelimiter.instances.<nome>`. Com outro prefixo (ex.: `resilience4j.retry...`) a propriedade é ignorada sem nenhum erro.
> - Informe a unidade nas durações (`4s`). Um número sem unidade é interpretado como **milissegundos**: `limit-refresh-period=4` seria 4ms.

No código:

```java
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@Override
@RateLimiter(name = "companyBreaker", fallbackMethod = "companyBreakerFallback")
public List<JobWithCompanyDto> findAll(Long companyId) { ... }
```

Quando o limite é excedido, a chamada lança `RequestNotPermitted`. Com `fallbackMethod`, o fallback é chamado no lugar, com as mesmas regras de assinatura do circuit breaker.

### Monitorando

| Endpoint | O que mostra |
|---|---|
| `/actuator/circuitbreakers` | Estado, `failureRate`, chamadas na janela, chamadas bloqueadas |
| `/actuator/circuitbreakerevents/companyBreaker` | Últimos eventos (`SUCCESS`, `ERROR`, `STATE_TRANSITION`, `NOT_PERMITTED`) |
| `/actuator/health` | Os mesmos dados em `components.circuitBreakers` |

A `failureRate` aparece como `-1.0%` até atingir `minimumNumberOfCalls`.
