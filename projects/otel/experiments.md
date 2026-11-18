# OpenTelemetry experiments

The current application makes two sequential calls to Postman Echo. The first section preserves the initial single-span baseline; the second records the extension.

## 2026-11-18 — Trace a local Spring Boot HTTP request

**Goal:** Run a small Java application with the OpenTelemetry Java agent and inspect a real request in Jaeger.

### Setup

Application source: [otel-demo](experiments/otel-demo/). The project was generated using Spring Initializr with Maven, Java, and Spring Web. Its package is `com.pipita.oteldemo`.

| Component | Recorded version or configuration |
|---|---|
| OS | Ubuntu; Linux `6.8.0-149-generic`, amd64 |
| JDK / compiler | OpenJDK `17.0.21.0.3` |
| Java home | `/usr/lib/jvm/java-17-openjdk-amd64` |
| System Maven | `3.6.3`; the experiment uses the wrapper instead |
| Maven Wrapper distribution | `3.10.1` |
| Spring Boot | `4.1.2` |
| Docker | `29.9.0` (reported by the developer) |
| Docker Compose | Version not recorded; `docker compose up` worked |
| Java agent | `2.33.1`, downloaded from the upstream release assets |
| Jaeger | `2.23.0`, all-in-one with in-memory storage |

VS Code's Extension Pack for Java was installed. Running and debugging a small standalone Java program were confirmed by the developer. No project-specific VS Code launch configuration was needed for this experiment.

### Run the experiment

Commands below assume a terminal at the repository root initially:

```bash
cd projects/otel/experiments/otel-demo
./mvnw -version
```

The wrapper reported Maven `3.10.1` using Java `17.0.21.0.3`.

Docker was checked with `docker run --rm hello-world`, which printed `Hello from Docker!`.

Start Jaeger in this directory:

```bash
docker compose up
```

The [Compose file](experiments/otel-demo/compose.yaml) pins the Jaeger image and exposes local ports `16686` (browser UI) and `4318` (OTLP over HTTP). Leave it running in its terminal. Open <http://localhost:16686>.

In a second terminal in the same application directory, build:

```bash
./mvnw package
```

The developer supplied `BUILD SUCCESS`. This lifecycle includes tests; individual test counts and results were not supplied, and the journal update did not rerun the build.

The application had previously started using `./mvnw spring-boot:run`, and `/hello` returned `Hello, OpenTelemetry!`. Stop that process with Ctrl+C before launching the packaged application, so port `8080` is available.

### Java command with the agent

The downloaded agent is outside this repository. This is the command used locally; change the absolute agent path if reproducing it on another machine:

```bash
java \
  -javaagent:/home/pipita/workspace/tools/development/opentelemetry/opentelemetry-javaagent.jar \
  -Dotel.service.name=otel-demo \
  -Dotel.traces.exporter=otlp \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
  -Dotel.metrics.exporter=none \
  -Dotel.logs.exporter=none \
  -jar target/otel-demo-0.0.1-SNAPSHOT.jar
```

- `-javaagent` attaches instrumentation to this JVM.
- `otel.service.name` identifies the application in Jaeger.
- The OTLP exporter sends traces over HTTP with Protocol Buffers to Jaeger on port `4318`.
- Metrics and log export are disabled for this experiment. Application logs still appear in the terminal.
- The command must run from the application directory so the relative `target/` path resolves.

### Generate and inspect requests

1. Open <http://localhost:8080/hello> and refresh a few times.
2. Allow approximately 5–10 seconds for batched export.
3. Open Jaeger, select service `otel-demo`, and click **Find Traces**.
4. Open a trace and expand its span attributes.

### Observations and evidence

Evidence consists of developer-reported command output and Jaeger screenshots shared during the session, plus the checked-in source and configuration. Screenshots are not currently stored in this repository.

- Application startup with the agent was reported successful.
- Jaeger logs identified version `2.23.0`, in-memory storage, and an OTLP HTTP receiver listening on `4318`; startup ended with `Everything is ready`.
- The Jaeger search screenshot showed **14 traces**, with one span per displayed trace.
- A selected trace showed service `otel-demo`, span `GET /hello`, duration **86.5 ms**, and total span count **1**.
- Visible attributes included `http.request.method=GET`, `http.response.status_code=200`, `http.route=/hello`, `server.address=localhost`, and `server.port=8080`.
- Span kind was not visible in the screenshots and has not been separately verified.
- No endpoint-specific agent configuration or manual span creation was required. The recorded HTTP span does not provide a breakdown of internal method time.
- No database call, outgoing service call, or cross-service context propagation has been exercised yet.

### Stop

