# Roadmap

This journal tracks learning and progress toward meaningful open-source contributions. The initial target is **OpenTelemetry Java Instrumentation**; the plan will adapt to findings and upstream guidance.

## Current position — 2026-11-20

Incoming HTTP and outgoing client spans have been observed in Jaeger, including two sequential sibling calls to Postman Echo. Outgoing trace context was verified through echoed headers and a matching client span ID. See [learning notes](projects/otel/learning-notes.md) and the [reproducible experiment](projects/otel/experiments.md). Receiving-side span creation in another instrumented service has not been tested. The separate order-fulfillment experiment now includes database-backed Inventory stock handling and three developer-reported passing integration tests. Order-ID idempotency and the other services remain ahead. No upstream investigation or contribution has started.

## Learning and contribution milestones

1. **Understand observability fundamentals.** Learn the roles of traces, spans, context propagation, and instrumentation. Explain how spans represent work within a single request using a small Java example.
2. **Observe a local application.** Run a small Java/Spring Boot application with the OpenTelemetry Java agent and a local trace backend. Record versions, configuration, commands, and observations so the experiment can be reproduced.
3. **Explore Java instrumentation.** Understand how a Java agent instruments application code and where Byte Buddy fits. Connect these concepts to concrete instrumentation code.
4. **Inspect the upstream project.** Clone OpenTelemetry Java Instrumentation into a separate directory. Read its contribution and build instructions, check tool requirements, and run an appropriate existing test.
5. **Investigate a focused behavior.** Explore an area such as JDBC instrumentation. Reproduce a specific behavior, inspect the relevant code, and write a focused test when appropriate.
6. **Work toward an upstream contribution.** Select a suitable issue based on evidence and maintainer guidance. Prepare a focused change, run the required checks, and seek explicit approval before opening a pull request.

Milestones 1 and 2 have an initial learning baseline and working experiment; child spans and outgoing context injection have now been exercised. Receiving-side and asynchronous propagation remain future learning topics. Milestones 3–6 remain ahead. None of this learning work is an upstream contribution. There is no fixed deadline or guaranteed issue or pull request outcome.

## Working approach

- Take one small step at a time, with explanation and review before advancing.
- Add notes and experiment documents as useful evidence becomes available.
- Keep upstream source changes in separate forks or clones.
- Record verified outcomes and the single next action in [PROGRESS.md](PROGRESS.md).

## Next action

Design a durable reservation record keyed by order ID, so repeat requests cannot deduct stock twice. See the [order fulfillment experiment](projects/otel/experiments/order-fulfillment/README.md) for the implemented stock slice and remaining design.
