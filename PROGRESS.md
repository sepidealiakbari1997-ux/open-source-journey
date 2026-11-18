# Progress

## Current stage

Observed incoming HTTP and outgoing HTTP client spans in Jaeger. The application now makes two sequential calls to Postman Echo, producing sibling child spans. An echoed `traceparent` was matched to the outgoing client span in the one-call version. Receiving-side span creation in another instrumented service has not been exercised. No upstream investigation or contribution has been recorded.

## 2026-11-18 — Outgoing HTTP spans and context propagation

**Goal:** Observe a real child span, verify propagated context, and test sequential span relationships.

**Work completed:** Changed `HelloController.hello()` to call Postman Echo using Java `HttpClient`, first once and then twice sequentially. The endpoint returns the second response body. Inspected the external host attribute, matched an echoed header to a client span, and observed two sibling spans. Imported the Maven project using **Java: Import Java Projects into Workspace** after VS Code reported that the controller was outside the classpath.

**Verification:** The developer reported `./mvnw package` succeeded and confirmed the updated endpoint response. A one-call trace screenshot showed client SpanID `dba19004546330ae`, matching the supplied `traceparent` header. The final screenshot showed three spans: the 709 ms parent and sequential 402 ms and 201 ms client spans, both HTTP 200 and targeting `postman-echo.com`. An earlier two-span result prompted a rebuild/restart check; the subsequent result showed three. No builds or runtime checks were rerun during the documentation update, and individual test results were not supplied.

**Learning:** The agent instruments supported HTTP client operations, not arbitrary Java statements. Sequential calls use the restored parent context and become siblings. Parent elapsed time includes child time. Echoing trace context verifies header receipt but provides no external server spans.

**Open questions:** The two-call response should carry the second client's span ID; this comparison remains unverified. Explicit span kind, cross-thread propagation, receiving-side context extraction, manual spans, and Byte Buddy internals remain unexplored. No upstream issue has been selected.

**Single next action:** For one new `/hello` request, compare the echoed `traceparent` span ID with the second HTTP client span in Jaeger.

## 2026-11-18 — First local trace and context propagation fundamentals

**Goal:** Understand how operations connect in a trace and observe a real instrumented HTTP request.

**Work completed:**

- Discussed operations versus spans, current context and parent relationships, server/client spans, propagation through HTTP headers, and propagation versus exporting. These cross-service examples remain conceptual.
- Installed VS Code Java extensions and confirmed Run and Debug using a small Java program, as reported by the developer.
- Created a Spring Boot 4.1.2 application at `projects/otel/experiments/otel-demo`, using Java 17 and Maven Wrapper 3.10.1.
- Added `/hello`, started Jaeger 2.23.0 through Compose, and launched the packaged application with Java agent 2.33.1.
- Viewed real traces in Jaeger. Moved project documentation into `projects/otel` and recorded the reusable startup command in [experiments](projects/otel/experiments.md).

**Verification:** Developer-provided output confirmed `javac` 17.0.21.0.3, wrapper Maven 3.10.1 using Java 17, successful Docker `hello-world`, `./mvnw package` reporting `BUILD SUCCESS`, and Jaeger readiness. The developer confirmed `/hello` returned the expected response. Shared screenshots showed 14 traces and a selected single-span `GET /hello` trace lasting 86.5 ms with status 200. Individual build test results were not supplied. These runtime checks were performed by the developer, not rerun during documentation work.

**Learning:** Instrumentation records supported operations without configuring each endpoint. Trace context connects spans; each application exports its own data. A single HTTP span provides overall timing without identifying internal bottlenecks. Jaeger is a tracing backend; this local instance loses its in-memory traces on stop.

**Open questions:** How should a custom operation be recorded as a child span? Cross-thread and cross-service propagation, Byte Buddy internals, and upstream build conventions remain unexplored. Span kind has not yet been verified in Jaeger. No upstream issue has been selected.

**Single next action:** Add one recorded child operation and inspect its parent relationship and timing under the HTTP span.

## 2026-11-13 — Understand traces and spans

**Goal:** Understand how spans describe work within a request and what their durations can tell us.

**Work completed:** Discussed a small Spring endpoint and a conceptual HTTP trace with two sequential database query spans. Recorded the explanation in [learning notes](projects/otel/learning-notes.md).

**Learning:** Identified the HTTP span as the parent of both query spans and recognized that unaccounted request time cannot be attributed to a specific operation without more evidence. Reviewed the timing calculation: 200 − 60 − 90 = 50 ms, assuming no overlap.

**Verification:** Checked understanding through discussion. No application was run and no tests were executed; the timing values are illustrative.

**Open question:** How is trace context carried between operations, especially across threads or service boundaries?

**Single next action:** Explain context propagation with a small request-flow example and check understanding.

## 2026-11-13 — Establish the journal

**Goal:** Give the repository a clear public purpose and establish a learning plan and progress record.

**Work completed:**

- Read `AGENTS.md` and inspected the existing repository files.
- Expanded `README.md` to explain the journal's purpose, initial OpenTelemetry Java Instrumentation focus, and planned documentation.
- Added `ROADMAP.md` with provisional learning and contribution milestones.
- Created this progress record, distinguishing completed preparation from planned technical work.

**Verification:**

- Used `rg --files` and file reads to inspect repository contents.
- Ran `git diff --check` after the README update; it passed.
- No application build or tests were run; this work consists of documentation only.

**Learning:** The journal's scope and workflow are established: record reproducible evidence here and keep upstream source changes in separate forks or clones. Technical learning has not yet been documented.

**Open questions:** The sample application's setup, required tool versions, and local trace backend have not been selected or verified. No upstream issue has been selected.

**Single next action:** Explain a trace and its spans through a small Java request-flow example, then check understanding before setting up an application.