Use Ctrl+C in the Java terminal to stop the application and Ctrl+C in the attached Compose terminal to stop Jaeger. The in-memory traces are lost when Jaeger stops. To remove the stopped Compose containers and its network, run `docker compose down` from the application directory when desired.

### References

- [Java agent setup](https://opentelemetry.io/docs/zero-code/java/agent/getting-started/)
- [Agent release 2.33.1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/tag/v2.33.1)
- [Java telemetry configuration](https://opentelemetry.io/docs/languages/java/configuration/)
- [Jaeger 2.23 getting started](https://www.jaegertracing.io/docs/2.23/getting-started/)

## 2026-11-18 — Outgoing HTTP calls and propagated context

**Goal:** Observe automatically recorded HTTP client spans, verify outgoing trace context, and test whether sequential calls become sibling spans.

### Application change and setup

[HelloController.hello()](experiments/otel-demo/src/main/java/com/pipita/oteldemo/HelloController.java) now uses Java 17's built-in `HttpClient` to send two synchronous GET requests to:

```text
https://postman-echo.com/get?message=hello
```

Postman Echo is a public test API that returns request parameters and received headers. It needs no API key for this endpoint. Internet access is required; service availability and latency can vary. See [its documentation](https://learning.postman.com/docs/reference/developer-resources/echo-api/).

The controller reuses one client, configures a 5-second connection timeout and a 10-second timeout per request, and returns the second response body. It currently lets `IOException` and `InterruptedException` propagate to Spring; explicit application error handling has not been explored.

```java
HttpResponse<String> first =
        client.send(request, HttpResponse.BodyHandlers.ofString());

HttpResponse<String> second =
        client.send(request, HttpResponse.BodyHandlers.ofString());

return second.body();
```

No manual span creation or additional telemetry dependency was added. The agent instruments the supported HTTP client operation, rather than treating every source statement as an operation to record.

### Reproduce

1. Keep Jaeger running through `docker compose up`.
2. Stop the previous Java process with Ctrl+C.
3. From `projects/otel/experiments/otel-demo`, run `./mvnw package`.
4. Restart using the **Java command with the agent** above. Running a previously built JAR does not pick up source edits automatically.
5. Open <http://localhost:8080/hello>. The response is now Postman Echo JSON, rather than the original greeting.
6. Wait for export and find the newest `otel-demo` trace in Jaeger.

### Observed progression

The developer reported a successful build and confirmed the external response. Session screenshots showed:

- With one outgoing call, a trace contained two spans: `GET /hello` and a child `GET`. The child had `server.address=postman-echo.com`; both requests showed HTTP 200.
- In a selected one-call trace, the echoed header was `00-bb11f0a1de4dd382a4ead71d8e787b65-dba19004546330ae-03`. The child span's ID in Jaeger was **`dba19004546330ae`**, matching the header. This verified outgoing context injection and receipt by the echo service.
- After adding the second call, an initial screenshot still showed only two spans. The source contained two calls; rebuilding and restarting was recommended. The subsequent screenshot showed the expected **three spans**, with **depth 2** and **one service**.

The final screenshot showed:

```text
GET /hello                  709 ms
├── First GET to Postman    402 ms; starts at 76.54 ms
└── Second GET to Postman   201 ms; starts at 479.64 ms
```

Both client spans showed HTTP 200 and `server.address=postman-echo.com`. Their placement and timing demonstrate sequential sibling operations under the same HTTP parent. The parent duration already includes the child durations. Using the rounded displayed values, the calls account for 603 ms, leaving approximately 106 ms outside the client spans but inside the server span. The trace does not identify the cause of that remaining time.

These timings are individual observations, not performance benchmarks. Both client spans belong to `otel-demo`; no Postman-internal spans were collected. The echoed header proves context receipt, not that Postman created a server span or exported it to our backend. The explicit span-kind field was not inspected.

### VS Code troubleshooting

VS Code initially reported: `HelloController.java is not on the classpath of project open-source-journey_1f880201, only syntax errors are reported`. This prevented full compiler diagnostics for the Maven project. The developer ran **Java: Import Java Projects into Workspace** and reported completion before continuing. A separate post-import type-error check was not recorded.

### Evidence limits and stopping point

Build success was reported by the developer; no build or runtime checks were rerun during this documentation update. Individual test results and trace JSON were not supplied, and screenshots remain session evidence rather than repository artifacts.

The one-call header-to-span comparison is verified. Comparing the returned header with the **second** client span in the final two-call version was proposed but not completed before stopping.

**Single next action:** For one new `/hello` request, compare the echoed `traceparent` span ID with the second HTTP client span in Jaeger.
