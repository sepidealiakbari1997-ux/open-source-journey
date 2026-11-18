# OpenTelemetry learning notes

## 2026-11-13 — Traces and spans

A trace records the work involved in handling a request. A span represents one operation within that work, with a start time, end time, and information about the operation.

Consider a Spring endpoint that loads an order:

```java
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable long id) {
    return orderRepository.findById(id).orElseThrow();
}
```

With suitable HTTP and database instrumentation, the request could produce an HTTP server span containing a database query span. Instrumentation determines which operations are captured; every Java method does not automatically become a span.

### Relationships and timing

In a simplified example with two sequential database queries directly under the HTTP span:

```text
HTTP server span: 200 ms
├── First database query span: 60 ms
└── Second database query span: 90 ms
```

- Both query spans have the HTTP span as their parent. They are siblings; execution order alone does not establish a parent–child relationship.
- The query durations are included within the HTTP span's duration, rather than added to it.
- Assuming the queries do not overlap, they account for 150 ms. The remaining 50 ms is outside those database spans but inside the HTTP span.
- That remaining time could include application logic, serialization, deserialization, or other work. These spans alone cannot identify its cause.
- Span durations measure elapsed time and can include waiting, not just CPU work.

These are conceptual examples, not observations from a running application. Actual span structure and timing will depend on the application and its instrumentation.

## 2026-11-18 — Operations and instrumentation

An operation is a piece of work, such as handling an HTTP request, querying a database, or calculating a discount. A span records an operation; the operation can execute without any span being created for it.

Automatic instrumentation creates spans around supported framework and library operations. It does not automatically record every Java method. Manual instrumentation can create spans for business operations that need more visibility.

The Java agent is attached with the JVM's `-javaagent` option. It instruments supported code as it is loaded. We did not configure `/hello` individually: incoming HTTP request instrumentation recorded the request, and framework instrumentation supplied the matched route. Byte Buddy and the underlying implementation have not yet been investigated.

### Server and client spans

- A server span records receiving and handling an incoming request.
- An HTTP client span records making an outgoing HTTP request, including waiting for the result.
- One application can be a server for an incoming request and a client for an outgoing call during that request.

For example, with suitable instrumentation and propagation:

```text
Orders HTTP server span A
└── Orders HTTP client span D
    └── Payments HTTP server span E
        ├── Database query span F
        └── Database update span G
```

Payments' operations F and G are direct children of E and descendants of D. A new span normally takes the current span as its parent. A nested recorded operation can therefore introduce another level in the hierarchy. An internal Java service method adds no level unless instrumentation creates a span for it.

## 2026-11-18 — Context propagation and export

Context propagation carries the information needed to connect operations to the same trace and establish their parent relationships. Trace context includes a trace ID, a span ID, and trace flags; it does not contain all the recorded span data.

Within one thread, instrumentation makes a span current while its operation executes. Instrumentation starting a nested span can read that context. If query B finishes and HTTP span A becomes current again, a subsequent query C is a sibling of B, with parent A.

Across HTTP service boundaries, instrumentation injects context into request headers such as W3C `traceparent`. The receiving service extracts it before creating its server span:

- The receiving span uses the incoming trace ID.
- It gets its own span ID.
- Its parent is the outgoing HTTP client span whose context was sent.

Without incoming trace context, the receiving service normally starts a new trace. Exporting both services' spans to the same backend alone does not connect them.

**Propagation and exporting serve different purposes:**

```text
Orders ── HTTP request + trace context ──→ Payments
   │                                         │
   └── exports its own spans ──┐             │
                               ▼             │
                        Tracing backend ←────┘ exports its own spans
```

Each application exports its spans separately. The backend displays their relationships using the recorded identifiers; Orders does not need Payments' spans in the HTTP response. Payments must itself be instrumented and export spans somewhere accessible to us. Sending context to a third-party provider does not grant access to its internal telemetry.

Cross-thread and asynchronous propagation have not yet been explored. The service hierarchy above is conceptual; our running experiment currently has only one application.

## 2026-11-18 — Jaeger and the first observed span

Jaeger receives, stores, and displays traces. It is a tracing backend with a storage layer, rather than a general-purpose database. Production deployments can use persistent storage and scaled components. Our local all-in-one configuration uses memory, so its traces disappear when Jaeger stops.

Our first observed trace contains one `GET /hello` span with HTTP status `200`. One selected request took `86.5 ms`; that measures the recorded request-handling interval, not just execution of `HelloController.hello()`. There is no separate controller-method span in the observed trace, and the single span cannot identify which part of request handling consumed the time. Span kind was expected to be `SERVER`, but was not visible in the supplied screenshots.

See [the experiment record](experiments.md) for versions, commands, and observations.

## 2026-11-18 — HTTP client instrumentation in practice

The agent automatically recorded Java `HttpClient.send(...)` calls as outgoing HTTP client spans. It instruments supported library behavior; the assignment to a response variable and the `BodyHandlers.ofString()` expression do not each become spans.

Two sequential sends produced sibling spans:

```text
GET /hello — server span A
├── First outgoing GET — client span B
└── Second outgoing GET — client span C
```

After the first synchronous call, instrumentation restores the previous current context. The second call therefore starts under A rather than B. Execution order alone does not determine span parenthood.

### Read the propagated header

Postman Echo returned this received header during the one-call experiment:

```text
00-bb11f0a1de4dd382a4ead71d8e787b65-dba19004546330ae-03
```

The hyphen-separated fields are version, trace ID, parent ID, and trace flags. Here, the trace ID is `bb11f0a1de4dd382a4ead71d8e787b65`, and the parent ID is `dba19004546330ae`. That parent ID matched our outgoing client span's SpanID in Jaeger. It is called the parent ID because a receiving server would use it as its parent; it is not the ID of our `/hello` server span.

The flags value `03` has the sampled bit set. We have not investigated sampling behavior further.

All spans in a trace share its trace ID but have distinct span IDs. In Jaeger, expanding a span exposes its SpanID at the bottom-right; the trace title shows a shortened trace ID, and the trace page URL contains the full ID. Compare identifiers from the same request: refreshing `/hello` normally creates another trace with new IDs.

The echo response demonstrates that the header reached the external service. It does not expose that service's internal spans. Jaeger still showed one service, `otel-demo`, because both recorded spans came from our application.

With two calls, the controller returns `second.body()`, so the echoed context should correspond to the second client span. That final comparison remains to be checked.

**Single next action:** For one new `/hello` request, compare the echoed `traceparent` span ID with the second HTTP client span in Jaeger.

