RU | [EN](README.md)

# Wiretap

> Структурированное JSON-логирование для Spring Boot приложений с перехватом HTTP-запросов и ответов
> через servlet, RestTemplate, RestClient, FeignClient и WebServiceTemplate.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Статус:** `0.1.0-SNAPSHOT` — в разработке, публичный API нестабилен. Не использовать в продакшене.

## Что вы получаете

Добавьте Wiretap в Spring Boot приложение — и каждый входящий и исходящий HTTP-вызов будет зафиксирован
как структурированная JSON-строка лога, с единообразными полями, автоматическим распространением
correlation ID и встроенным маскированием чувствительных данных.

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
    implementation 'io.wiretap:wiretap:0.1.0-SNAPSHOT'
}
```

Никакой дополнительной конфигурации не требуется. Wiretap подключается автоматически через механизм
Spring Boot auto-configuration. Логи пишутся в stdout в формате JSON. Входящий HTTP-трафик
перехватывается автоматически; исходящий — для любого `RestTemplate` / `RestClient` /
`FeignClient` / `WebServiceTemplate`, созданного через стандартные Spring-билдеры.

Чтобы также писать логи в ротируемый файл:

```yaml
wiretap:
  file-logging:
    enabled: true
    path: /var/log/myapp     # по умолчанию: /var/log/wiretap
```

## Настройка имён полей

Имена полей по умолчанию соответствуют схеме Wiretap. Любое имя можно переопределить в `application.yml`:

```yaml
wiretap:
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
| `wiretap.fields.timestamp` | `@timestamp` |
| `wiretap.fields.env` | `env` |
| `wiretap.fields.system` | `system` |
| `wiretap.fields.instance` | `inst` |
| `wiretap.fields.lb-trace-id` | `lb_trace_id` |
| `wiretap.fields.trace-id` | `trace_id` |
| `wiretap.fields.span-id` | `span_id` |
| `wiretap.fields.session-key` | `session_key` |
| `wiretap.fields.level` | `level` |
| `wiretap.fields.message` | `message` |
| `wiretap.fields.http-info` | `http_info` |
| `wiretap.fields.http.return-code` | `return_code` |
| `wiretap.fields.http.method` | `http_method` |
| `wiretap.fields.http.direction` | `direction` |
| `wiretap.fields.http.url` | `request_url` |
| `wiretap.fields.http.protocol` | `protocol` |
| `wiretap.fields.http.duration` | `duration` |
| `wiretap.fields.http.source-port` | `source_port` |
| `wiretap.fields.http.request-headers` | `request_headers` |
| `wiretap.fields.http.response-headers` | `response_headers` |
| `wiretap.fields.http.request-params` | `request_params` |
| `wiretap.fields.http.request-body` | `request_body` |
| `wiretap.fields.http.request-body-length` | `request_body_length` |
| `wiretap.fields.http.response-body` | `response_body` |
| `wiretap.fields.http.response-body-length` | `response_body_length` |
| `wiretap.fields.http.xml-body-type` | `xml_body_type` |

## Добавление собственных полей (SPI)

По умолчанию Wiretap выводит фиксированный набор полей. Чтобы дополнить каждую запись access-лога
значениями из вашей доменной модели (ID тенанта, ID киоска, тип бизнес-операции и т. д.),
реализуйте `WiretapAccessFieldProvider` как Spring-бин — Wiretap подхватит его автоматически:

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

## Проброс заголовков

По умолчанию следующие входящие заголовки запроса копируются в MDC, так что любой вызов
`log.info(...)` внутри потока запроса автоматически получает их в качестве тегов:
`x-request-id`, `x-session-key`, `lb-trace-id`. Свойство `session-key-header` также
определяет значение поля `session_key` в access-логе.

Переопределите любой из них, если ваша инфраструктура использует другие соглашения:

```yaml
wiretap:
  headers:
    forward-to-mdc:
      - x-request-id
      - x-correlation-id
      - x-trace-id
    session-key-header: x-session-key
```

## Что логируется

Wiretap перехватывает HTTP-трафик из пяти источников, каждый из которых настраивается отдельно.
У каждого источника свой префикс свойства:

| Трафик | Префикс | Управление |
|---|---|---|
| Входящий (servlet) | `wiretap.rest-controllers.*` | Всегда включён |
| Исходящий `RestTemplate` | `wiretap.rest-template-interceptor.*` | `.enabled=false` для отключения |
| Исходящий `RestClient` | `wiretap.rest-client-interceptor.*` | `.enabled=false` для отключения |
| Исходящий `FeignClient` | `wiretap.feign-client-interceptor.*` | `.enabled=false` для отключения |
| Исходящий `WebServiceTemplate` (SOAP) | `wiretap.web-service-template-interceptor.*` | `.enabled=false` для отключения |

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

### Ограничение размера тела и маскирование

```yaml
wiretap:
  rest-controllers:
    http-body-settings:
      max-body-length: 10000        # обрезать тела длиннее этого
      max-field-length: 1000        # обрезать строковые поля внутри JSON-тел
      enable-body-truncating: true
      enable-body-masking: false    # применять MaskUtil к телам (PAN, телефон, дата истечения, PIN)
    enable-url-masking: true        # маскировать PAN/телефоны в URL
```

Встроенный `MaskUtil` распознаёт:
- Номер карты PAN (с проверкой по Луну или по длине)
- Номера телефонов в российском формате (`+7...`)
- Даты истечения карты (в значениях JSON вида `"expiry": "2510"`)
- PIN-блоки (16-символьный hex)

Чтобы подключить собственную логику маскирования, зарегистрируйте бин типа
`io.wiretap.applog.message.handler.MessageMaskingHandler`. Он применяется к
неструктурированным сообщениям `log.info(...)`.

### Исключение URL из логирования

```yaml
wiretap:
  rest-controllers:
    exclude-request-patterns:
      - "/actuator/.*"
      - "/health"
```

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

| | Версия |
|---|---|
| Java | 17+ (протестировано на 17, 21, 25) |
| Spring Boot | 3.2.x и выше |
| logback-access-spring-boot-starter | 4.1.x |

## Сборка из исходников

```bash
./gradlew build
```

Тесты используют JUnit 5 + WireMock + AssertJ. На Java 25 Mockito требует флага
`-Dnet.bytebuddy.experimental=true`; он уже задан в `build.gradle`.

## Лицензия

Apache License 2.0 — см. [LICENSE](./LICENSE).
