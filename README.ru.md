RU | [EN](README.md)

# Wiretap

> Структурированное JSON-логирование для Spring Boot приложений с перехватом HTTP-запросов и ответов
> через servlet, RestTemplate, RestClient, FeignClient, WebClient и WebServiceTemplate.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Статус:** Pre-release — сейчас опубликован только `1.0.0-SNAPSHOT` (публичный Central Portal snapshots), он проходит обкатку на реальных проектах перед стабильным релизом `1.0.0`. Публичный API (конфигурационные свойства, SPI-интерфейсы, координаты артефактов) практически финальный и следует [семантическому версионированию](https://semver.org/lang/ru/) начиная с 1.0.0.

> **Попробуйте вживую.** [`logger-demo`](https://github.com/alexander-kuznetsov/logger-demo) —
> готовое к запуску Spring Boot приложение, которое подключает Wiretap в реальный сервис:
> входящий/исходящий HTTP, Kafka, маскирование, метрики, pretty-print — чтобы увидеть
> JSON-вывод до интеграции в свой проект.

## Что вы получаете

Добавьте Wiretap в Spring Boot приложение — и каждый входящий и исходящий HTTP-вызов будет зафиксирован
как структурированная JSON-строка лога, с единообразными полями, автоматическим распространением
correlation ID и встроенным маскированием чувствительных данных.

**Лог приложения** (из `log.info(...)` или любого SLF4J-вызова):

```json
{
  "@timestamp": "2026-05-07T10:14:32.918+00:00",
  "env": "prod",
  "system": "checkout-api",
  "inst": "checkout-api-7d8c9f-xk2",
  "trace_id": "0123456789abcdef",
  "span_id": "fedcba9876543210",
  "level": "INFO",
  "thread_name": "http-nio-8080-exec-1",
  "logger": "com.example.OrderService",
  "message": "Order created"
}
```

**HTTP access-лог** (каждый входящий и исходящий HTTP-вызов):

```json
{
  "@timestamp": "2026-05-07T10:14:32.918+00:00",
  "env": "prod",
  "system": "checkout-api",
  "inst": "checkout-api-7d8c9f-xk2",
  "trace_id": "0123456789abcdef",
  "span_id": "fedcba9876543210",
  "level": "INFO",
  "message": "Captured incoming http request /api/v1/orders",
  "http_info": {
    "direction": "INCOMING",
    "http_method": "POST",
    "request_url": "/api/v1/orders",
    "return_code": 201,
    "duration": 47,
    "request_body": "{\"items\":[...]}",
    "request_body_length": 320,
    "response_body": "{\"id\":\"order_42\"}",
    "response_body_length": 19
  }
}
```

## Быстрый старт

> **Pre-release.** Сейчас опубликован только `1.0.0-SNAPSHOT` — в публичном
> Central Portal snapshots — и он проходит обкатку на реальных проектах.
> Стабильный релиз `1.0.0` будет позже.

```gradle
repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
    // Подбираем координату под версию вашего Spring Boot
    // (см. раздел «Версионирование и совместимость» ниже).
    implementation 'io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter:1.0.0-SNAPSHOT'
}
```

Когда выйдет `1.0.0` — убираете snapshots-репозиторий и берёте `:1.0.0` из
`mavenCentral()`, это единственное изменение.

Никакой дополнительной конфигурации не требуется. Wiretap подключается автоматически через механизм
Spring Boot auto-configuration. Логи пишутся в stdout в формате JSON. Входящий HTTP-трафик
перехватывается автоматически; исходящий — для любого `RestTemplate` / `RestClient` /
`FeignClient` / `WebClient` / `WebServiceTemplate`, созданного через стандартные Spring-билдеры.
Клиенты на базе `WebClient`, в том числе `graphql.kickstart.spring.webclient.boot.GraphQLWebClient`,
покрываются автоматически через тот же `WebClient.Builder`-кастомайзер.

Чтобы также писать логи в ротируемый файл:

```yaml
wiretap:
  file-logging:
    enabled: true
    path: /var/log/myapp     # по умолчанию: /var/log/wiretap
```

### Свой logback-конфиг

Wiretap кладёт в jar дефолтные `logback-spring.xml` и `logback-access.xml`
— именно за счёт них JSON-вывод работает «из коробки». Если в вашем
приложении есть собственный `src/main/resources/logback-spring.xml`, он
перекроет дефолтный (приоритет classpath). Чтобы сохранить wiretap-овские
encoder'ы, подключайте фрагменты явно:

```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
    <include resource="logback-console-appender.xml"/>
    <!-- сюда ваши кастомные appender'ы -->
</configuration>
```

То же самое относится к `logback-access.xml` и
`logback-access-console-appender.xml`.

## Поля лога приложения

Стандартные поля, которые Wiretap добавляет к каждому `log.info(...)` / `log.error(...)`:

| Поле | Значение по умолчанию | Источник |
|---|---|---|
| `wiretap.app-log.fields.timestamp` | `@timestamp` | Время события |
| `wiretap.app-log.fields.env` | `env` | `spring.profiles.active` |
| `wiretap.app-log.fields.system` | `system` | `spring.application.name` |
| `wiretap.app-log.fields.instance` | `inst` | Переменная окружения `HOSTNAME` |
| `wiretap.app-log.fields.trace-id` | `trace_id` | MDC `traceId` |
| `wiretap.app-log.fields.span-id` | `span_id` | MDC `spanId` |
| `wiretap.app-log.fields.level` | `level` | Уровень лога |
| `wiretap.app-log.fields.thread-name` | `thread_name` | Имя потока |
| `wiretap.app-log.fields.logger-name` | `logger` | Имя логгера (без stack trace) |
| `wiretap.app-log.fields.message` | `message` | Отформатированное сообщение (маскированное) |
| `wiretap.app-log.fields.http-info` | `http_info` | MDC `HTTP-REQUEST-LOG` как JSON |
| `wiretap.app-log.fields.extra` | `extra` | MDC `LOG_EXTRA` как JSON |
| `wiretap.app-log.fields.caller-class` | `caller_class` | Класс вызывающего (отключено по умолчанию) |
| `wiretap.app-log.fields.caller-method` | `caller_method` | Метод вызывающего (отключено по умолчанию) |
| `wiretap.app-log.fields.caller-line` | `caller_line` | Строка вызывающего (отключено по умолчанию) |
| `wiretap.app-log.fields.caller-file` | `caller_file` | Файл вызывающего (отключено по умолчанию) |

Поля caller-data требуют захвата стека вызовов и отключены по умолчанию.
Включите их, если нужна точная информация об источнике лога (с накладными расходами на CPU):

```yaml
wiretap:
  app-log:
    visibility-settings:
      CALLER_CLASS: true
      CALLER_METHOD: true
      CALLER_LINE: true
```

Переименование полей:

```yaml
wiretap:
  app-log:
    fields:
      logger-name: class   # вернуть старое имя
      thread-name: thread
```

### Глушим шумный логгер

Wiretap работает поверх Spring Boot + Logback, поэтому любой логгер приглушается
(или выключается) штатным `logging.level.*` — без отдельной настройки wiretap.
Например, чтобы убрать болтливые спаны `brave.Tracer`:

```yaml
logging:
  level:
    brave.Tracer: OFF          # выключить полностью; либо WARN / ERROR, чтобы поднять порог
```

## Дополнительные структурированные поля

Поле `extra` в каждой записи лога приложения заполняется из MDC-ключа `LOG_EXTRA`.
`ExtraAppLogContextKeeper` — thread-bound утилита для управления этим ключом: хранит
все дополнительные поля в одном JSON-объекте, не засоряя корневую структуру лога.

```java
ExtraAppLogContextKeeper.putExtraField("order_id", "ord-42");
ExtraAppLogContextKeeper.putExtraField("step", "validation");
log.info("Шаг оплаты завершён");
ExtraAppLogContextKeeper.clearExtraContext();
```

`extra` в итоговой записи лога:

```json
{ "order_id": "ord-42", "step": "validation" }
```

| Метод | Описание |
|---|---|
| `putExtraField(key, value)` | Добавить или обновить поле в extra-контексте текущего потока |
| `removeExtraField(key)` | Удалить одно поле; убирает MDC-ключ, если полей больше не осталось |
| `clearExtraContext()` | Очистить все дополнительные поля текущего потока |

MDC не распространяется автоматически на дочерние потоки. При запуске асинхронной работы
(`@Async`, `CompletableFuture`, parallel streams) копируйте и восстанавливайте MDC вручную
или используйте `TaskDecorator`. Поля `extra` появляются **только в логах приложения** — в
HTTP access-логах они отсутствуют.

## Настройка имён полей access-лога

Имена полей по умолчанию соответствуют схеме Wiretap. Любое имя можно переопределить в `application.yml`:

```yaml
wiretap:
  access-log:
    fields:
      timestamp: "@timestamp"
      trace-id: trace_id
      http-info: http
      http:
        return-code: status
        duration: elapsed_ms
        request-url: path
        request-body: req
        response-body: resp
```

Изменение применяется и к входящим access-логам, и к исходящим HTTP-логам, так что все записи
имеют одинаковую структуру.

| Свойство | Значение по умолчанию |
|---|---|
| `wiretap.access-log.fields.timestamp` | `@timestamp` |
| `wiretap.access-log.fields.env` | `env` |
| `wiretap.access-log.fields.system` | `system` |
| `wiretap.access-log.fields.instance` | `inst` |
| `wiretap.access-log.fields.lb-trace-id` | `lb_trace_id` |
| `wiretap.access-log.fields.trace-id` | `trace_id` |
| `wiretap.access-log.fields.span-id` | `span_id` |
| `wiretap.access-log.fields.level` | `level` |
| `wiretap.access-log.fields.message` | `message` |
| `wiretap.access-log.fields.http-info` | `http_info` |
| `wiretap.access-log.fields.http.return-code` | `return_code` |
| `wiretap.access-log.fields.http.method` | `http_method` |
| `wiretap.access-log.fields.http.direction` | `direction` |
| `wiretap.access-log.fields.http.url` | `request_url` |
| `wiretap.access-log.fields.http.protocol` | `protocol` |
| `wiretap.access-log.fields.http.duration` | `duration` |
| `wiretap.access-log.fields.http.source-port` | `source_port` |
| `wiretap.access-log.fields.http.request-headers` | `request_headers` |
| `wiretap.access-log.fields.http.response-headers` | `response_headers` |
| `wiretap.access-log.fields.http.request-params` | `request_params` |
| `wiretap.access-log.fields.http.request-body` | `request_body` |
| `wiretap.access-log.fields.http.request-body-length` | `request_body_length` |
| `wiretap.access-log.fields.http.response-body` | `response_body` |
| `wiretap.access-log.fields.http.response-body-length` | `response_body_length` |
| `wiretap.access-log.fields.http.xml-body-type` | `xml_body_type` |

## Добавление собственных полей (SPI)

Wiretap предоставляет три SPI-интерфейса для расширения того, что попадает в лог:

- **`WiretapAccessFieldProvider`** — добавляет поля в HTTP access-логи (входящие и исходящие HTTP-вызовы).
- **`WiretapLogFieldProvider`** — добавляет поля в логи приложения (`log.info(...)`, `log.error(...)` и т. д.).
- **`HttpAccessEventPostProcessHandler`** — реагирует на уже сериализованную JSON-строку access-лога (например, публикует Micrometer-метрики из полей response body) — см. [Пост-обработка access-лог событий](#пост-обработка-access-лог-событий).

### Поля HTTP access-лога

Реализуйте `WiretapAccessFieldProvider` как Spring-бин — Wiretap подхватит его автоматически:

```java
@Component
public class TenantIdFieldProvider implements WiretapAccessFieldProvider {
    @Override public String fieldName() { return "tenant_id"; }

    @Override public Object value(IAccessEvent event) {
        String raw = event.getRequestHeaderMap().get("X-Tenant-ID");
        return raw == null ? null : raw.toLowerCase();
    }
}
```

Возврат `null` из `value(...)` пропускает поле для данного события.
Для провайдеров с несколькими полями или с «сырым» JSON-выводом переопределите `writeTo(...)` напрямую.

### Поля лога приложения

Реализуйте `WiretapLogFieldProvider` как Spring-бин, чтобы добавить поля к каждой строке `log.info(...)`:

```java
@Component
public class TenantIdLogFieldProvider implements WiretapLogFieldProvider {
    @Override public String fieldName() { return "tenant_id"; }

    @Override public Object value(ILoggingEvent event) {
        return event.getMDCPropertyMap().get("tenant-id");
    }
}
```

Обратите внимание: Logback-access и SLF4J MDC работают в раздельных контекстах. Поля, доступные
через `IAccessEvent` (например, заголовки запроса), недоступны внутри `WiretapLogFieldProvider` —
читайте их из MDC-ключей, установленных выше по стеку через `WiretapHeadersProperties` или
servlet-фильтр.

### Пост-обработка access-лог событий

Для сценариев, где нужно реагировать на уже сериализованный JSON access-лога —
например, опубликовать Micrometer-счётчик, тегированный полем из response body —
реализуйте `HttpAccessEventPostProcessHandler`:

```java
public interface HttpAccessEventPostProcessHandler {
    void performPostProcess(byte[] encodedBytes);
}
```

Wiretap вызывает `performPostProcess(...)` из `LazyJsonAccessEncoder.encode()`
*после* того, как строка access-лога сериализована в JSON, но *до* записи
в appender. Зарегистрируйте Spring-бин — Wiretap подхватит его автоматически
через `@ConditionalOnBean`:

```java
@Component
public class PaymentDecisionMetricsHandler implements HttpAccessEventPostProcessHandler {

    private final MeterRegistry registry;
    private final ObjectMapper mapper;

    public PaymentDecisionMetricsHandler(MeterRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    @Override
    public void performPostProcess(byte[] encodedBytes) {
        try {
            JsonNode root = mapper.readTree(encodedBytes);
            String url = root.at("/http_info/request/url").asText();
            if (!url.contains("/payments/")) return;
            String decision = root.at("/http_info/response/body/decision").asText("unknown");
            registry.counter("payments.decision", "outcome", decision).increment();
        } catch (IOException ignored) {
            // ни в коем случае не ломаем pipeline логирования
        }
    }
}
```

Типичные сценарии:

- Публикация Micrometer-счётчиков или distribution'ов, тегированных
  полями из response body (сумма платежа, decision code, ID партнёра).
- Эмит параллельного аудит-события в отдельный sink (например, audit-топик)
  без повторного парсинга payload в бизнес-коде.
- Sample-based forensic capture: записать 1/1000 сырых access-строк в
  cold-storage для последующего replay.

**Ограничения:**

- Handler выполняется синхронно на потоке логирования (или, при
  `wiretap.async-logging.enabled=true`, на потоке `AsyncAppender`).
  Делайте его lock-free и ограниченным по времени — долгие операции
  забивают pipeline.
- Не бросайте исключения. Оборачивайте парсинг в `try/catch` — иначе
  внутренний exception сломает вызывающий логгер.
- На одно событие вызывается ровно один handler. Если нужен fan-out
  нескольким подписчикам — напишите диспетчерский handler.

## Проброс заголовков

По умолчанию следующие входящие заголовки запроса копируются в SLF4J MDC под
ключами, равными имени заголовка: `x-request-id`, `x-session-key`, `lb-trace-id`.
Внутри потока запроса их можно прочитать через `MDC.get("x-request-id")` или
обратиться к ним из любого Logback `PatternLayout`-токена (`%X{x-request-id}`).

В JSON-поля лога приложения wiretap'а эти заголовки **автоматически не
попадают**. Дефолтный encoder выводит фиксированную схему плюс только заранее
известные MDC-ключи (`traceId`, `spanId`, `HTTP-REQUEST-LOG`,
`KAFKA-MESSAGE-LOG`, `LOG_EXTRA`). Чтобы проброшенный заголовок стал полем
верхнего уровня в каждой строке `log.info(...)`, зарегистрируйте
`WiretapLogFieldProvider`, который читает значение обратно из MDC — пример SPI
выше (`TenantIdLogFieldProvider`).

Переопределите список проброшенных заголовков, если ваша инфраструктура
использует другие соглашения:

```yaml
wiretap:
  headers:
    forward-to-mdc:
      - x-request-id
      - x-correlation-id
      - x-trace-id
```

Чтобы дополнительно вывести значение заголовка как поле в access-логе (например, `session_key`),
используйте `WiretapAccessFieldProvider`. Logback-access работает в контексте, отдельном от
SLF4J MDC, поэтому заголовок необходимо читать напрямую из события. Провайдер ниже также
проверяет заголовки ответа в качестве запасного варианта — это удобно, когда сессионный ключ
устанавливается в ответе (например, SOAP):

```java
@Component
public class SessionKeyFieldProvider implements WiretapAccessFieldProvider {
    @Override public String fieldName() { return "session_key"; }

    @Override public Object value(IAccessEvent event) {
        String v = event.getRequestHeaderMap().get("x-session-key");
        return v != null ? v : event.getResponseHeaderMap().get("x-session-key");
    }
}
```

## Что логируется

Wiretap перехватывает HTTP-трафик из шести источников, каждый из которых настраивается отдельно.
У каждого источника свой префикс свойства:

| Трафик | Префикс | Управление |
|---|---|---|
| Входящий (servlet) | `wiretap.rest-controllers.*` | Всегда включён |
| Исходящий `RestTemplate` | `wiretap.rest-template-interceptor.*` | `.enabled=false` для отключения |
| Исходящий `RestClient` | `wiretap.rest-client-interceptor.*` | `.enabled=false` для отключения |
| Исходящий `FeignClient` | `wiretap.feign-client-interceptor.*` | `.enabled=false` для отключения |
| Исходящий `WebClient` / `GraphQLWebClient` | `wiretap.web-client-interceptor.*` | `.enabled=false` для отключения |
| Исходящий `WebServiceTemplate` (SOAP) | `wiretap.web-service-template-interceptor.*` | `.enabled=false` для отключения |
| Исходящий Kafka-продьюсер | `wiretap.kafka-producer-interceptor.*` | `.enabled=false` для отключения |
| Входящий Kafka-консьюмер | `wiretap.kafka-consumer-interceptor.*` | `.enabled=false` для отключения |

### Видимость полей

Каждый источник поддерживает флаги видимости на уровне поля. Поле можно отключить глобально,
а затем включить для конкретных URL-паттернов (и наоборот):

```yaml
wiretap:
  rest-controllers:
    visibility-settings:
      REQUEST_BODY: false        # не логировать тела входящих запросов по умолчанию
    specific-http-info-settings:
      - match-url-pattern: ".*/orders/.*"
        visibility-settings:
          REQUEST_BODY: true     # но логировать на эндпойнте /orders
```

Доступные флаги: `REQUEST_URL`, `REQUEST_HEADERS`, `REQUEST_PARAMS`,
`REQUEST_BODY`, `RESPONSE_HEADERS`, `RESPONSE_BODY`.

### Захват хедеров и wildcard `*`

Для каждого HTTP-источника есть пара явных allow-листов —
`request-headers` и `response-headers`, для Kafka — общий список
`headers`. В лог попадают только хедеры, перечисленные в этих
списках. Дефолты намеренно короткие (`Content-Type` и
`X-Forwarded-For` для HTTP, `x-trace-id` и `x-request-id` для Kafka),
потому что трафик в проде часто несёт чувствительные значения вроде
`Authorization` или `Cookie`, которые не должны попадать в логи
случайно.

Если полный набор хедеров заранее неизвестен — это бывает на старте
проекта, при отладке или когда апстрим-прокси добавляет произвольные
correlation-поля — используйте wildcard:

```yaml
wiretap:
  rest-controllers:
    request-headers: ['*']        # логировать все хедеры входящих запросов
  web-client-interceptor:
    response-headers: ['*']       # логировать все хедеры ответов WebClient
  kafka-producer-interceptor:
    headers: ['*']                # логировать все хедеры отправленных записей
```

Wildcard поддерживается в `request-headers`, `response-headers` (для
всех HTTP-источников, входящих и исходящих, включая SOAP `MimeHeaders`
плюс соответствующие HTTP-хедеры транспорта) и в Kafka `headers`,
включая per-URL `specific-http-info-settings` и per-topic
`specific-topic-settings` overrides. Если в списке есть `*`, остальные
элементы игнорируются (сравнение со звёздочкой регистронезависимое).
Wildcard **не** применяется к `wiretap.headers.forward-to-mdc` — этот
список остаётся явным намеренно, иначе произвольные хедеры раздули
бы каждую строку лога и могли утечь в downstream через MDC-проброс.

Wildcard захватывает значения хедеров как есть — wiretap **не**
вырезает `Authorization`, `Cookie` или другие чувствительные хедеры
сам. Для Kafka зарегистрируйте `KafkaHeaderMaskingHandler` для
маскирования значений. Для HTTP — настройте `wiretap.message-masking`
или скрабьте на уровне аппендера. Не включайте `*` на входящем
трафике без плана на эти два хедера.

### Ограничение размера тела и маскирование

```yaml
wiretap:
  rest-controllers:
    http-body-settings:
      max-body-length: 10000        # обрезать тела длиннее этого
      max-field-length: 1000        # обрезать строковые поля внутри JSON-тел
      enable-body-truncating: true
      enable-body-masking: true     # вызывать HttpBodyFieldMaskingHandler для каждого поля тела
    enable-url-masking: true        # вызывать HttpUrlMaskingHandler для URL запроса
    enable-request-params-masking: true   # вызывать HttpRequestParamsMaskingHandler для query-параметров
```

Wiretap предоставляет четыре независимых SPI-интерфейса для маскирования. Регистрируйте
только те бины, которые нужны — каждый контекст независим:

| Интерфейс | Применяется к | Активация |
|---|---|---|
| `io.wiretap.applog.message.handler.MessageMaskingHandler` | поле `message` в логах приложения | бин + `wiretap.message-masking=true` (по умолчанию) |
| `io.wiretap.http.message.settings.body.HttpBodyFieldMaskingHandler` | каждое поле в телах HTTP-запросов и ответов (рекурсивно, без знания URL) | бин + `enable-body-masking=true` |
| `io.wiretap.http.message.settings.body.HttpBodyMaskingHandler` | разобранный JSON-body как целое, per-URL (структурно — замаскировать конкретные поля на конкретных эндпоинтах) | бин + `enable-body-masking=true`; срабатывает первый handler, чей `appliesTo(url)` вернул `true` |
| `io.wiretap.http.message.HttpUrlMaskingHandler` | полный URL запроса (path + query string) | бин + `enable-url-masking=true` |
| `io.wiretap.http.message.HttpRequestParamsMaskingHandler` | каждое значение query-параметра в `request_params` | бин + `enable-request-params-masking=true` (по умолчанию) |

`HttpBodyMaskingHandler` (структурный per-URL) и
`HttpBodyFieldMaskingHandler` (рекурсивный per-field) композируются:
сначала структурный handler (если он подошёл) применяется к JSON-tree,
потом рекурсивный field-handler (если зарегистрирован) пробегает по
результату. Структурный — для правил «маскировать поле X на эндпоинте
Y», рекурсивный — для глобальных паттернов строк:

```java
@Component
public class CardLimitsMasker implements HttpBodyMaskingHandler {
    private static final List<String> FIELDS = List.of("remaining_auth", "remaining_cash");

    @Override public boolean appliesTo(String url) {
        return url.contains("/api/cardlimits");
    }

    @Override public JsonNode mask(JsonNode body) {
        if (body.isObject()) {
            FIELDS.forEach(name -> Optional.ofNullable(body.findValue(name))
                    .ifPresent(v -> ((ObjectNode) body).put(name, "***")));
        }
        return body;
    }
}
```

Если бин для контекста не зарегистрирован, данные проходят без изменений независимо от
значения флага. Точечное управление через `specific-http-info-settings[].enable-body-masking`
(и `enable-url-masking`) работает по тому же правилу — хендлер вызывается только если
и флаг, и бин присутствуют.

Отключить маскирование сообщений глобально, даже при наличии бина:

```yaml
wiretap:
  message-masking: false
```

### Исключение URL из логирования

```yaml
wiretap:
  rest-controllers:
    exclude-request-patterns:
      - "/actuator/.*"
      - "/health"
```

### Буферизация тела между фильтрами

Стандартный `TeeFilter` из logback-access умеет захватывать тело
только при условии, что цепочка сервлет-фильтров отработала полностью
и запись шла через оригинальные потоки. Если кастомный фильтр,
exception resolver или фреймворк (например, Spring Security) подменяет
поток ответа или прерывает цепочку, к моменту работы access-encoder'а
тело уже потеряно.

Для таких edge-кейсов сохраняйте тело через `BufferedHttpBodyHolder` —
Wiretap прочитает его, когда будет собирать `http_info`:

```java
@Component
public class CapturedBodyExceptionResolver implements HandlerExceptionResolver {
    @Override
    public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse res,
                                         Object handler, Exception ex) {
        String reqBody  = readMyCachedRequestBody(req);
        String respBody = renderErrorResponse(ex);
        BufferedHttpBodyHolder.put(req, new BufferedHttpMessageInfo(
                reqBody,  reqBody.length(),
                respBody, respBody.length()));
        // ...записываем ответ как обычно
        return new ModelAndView();
    }
}
```

Буфер кладётся в атрибут `HttpServletRequest` — он привязан к
жизненному циклу запроса (никакого `ThreadLocal`, безопасно под
виртуальными потоками, ручная очистка не нужна).

## WebClient: non-blocking семантика и известные ограничения

`WebClient` — неблокирующий HTTP-клиент, у которого два главных преимущества:
(1) много одновременных соединений на один event-loop тред и (2) backpressure
для стриминговых тел. Логирование HTTP-обмена неизбежно затрагивает оба,
поэтому Wiretap старается оставаться прагматичным на горячем пути и честно
описывает компромиссы.

**Что фильтр делает полностью неблокирующе**

- Сама реактивная цепочка — захват, лог, replay — остаётся внутри
  Reactor-операторов, без блокирующих I/O-вызовов.
- Модель connection-per-thread остаётся N:1; event-loop тред не закреплён
  за конкретным запросом.

**Что по умолчанию выполняется синхронно на event-loop треде**

- Jackson сериализует захваченный HTTP info (микросекунды для маленьких
  payload'ов, до миллисекунд для крупных).
- Вызов `log.info(...)` идёт через цепочку аппендеров Logback; встроенные
  `ConsoleAppender` и `RollingFileAppender` пишут синхронно. Под нагрузкой
  (высокий QPS, большие тела) это становится доминирующей стоимостью.

**Streaming-aware захват (автоматически)**

Ответы со следующими content-type'ами логируются только с метаданными —
body Flux не объединяется и не подменяется, поэтому SSE / NDJSON / большие
загрузки проходят неизменно:

```
text/event-stream         application/grpc
application/x-ndjson      application/grpc+proto
application/octet-stream  application/grpc+json
multipart/x-mixed-replace
```

В поле `response_body` будет маркер
`[streaming response — body not captured]` вместо самого тела. Никакой
дополнительной настройки не требуется.

**Visibility-aware захват**

Когда `REQUEST_BODY` или `RESPONSE_BODY` visibility выключен (глобально или
на уровне URL), соответствующее тело вообще не захватывается — декоратор
не оборачивает запрос, drain ответа не выполняется. Это экономит и память,
и CPU по сравнению с подходом «захватить и потом отбросить».

**Ограниченный захват**

Длина захваченной строки для лог-линии жёстко ограничена
`http-body-settings.max-body-length` (по умолчанию 2KB). Тела больше этого
лимита получают маркер `...[truncated]` в логе; полное тело при этом
доставляется приложению без изменений.

**Асинхронное логирование (рекомендуется под нагрузкой WebClient)**

Оберните встроенные аппендеры Wiretap в Logback `AsyncAppender`:

```yaml
wiretap:
  async-logging:
    enabled: true
    queue-size: 1024
    never-block: true              # дропать события при переполнении вместо блокировки
    discarding-threshold: 0        # 0 = никогда не дропать (по умолчанию queueSize/5 от Logback)
```

С включённым флагом `log.info(...)` возвращается на event-loop тред сразу
после постановки события в очередь; реальная запись происходит на отдельном
worker-треде. Проверить можно по имени треда в логе для WebClient-записи:
должно быть `AsyncAppender-Worker-...`, а не `reactor-http-nio-N`.

**WebClient + Wiretap vs RestTemplate + Wiretap**

| | RestTemplate + Wiretap | WebClient + Wiretap |
|---|---|---|
| Модель тредов | Один тред на запрос | N запросов на event-loop тред |
| Захват тела | Всегда полностью (in-memory) | Ограничен `max-body-length` для лог-линии |
| Стриминговые тела | n/a (RestTemplate не стримит) | Авто-пропуск |
| Цена синхронного `log.info()` | Дёшево (dedicated тред) | Блокирует event-loop пока appender не вернётся — включайте `wiretap.async-logging.enabled` |

Если ваши WebClient-запросы — в основном небольшие REST/GraphQL и QPS
умеренный, дефолты подойдут. Под высокий QPS, большие тела или стриминг
включайте `async-logging` и проверяйте, что `max-body-length` достаточно
плотный, чтобы предсказуемо ограничивать память на запрос.

## Логирование Kafka

Если на classpath присутствует `spring-kafka`, Wiretap пишет ровно
одну JSON-запись `kafka_info` на сообщение в каждую сторону — producer
и consumer — с единой схемой: полный snapshot записи, `status`
(`SUCCESS` / `ERROR`) и исход операции.

### Продьюсер

На producer-стороне используется Spring Kafka
`org.springframework.kafka.support.ProducerListener`. Hook срабатывает
**после** того, как broker подтвердил (или отверг) отправку, поэтому
одна запись несёт и pre-serialization snapshot (`key`, `value`,
headers как сформировало приложение), и broker-side координаты
(`partition`, `offset`):

```json
{
  "@timestamp": "...",
  "level": "INFO",
  "logger": "io.wiretap.kafka.KafkaLogSink",
  "message": "Sent outgoing kafka message orders.events",
  "kafka_info": {
    "direction": "OUTGOING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "key": "ord-42",
    "key_length": 6,
    "value": "{\"orderId\":\"ord-42\",\"amount\":100}",
    "value_length": 33,
    "headers": { "x-trace-id": "0123456789abcdef", "b3": "..." },
    "timestamp": "2026-05-07T10:14:32.918Z",
    "status": "SUCCESS"
  }
}
```

При ошибке доставки та же строка приходит на уровне `WARN` с
`status=ERROR` плюс `error_class` и `error_message`:

```json
{
  "level": "WARN",
  "message": "Failed to send outgoing kafka message orders.events",
  "kafka_info": {
    "direction": "OUTGOING",
    "topic": "orders.events",
    "key": "ord-42",
    "value": "{...}",
    "status": "ERROR",
    "error_class": "org.apache.kafka.common.errors.TimeoutException",
    "error_message": "Expiring 1 record(s) for orders.events-3: 30000 ms has passed since batch creation"
  }
}
```

Auto-configured `KafkaTemplate` от Spring Boot подхватывает
`ProducerListener` bean автоматически через `ObjectProvider`. Шаблоны,
собранные вручную, подключают listener явно — см. «Кастомный
KafkaTemplate» ниже.

```yaml
wiretap:
  kafka-producer-interceptor:
    enabled: true                            # выключить интерсептор
    headers:                                 # какие заголовки попадают в лог
      - x-trace-id
      - x-request-id
    visibility-settings:
      VALUE: true                            # также: TOPIC PARTITION CLIENT_ID KEY HEADERS TIMESTAMP STATUS ...
    enable-value-masking: true               # вызывать KafkaValueMaskingHandler для key/value
    enable-headers-masking: true             # вызывать KafkaHeaderMaskingHandler для заголовков
    enable-topic-masking: true               # вызывать KafkaTopicMaskingHandler для имени топика
    message-body-settings:
      enable-value-truncating: true
      max-value-length: 2000
      enable-value-masking: true             # комбинируется с enable-value-masking выше
    exclude-topic-patterns:
      - ".*\\.internal\\..*"
    specific-topic-settings:
      - match-topic-pattern: "orders\\..*"
        visibility-settings:
          VALUE: false                       # не логировать тело на семействе orders.*
```

Три опциональных SPI — зарегистрируйте бин нужного типа:

| Интерфейс | Применяется к |
|---|---|
| `io.wiretap.kafka.message.KafkaValueMaskingHandler` | `key` и `value` |
| `io.wiretap.kafka.message.KafkaHeaderMaskingHandler` | значение каждого заголовка |
| `io.wiretap.kafka.message.KafkaTopicMaskingHandler` | имя топика |

Те же SPI применяются и на consumer-стороне — один бин покрывает обе
стороны.

### Консьюмер

На consumer-стороне используется Spring Kafka
`org.springframework.kafka.listener.RecordInterceptor`, прикреплённый
к listener-контейнерам через bean-коллекцию `ContainerCustomizer`'ов
в Spring Boot. Hook срабатывает на `success(record, consumer)` /
`failure(record, exception, consumer)` — **после** возврата из
`@KafkaListener`-метода — поэтому одна запись несёт:

- pre-deserialization snapshot записи как её доставил broker;
- `duration` обработки listener'ом в миллисекундах;
- `status` (`SUCCESS` / `ERROR`), а при ошибке — `error_class` /
  `error_message`.

Поскольку callback выполняется внутри observation-спана listener'а
Spring Kafka, MDC к моменту работы wiretap'а уже содержит
`traceId` / `spanId` — `kafka_info` сразу коррелирует с собственными
логами листенера, без парсинга заголовков. Это работает и когда
producer не пропагирует трейс — observation в таком случае открывает
новый root-span на consumer-стороне, и wiretap подбирает его
автоматически.

Если listener observation выключен
(`spring.kafka.listener.observation-enabled=false`), wiretap
fallback'ом читает `b3` / `traceparent` из record-headers —
propagation от producer'а с observation не теряется.

```json
{
  "level": "INFO",
  "message": "Processed incoming kafka message orders.events in 47ms",
  "kafka_info": {
    "direction": "INCOMING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "client_id": "checkout-api-consumer-1",
    "group_id": "checkout-group",
    "key": "ord-42",
    "value": "{\"orderId\":\"ord-42\"}",
    "headers": { "b3": "...", "x-trace-id": "abc" },
    "timestamp": "2026-05-07T10:14:32.918Z",
    "timestamp_type": "CREATE_TIME",
    "duration": 47,
    "status": "SUCCESS"
  }
}
```

При исключении в листенере уровень становится `WARN`:

```json
{
  "level": "WARN",
  "message": "Failed to process incoming kafka message orders.events after 350ms",
  "kafka_info": {
    "direction": "INCOMING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "key": "ord-42",
    "value": "{...}",
    "duration": 350,
    "status": "ERROR",
    "error_class": "com.example.OrderValidationException",
    "error_message": "amount must be positive"
  }
}
```

Listener, который висит дольше `max.poll.interval.ms`, тоже даёт
запись: Spring Kafka бросает исключение, которое попадает в
`failure(...)`. Единственный сценарий, где лог потеряется — JVM crash
прямо посреди обработки, и тут никакой fallback не поможет.

```yaml
wiretap:
  kafka-consumer-interceptor:
    enabled: true
    visibility-settings:
      VALUE: true
    exclude-topic-patterns:
      - "__consumer_offsets"
```

### Справочник полей `kafka_info`

Схема `kafka_info` одна для обеих сторон; часть полей появляется
только на одной стороне или только при ошибках. Любое поле можно
скрыть через `visibility-settings` (enum-ключи соответствуют именам
из колонок ниже).

| Поле | Тип | Producer (OUTGOING) | Consumer (INCOMING) | Заметки |
|---|---|---|---|---|
| `direction` | string | `OUTGOING` | `INCOMING` | Всегда. |
| `topic` | string | всегда | всегда | Топик отправки/чтения. |
| `partition` | int | broker, на ack | всегда | На producer'е — post-ack из `RecordMetadata`. |
| `offset` | long | broker, на ack | всегда | На producer'е — post-ack. |
| `client_id` | string | (нет) | всегда | На producer'е недоступен через `ProducerListener` — Kafka client отдаёт client-id только через JMX/Micrometer метрики. |
| `group_id` | string | — | всегда | `group.id` consumer'а. |
| `key` | string | всегда | всегда | `String.valueOf(key)` — типизированное значение до сериализации. |
| `key_length` | long | всегда | всегда | Длина байтов `key` (UTF-8 для строк, native для byte[]). |
| `value` | string | всегда | всегда | Аналогично `key` — типизированное значение до сериализации. |
| `value_length` | long | всегда | всегда | Длина байтов `value`. |
| `headers` | object | настраивается | настраивается | Имена из `wiretap.kafka-*-interceptor.headers` (`['*']` — все хедеры). Multi-value склеивается через `;`. |
| `timestamp` | ISO-8601 string | условно | всегда | Подробно — в блоке «Про `timestamp`» ниже. |
| `timestamp_type` | string | (обычно нет) | всегда | `CREATE_TIME` / `LOG_APPEND_TIME`. См. ниже. |
| `duration` | long (ms) | (нет) | всегда | Время вызова listener'а. Producer-latency покрывают Kafka-native метрики. |
| `status` | enum | `SUCCESS` / `ERROR` | `SUCCESS` / `ERROR` | Исход операции. |
| `error_class` | string | при ошибке | при ошибке | FQN исключения. |
| `error_message` | string | при ошибке | при ошибке | `exception.getMessage()`. |

### Форматирование JSON-payload

Если `key` или `value` парсится как JSON-объект или массив, wiretap
прогоняет его через `OBJECT_MAPPER.writerWithDefaultPrettyPrinter()`
перед эмитом, и в итоге строка содержит реальные переносы `\n`. Системы
агрегации логов (Kibana / Splunk / Grafana Loki) рендерят это как
отформатированный многострочный блок, а не схлопнутый однострочник.
Скаляры, обычные строки и невалидный JSON эмитятся как есть, без
трансформации.

В финальном JSON поле `value` **остаётся строкой** — wiretap
сознательно не встраивает payload как вложенный объект в
`kafka_info.value`. У разных топиков структура value разная; превращение
в nested object заставило бы агрегатор индексировать каждый вариант
схемы, что в Elasticsearch / OpenSearch приведёт к ошибке `mapping
conflict`, как только два сообщения используют одно и то же
top-level-поле с разными типами.

Pretty-print применяется **после** `KafkaValueMaskingHandler` (handler
всё так же получает исходную single-line строку — existing regex-маски
не ломаются) и **до** `enable-value-truncating` (лимит длины
применяется к финальному pretty-printed тексту).

### Про `timestamp` и `timestamp_type`

Поле часто читают неправильно — выделено отдельным блоком.

#### Откуда берётся

**Producer (`OUTGOING`)**:
- Если приложение явно задало timestamp в record'е
  (`new ProducerRecord<>(topic, partition, timestamp, key, value)`)
  — пишем эту величину.
- Иначе, если `RecordMetadata.hasTimestamp()` истинно после ack'а
  брокера — пишем broker-side timestamp как fallback.
- Иначе поле опускаем.
- `timestamp_type` на producer-стороне **обычно отсутствует** —
  Kafka client не отдаёт его для исходящих записей.

**Consumer (`INCOMING`)**:
- Всегда `ConsumerRecord.timestamp()` — то значение, что broker
  сохранил и которое видят все consumer'ы. Какое именно — время
  отправки от продюсера (`CREATE_TIME`) или время записи у брокера
  (`LOG_APPEND_TIME`) — решает topic-config
  `message.timestamp.type` (default в Kafka 3.x — `CreateTime`).
- `timestamp_type` несёт дискриминатор (`CREATE_TIME` /
  `LOG_APPEND_TIME`). `NO_TIMESTAMP_TYPE` мы трактуем как
  «не задано» и поле опускаем.

#### Чего это **не** означает

- `OUTGOING` `timestamp` — это **не** «когда broker записал
  сообщение в лог». Это либо applic-supplied timestamp, либо
  best-effort broker-ack timestamp; на топике с
  `message.timestamp.type=LogAppendTime` эти времена могут заметно
  отличаться от реального момента log-append'а.
- `INCOMING` `timestamp` — это **не** «когда consumer получил
  запись». Для end-to-end latency сравните его с `@timestamp` самой
  `INCOMING` log-строки — разница даст «сколько прошло между
  записью на брокере и доставкой consumer'у».

#### Кастомный KafkaTemplate

`KafkaTemplate`, собранный вручную (multi-cluster, per-tenant),
не проходит через Spring Boot KafkaAutoConfiguration. Инжектируй
wiretap'овский `ProducerListener` и подключи явно:

```java
@Configuration
public class KafkaConfig {

    private final List<ProducerListener<Object, Object>> producerListeners;

    public KafkaConfig(List<ProducerListener<Object, Object>> producerListeners) {
        this.producerListeners = producerListeners;
    }

    @Bean("pciDssKafkaTemplate")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public KafkaTemplate<String, String> pciDssKafkaTemplate(ProducerFactory<String, String> factory) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(factory);
        producerListeners.forEach(l -> template.setProducerListener((ProducerListener) l));
        template.setObservationEnabled(true);   // для пропагации трейса в headers
        return template;
    }
}
```

`setProducerListener` — single-slot setter; если в приложении уже
есть свой listener, объедини оба через
`org.springframework.kafka.support.CompositeProducerListener` перед
`setProducerListener`.

#### Кастомные listener-container-factories

Auto-configured `ConcurrentKafkaListenerContainerFactory` от Spring Boot
подхватывает wiretap'овский `ContainerCustomizer` сам. Если ты собираешь
factory вручную (multi-cluster, per-tenant listener'ы), инжектируй
wiretap-customizer и применяй сам:

```java
@Configuration
public class KafkaConfig {

    private final List<ContainerCustomizer<Object, Object,
            ConcurrentMessageListenerContainer<Object, Object>>> customizers;

    public KafkaConfig(List<ContainerCustomizer<Object, Object,
            ConcurrentMessageListenerContainer<Object, Object>>> customizers) {
        this.customizers = customizers;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> listenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        // ... consumer factory, error handler и т.д.
        customizers.forEach(c -> factory.setContainerCustomizer(
                (ContainerCustomizer) c));
        return factory;
    }
}
```

`setContainerCustomizer` на factory — single-slot setter, поэтому если в
приложении уже есть свой `RecordInterceptor` (например, для retry-state
cleanup), оберни оба в
`org.springframework.kafka.listener.CompositeRecordInterceptor`
перед `setRecordInterceptor`.

### Распределённая трассировка через Kafka

В цепочке два участника:

1. **Пропагация на стороне producer'а** — `KafkaTemplate.send` должен
   вставить заголовок пропагации (`b3` или W3C `traceparent`,
   в зависимости от `management.tracing.propagation.type`). Это
   делает `spring.kafka.template.observation-enabled=true`. Без него
   ничего не уходит на проводе, и трейс обрывается на сервисе-
   отправителе.

2. **MDC на стороне consumer'а** — observation listener'а Spring Kafka
   открывает span вокруг каждой записи, берёт parent из propagation-
   заголовка (или открывает новый root span, если producer не
   пропагирует). Этот span кладёт `traceId` / `spanId` в MDC, а
   wiretap унаследует их естественно — наш `RecordInterceptor`
   вызывается внутри этого span'а. Свойство —
   `spring.kafka.listener.observation-enabled=true`.

Практический рецепт — включить оба:

```yaml
spring:
  kafka:
    template:
      observation-enabled: true
    listener:
      observation-enabled: true
```

`kafka_info` будет содержать `trace_id` даже когда включён только
listener flag (трейс просто не связан с producer'ом). Если listener
observation выключить совсем, wiretap fallback'ом извлекает
`b3` / `traceparent` напрямую из record-headers, так что producer с
propagation всё равно даёт скоррелированный consumer-лог.

Если оставить только `template.observation-enabled=true`,
`kafka_info`-строка wiretap'а на consumer'е всё равно получит
`trace_id`, но обычные `log.info` внутри listener'а останутся без
трейса — обычно нужно включить оба.

### Fire-and-forget и edge cases

`KafkaTemplate.send(...)` всегда асинхронный — возвращает
`CompletableFuture<SendResult<K, V>>`. «Fire-and-forget» на стороне
приложения (выбросить future, не звать `.get()`) **никак не влияет**
на wiretap-лог: `ProducerListener` подключён внутри template'а, и
`Callback`, который Spring Kafka регистрирует в
`KafkaProducer.send(...)`, срабатывает вне зависимости от того, что
прикладной код делает с future.

| Сценарий | Поведение wiretap'а |
|---|---|
| Broker подтвердил ack | `INFO Sent outgoing kafka message {topic}` + `status=SUCCESS` |
| Timeout / retry exhausted / broker отклонил | `WARN Failed to send outgoing kafka message {topic}` + `status=ERROR` + `error_class` / `error_message` |
| `KafkaProducer.close()` с pending-записями | Каждая pending-запись получает `ProducerFencedException` (или подобное) → `WARN` строка на каждую. |
| `max.block.ms` истёк прямо в `send()` (producer buffer переполнен) | `send` бросает **синхронно**, запись не попала в очередь producer'а. Callback не вызывается, лога нет. Exception улетает в caller — оберни fire-and-forget send в `try / catch` либо добавь `.whenComplete(...)` на future, если такие случаи хочется логировать самому. |
| JVM crash посреди send'а | Лога не будет — это никаким механизмом не лечится. |
| Прямой `KafkaProducer.send(...)` мимо `KafkaTemplate` | wiretap не увидит — auto-registration на template'е, не на raw producer'е. |

### Замечание про producer-IO thread

`onSuccess` / `onError` callback'и приходят на producer-IO thread'е
(`kafka-producer-network-thread | …`), а не на caller'е send'а. MDC
на этом thread'е по умолчанию пуст — `trace_id` всё-таки попадает в
`kafka_info OUTGOING`, потому что Spring Kafka observation
пробрасывает контекст через Micrometer Context Propagation
(автоматически конфигурируется Spring Boot'ом). Если propagation
явно отключён или версия Spring Boot слишком старая, producer-side
`kafka_info` может уйти без `trace_id`. Consumer-side это не
затрагивает — там observation-span открывается локально.

## Трассировка

Wiretap читает `trace_id` и `span_id` из активного контекста Micrometer Tracing
(любой бэкенд — Brave, OpenTelemetry и др.). Если на classpath присутствует Brave,
библиотека по умолчанию конфигурирует 64-битный single-header B3 propagator; отключить
это поведение можно через `wiretap.tracing.propagation.type.b3.enabled=false`.

Поле `lb_trace_id` берётся из входящего заголовка запроса `lb-trace-id` и предназначено
для ID, эмитируемых балансировщиком нагрузки.

## Метрики

Wiretap публикует Micrometer-метрики о **собственных** накладных расходах на
обработку — чтобы можно было оценить, сколько стоит логирование в проде.
Метрики активируются автоматически, если в Spring-контексте присутствует bean
`MeterRegistry` (обычно через `spring-boot-starter-actuator`); если такого
bean нет, библиотека ставит no-op-фасад и не добавляет ни одного байта overhead.

```yaml
wiretap:
  metrics:
    enabled: true             # главный выключатель (по умолчанию true)
    detailed-timings: false   # пофазные таймеры (parse/mask/truncate/serialize)
    histograms: false         # publishPercentileHistogram + p50/p95/p99
    tags:
      topic: false            # Kafka topic как тег (риск cardinality)
      status: true            # HTTP status в группах (2xx/3xx/4xx/5xx)
    async-appender:
      enabled: true           # gauges очереди, когда wiretap.async-logging.enabled=true
```

### Каталог метрик

Публикуются всегда (если метрики включены и `MeterRegistry` найден):

| Метрика                              | Тип                          | Теги                                                                | Описание |
|--------------------------------------|------------------------------|---------------------------------------------------------------------|----------|
| `wiretap.http.overhead`              | Timer (секунды)              | `direction`, `client`, `outcome`, `status`                          | Overhead логгера на один HTTP-запрос — время downstream-вызова исключено (см. примечание ниже) |
| `wiretap.http.requests`              | Counter                      | `direction`, `client`, `outcome`, `status`                          | Идёт парой с Timer |
| `wiretap.http.skipped`               | Counter                      | `direction`, `client`, `reason`                                     | Запросы, прошедшие мимо логирования |
| `wiretap.http.body.size`             | DistributionSummary (bytes)  | `direction`, `client`, `content_type_class`, `kind` (`request`/`response`) | Размер захваченного тела |
| `wiretap.http.body.capture.failures` | Counter                      | `direction`, `client`, `phase`                                      | Исключения в пайплайне тела, пишутся единообразно во всех клиентах (входящий servlet + все исходящие): `phase=capture` (чтение/парсинг тела) или `phase=serialize` (рендеринг JSON в MDC) |
| `wiretap.kafka.overhead`             | Timer                        | `direction` (`producer`/`consumer`), `outcome`                      | Полный overhead pipeline на одно Kafka-сообщение |
| `wiretap.kafka.messages`             | Counter                      | `direction`, `outcome`                                              | Идёт парой с Timer |
| `wiretap.kafka.skipped`              | Counter                      | `direction`, `reason`                                               | Причины skip (исключённый топик / null record) |
| `wiretap.kafka.message.size`         | DistributionSummary (bytes)  | `direction`                                                         | Размер value |
| `wiretap.kafka.body.capture.failures`| Counter                      | `direction`, `phase`                                                | Исключения в пайплайне тела Kafka — аналог `wiretap.http.body.capture.failures` (`phase=capture` маскирование/парсинг, `phase=serialize` рендеринг JSON) |

> **`wiretap.http.overhead` меряет стоимость логирования, а не latency запроса.**
> Для исходящих клиентов время downstream-вызова меряется в **наносекундах** и
> вычитается, поэтому таймер отражает только работу самого wiretap (захват,
> парсинг, маскирование, сериализация) без округления до миллисекунд. Вычитаемый
> downstream — это сетевой вызов для RestTemplate/RestClient/Feign, время до
> получения ответа (заголовки, а для буферизуемых ответов — и тело) для WebClient
> и SOAP round-trip для WebServiceTemplate. Для входящих (servlet) downstream
> вычитать не из чего, поэтому таймер покрывает весь JSON-рендеринг access-лога в
> `LazyJsonAccessEncoder.encode()` (все провайдеры, включая `http_info`). Поле лога
> `duration`/`elapsedTime` остаётся в миллисекундах (видимая пользователю latency) и
> не зависит от наносекундного таймера overhead. Для пофазной атрибуции включите
> phase-таймеры (`wiretap.metrics.detailed-timings`) ниже.

Под флагом `wiretap.metrics.detailed-timings=true`:

| Метрика                          | Теги                                                  | Описание |
|----------------------------------|-------------------------------------------------------|----------|
| `wiretap.body.phase`             | `phase`, `direction`, `client`, `content_type_class`  | Timer на одну фазу обработки тела. HTTP body эмитит `parse` / `mask` / `truncate` из `DefaultBodyParser` с реальными тегами `direction` (`incoming`/`outgoing`) и `client` (`servlet`/`webclient`/…). Kafka body эмитит те же три фазы из `KafkaLogSink.renderValue` с тегами `client=kafka` и `direction=producer`/`consumer`. |
| `wiretap.json.serialization`     | `sink`, `direction`, `client`                         | Время `ObjectMapper.writeValueAsString` |
| `wiretap.body.masker.invocation` | `masker_class`, `direction`                           | Время одного вызова `HttpBodyMaskingHandler` для HTTP-стороны или `KafkaValueMaskingHandler` для Kafka-стороны (`masker_class` = FQN хэндлера). |

Под `wiretap.async-logging.enabled=true`
(плюс `wiretap.metrics.async-appender.enabled=true`, по умолчанию true):

| Метрика                                  | Тип   | Теги         | Описание |
|------------------------------------------|-------|--------------|----------|
| `wiretap.async.appender.queue.size`      | Gauge | `appender`   | Сколько событий сейчас в очереди |
| `wiretap.async.appender.queue.capacity`  | Gauge | `appender`   | Лимит `queueSize` |
| `wiretap.async.appender.queue.remaining` | Gauge | `appender`   | Остаток (capacity − size) |

### Справочник значений тегов

- `direction`: `incoming` / `outgoing` (HTTP), `producer` / `consumer` (Kafka).
- `client`: `servlet` (incoming) / `webclient` / `restclient` / `resttemplate` / `feign` / `webservicetemplate` (HTTP); `kafka` (Kafka body pipeline — только на `wiretap.body.phase` и `wiretap.body.masker.invocation`).
- `outcome`: `success` / `client_error` (4xx) / `server_error` (5xx) / `exception` (HTTP); `success` / `error` (Kafka).
- `status`: `2xx` / `3xx` / `4xx` / `5xx` / `other` / `exception` — никогда не сырое значение кода.
- `content_type_class`: `json` / `xml` / `text` / `binary` / `other`.
- `phase`: на `wiretap.body.phase` — `parse` / `mask` / `truncate`; на `wiretap.{http,kafka}.body.capture.failures` — `capture` (чтение/парсинг тела) / `serialize` (рендеринг JSON в MDC).
- `reason`: `exclude_pattern` / `exclude_topic` / `streaming` / `unsupported_content_type` / `visibility_disabled` / `null_topic` / `null_record`.

### Сбор метрик

Совместите с `spring-boot-starter-actuator` и выбранным экспортёром
(`micrometer-registry-prometheus`, Datadog и т.д.). Все wiretap-метрики имеют
префикс `wiretap.`, поэтому одно Prometheus-правило покрывает всё:

```bash
curl localhost:8080/actuator/prometheus | grep '^wiretap_'
```

## Красивый вывод

Для локальной разработки установите `wiretap.pretty-print=true` — JSON будет
выводиться в многострочном формате. В продакшене оставляйте `false` (по умолчанию):
log-шипперы быстрее разбирают однострочный JSON.

В pretty-print режиме поле `stack_trace` рендерится **массивом строк**
(по одной строке трейса на элемент массива), а не одной embedded-строкой.
Причина: JSON-pretty-printer не умеет переносить внутри string-литерала
— без этого фокуса длинный stack trace превращается в горизонтальный
скролл в терминале. Лимиты (`wiretap.stacktrace.max-depth`,
`wiretap.stacktrace.max-length`) и тот же самый `ShortenedThrowableConverter`
работают в обоих режимах — отличается только форма поля в JSON:

```text
# pretty-print=false (продакшен, по умолчанию) — одна строка
"stack_trace": "java.lang.RuntimeException: boom\n\tat com.example..."

# pretty-print=true (локальная разработка) — массив строк
"stack_trace": [
  "java.lang.RuntimeException: boom",
  "\tat com.example.Foo.bar(Foo.java:42)",
  "\tat ..."
]
```

Не включайте pretty-print в окружениях, которые шлют логи в Elasticsearch /
OpenSearch — смена типа поля между string и array на одном индексе вызовет
mapping conflict.

## Совместимость

Wiretap публикуется в виде **одного артефакта на каждую тестируемую
patch-версию Spring Boot**. Имя координаты явно содержит целевую
версию, так что выбор однозначен:

| Артефакт                              | Spring Boot       | Java        | Статус |
|---------------------------------------|-------------------|-------------|--------|
| `wiretap-spring-boot-3.2.7-starter`   | 3.2.7 (baseline)  | 17, 21      | extended support — для legacy-потребителей |
| `wiretap-spring-boot-3.4.5-starter`   | 3.4.5             | 17, 21, 25  | активно тестируется |
| `wiretap-spring-boot-3.5.14-starter`  | 3.5.14            | 17, 21, 25  | активно тестируется, последний 3.x минор |
| `wiretap-spring-boot-4.0.6-starter`   | 4.0.6             | 17, 21, 25  | активно тестируется, первая SB 4 / Jackson 3 линия |

Разделение появилось потому, что Logback 1.5 перенёс `IAccessEvent` из
`ch.qos.logback.access.spi` в `ch.qos.logback.access.common.spi`, а
Spring Boot 4 принёс Jackson 3 (`tools.jackson.*`) и разнёс клиентские
модули — один jar не покрывает все активные ветки. Подключайте
координату, соответствующую вашей версии Spring Boot. Политика
deprecation и скрипты проверки в [`COMPATIBILITY.md`](./COMPATIBILITY.md).

> **Deprecated:** `io.github.alexander-kuznetsov:wiretap` (без суффикса
> `-spring-boot-X.Y.Z-starter`) заморожен на версии `0.1.6`. Мигрируйте
> на `wiretap-spring-boot-3.2.7-starter` — содержимое то же, дальнейшие
> патчи только под новой координатой.

Переопределение матрицы при сборке:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

## Сборка из исходников

```bash
./gradlew build
```

Тесты используют JUnit 5 + WireMock + AssertJ. На Java 25 Mockito требует флага
`-Dnet.bytebuddy.experimental=true`; он уже задан в `build.gradle`.

## Лицензия

Apache License 2.0 — см. [LICENSE](./LICENSE).
