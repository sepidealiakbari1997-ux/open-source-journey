# OpenTelemetry experiments

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

**Single next action:** Add one recorded child operation and inspect its parent relationship and timing under the HTTP span.
