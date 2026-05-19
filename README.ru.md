RU | [EN](README.md)

# Wiretap

> Структурированное JSON-логирование для Spring Boot приложений с перехватом HTTP-запросов и ответов
> через servlet, RestTemplate, RestClient, FeignClient, WebClient и WebServiceTemplate.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Статус:** `0.1.0-SNAPSHOT` — в разработке, публичный API нестабилен. Не использовать в продакшене.

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

```gradle
dependencies {
    implementation 'io.github.alexander-kuznetsov:wiretap:0.1.1'
}
```

Артефакт опубликован в **Maven Central** — `mavenCentral()`, дефолтный
репозиторий любого Spring Boot проекта, уже подходит без какой-либо
аутентификации.

Снапшоты между релизами лежат в публичном Central snapshots:

```gradle
repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
    implementation 'io.github.alexander-kuznetsov:wiretap:0.1.2-SNAPSHOT'
}
```

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

Wiretap предоставляет два SPI-интерфейса для добавления кастомных полей:

- **`WiretapAccessFieldProvider`** — добавляет поля в HTTP access-логи (входящие и исходящие HTTP-вызовы).
- **`WiretapLogFieldProvider`** — добавляет поля в логи приложения (`log.info(...)`, `log.error(...)` и т. д.).

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

## Проброс заголовков

По умолчанию следующие входящие заголовки запроса копируются в MDC, так что любой вызов
`log.info(...)` внутри потока запроса автоматически получает их в качестве тегов:
`x-request-id`, `x-session-key`, `lb-trace-id`.

Переопределите список, если ваша инфраструктура использует другие соглашения:

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
      enable-body-masking: true     # вызывать HttpBodyMaskingHandler для каждого поля тела
    enable-url-masking: true        # вызывать HttpUrlMaskingHandler для URL запроса
    enable-request-params-masking: true   # вызывать HttpRequestParamsMaskingHandler для query-параметров
```

Wiretap предоставляет четыре независимых SPI-интерфейса для маскирования. Регистрируйте
только те бины, которые нужны — каждый контекст независим:

| Интерфейс | Применяется к | Активация |
|---|---|---|
| `io.wiretap.applog.message.handler.MessageMaskingHandler` | поле `message` в логах приложения | бин + `wiretap.message-masking=true` (по умолчанию) |
| `io.wiretap.http.message.settings.body.HttpBodyMaskingHandler` | каждое поле в телах HTTP-запросов и ответов (рекурсивно, без знания URL) | бин + `enable-body-masking=true` |
| `io.wiretap.http.message.settings.body.HttpBodyMasker` | разобранный JSON-body как целое, per-URL (структурно — замаскировать конкретные поля на конкретных эндпоинтах) | бин + `enable-body-masking=true`; срабатывает первый masker, чей `appliesTo(url)` вернул `true` |
| `io.wiretap.http.message.HttpUrlMaskingHandler` | полный URL запроса (path + query string) | бин + `enable-url-masking=true` |
| `io.wiretap.http.message.HttpRequestParamsMaskingHandler` | каждое значение query-параметра в `request_params` | бин + `enable-request-params-masking=true` (по умолчанию) |

`HttpBodyMasker` и `HttpBodyMaskingHandler` композируются: сначала
структурный masker (если он подошёл) применяется к JSON-tree, потом
рекурсивный handler (если зарегистрирован) пробегает по результату.
Структурный — для правил «маскировать поле X на эндпоинте Y»,
рекурсивный — для глобальных паттернов строк:

```java
@Component
public class CardLimitsMasker implements HttpBodyMasker {
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

Если на classpath присутствует `spring-kafka`, Wiretap автоматически
регистрирует `ProducerInterceptor`, который снимает каждое отправленное
сообщение. Точка перехвата —
`org.apache.kafka.clients.producer.ProducerInterceptor.onSend(...)`,
вызывается **до** сериализатора, поэтому в логе видны типизированные
`key` / `value` как их сформировало приложение, а не байты на проводе:

```json
{
  "@timestamp": "...",
  "level": "INFO",
  "logger": "io.wiretap.kafka.KafkaLogSink",
  "message": "Captured outgoing kafka message orders.events",
  "kafka_info": {
    "direction": "OUTGOING",
    "topic": "orders.events",
    "partition": null,
    "client_id": "checkout-api-producer-1",
    "key": "ord-42",
    "key_length": 6,
    "value": "{\"orderId\":\"ord-42\",\"amount\":100}",
    "value_length": 33,
    "headers": { "x-trace-id": "0123456789abcdef" }
  }
}
```

Поля брокерской стороны (`partition`, `offset`, `duration`) на этапе до
сериализации ещё не известны. Вторая запись лога приходит из
`onAcknowledgement` **только при ошибке доставки** — с `status=ERROR`,
`error_class`, `error_message` и метаданными, если брокер их вернул.
Для успешной доставки достаточно записи из `onSend`.

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

Подключение работает через Spring Boot
`DefaultKafkaProducerFactoryCustomizer` — Wiretap добавляет
`interceptor.classes=io.wiretap.kafka.producer.WiretapProducerInterceptor`
в конфигурацию producer factory; вы продолжаете использовать
`KafkaTemplate` / `@KafkaListener` без дополнительной настройки.

### Консьюмер

Сторона потребителя зарегистрирована как Spring Kafka
`org.springframework.kafka.listener.RecordInterceptor` и прикреплена к
listener-контейнерам через bean-коллекцию `ContainerCustomizer`'ов в
Spring Boot. Точка перехвата —
`RecordInterceptor.intercept(record, consumer)`, вызывается **внутри**
observation-спана listener'а Spring Kafka, после того как
десериализатор уже преобразовал ключ/значение, но **до** вызова
`@KafkaListener`-метода. Отсюда два следствия:

- В логе мы видим тот же объектный вид, что и листенер.
- MDC к моменту работы wiretap'а уже содержит `traceId` / `spanId` от
  Spring Kafka observation — `kafka_info` сразу коррелирует с
  собственными логами листенера, без парсинга заголовков. Это работает
  и тогда, когда producer не пропагирует трейс — observation в таком
  случае открывает новый root-span на стороне consumer'а, и wiretap
  подбирает его автоматически.

Если listener observation выключен
(`spring.kafka.listener.observation-enabled=false`), wiretap fallback'ом
читает `b3` / `traceparent` из record-headers — propagation от
producer'а с observation не теряется.

```json
{
  "kafka_info": {
    "direction": "INCOMING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "client_id": "checkout-api-consumer-1",
    "group_id": "checkout-group",
    "key": "ord-42",
    "value": "{\"orderId\":\"ord-42\"}",
    "timestamp": "2026-05-07T10:14:32.918Z",
    "timestamp_type": "CREATE_TIME"
  }
}
```

```yaml
wiretap:
  kafka-consumer-interceptor:
    enabled: true
    visibility-settings:
      VALUE: true
    exclude-topic-patterns:
      - "__consumer_offsets"
```

Три масккинг-SPI (`KafkaValueMaskingHandler`, `KafkaHeaderMaskingHandler`,
`KafkaTopicMaskingHandler`) работают и на стороне consumer'а; один бин
покрывает обе стороны.

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

    public KafkaConfig(ObjectProvider<ContainerCustomizer<Object, Object,
            ConcurrentMessageListenerContainer<Object, Object>>> customizers) {
        this.customizers = customizers.orderedStream().toList();
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

## Трассировка

Wiretap читает `trace_id` и `span_id` из активного контекста Micrometer Tracing
(любой бэкенд — Brave, OpenTelemetry и др.). Если на classpath присутствует Brave,
библиотека по умолчанию конфигурирует 64-битный single-header B3 propagator; отключить
это поведение можно через `wiretap.tracing.propagation.type.b3.enabled=false`.

Поле `lb_trace_id` берётся из входящего заголовка запроса `lb-trace-id` и предназначено
для ID, эмитируемых балансировщиком нагрузки.

## Красивый вывод

Для локальной разработки установите `wiretap.pretty-print=true` — JSON будет
выводиться в многострочном формате. В продакшене оставляйте `false` (по умолчанию):
log-шипперы быстрее разбирают однострочный JSON.

## Совместимость

| Wiretap | Spring Boot | Java | Статус |
|---------|-------------|------|--------|
| 0.1.x | 3.2.7 (baseline) | 17, 21 | extended support |
| 0.1.x | 3.4.5 | 17, 21, 25 | активно тестируется |
| 0.1.x | 3.5.14 | 17, 21, 25 | активно тестируется, последний 3.x минор |

Boot 4.x запланирован отдельным major'ом (wiretap 2.x) — детали и
скрипты проверки в [`COMPATIBILITY.md`](./COMPATIBILITY.md).

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
