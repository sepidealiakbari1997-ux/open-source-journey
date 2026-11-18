# Roadmap

This journal tracks learning and progress toward meaningful open-source contributions. The initial target is **OpenTelemetry Java Instrumentation**; the plan will adapt to findings and upstream guidance.

## Current position — 2026-11-18

Core tracing concepts have been discussed, and the first local HTTP trace has been observed in Jaeger. See [learning notes](projects/otel/learning-notes.md) and the [reproducible experiment](projects/otel/experiments.md). Cross-service propagation is understood conceptually but has not been tested. No upstream investigation or contribution has started.

## Learning and contribution milestones

1. **Understand observability fundamentals.** Learn the roles of traces, spans, context propagation, and instrumentation. Explain how spans represent work within a single request using a small Java example.
2. **Observe a local application.** Run a small Java/Spring Boot application with the OpenTelemetry Java agent and a local trace backend. Record versions, configuration, commands, and observations so the experiment can be reproduced.
3. **Explore Java instrumentation.** Understand how a Java agent instruments application code and where Byte Buddy fits. Connect these concepts to concrete instrumentation code.
4. **Inspect the upstream project.** Clone OpenTelemetry Java Instrumentation into a separate directory. Read its contribution and build instructions, check tool requirements, and run an appropriate existing test.
5. **Investigate a focused behavior.** Explore an area such as JDBC instrumentation. Reproduce a specific behavior, inspect the relevant code, and write a focused test when appropriate.
6. **Work toward an upstream contribution.** Select a suitable issue based on evidence and maintainer guidance. Prepare a focused change, run the required checks, and seek explicit approval before opening a pull request.

Milestones 1 and 2 have an initial learning baseline and working experiment; deepen them through child spans and practical propagation. Milestones 3–6 remain ahead. None of this learning work is an upstream contribution. There is no fixed deadline or guaranteed issue or pull request outcome.

## Working approach

- Take one small step at a time, with explanation and review before advancing.
- Add notes and experiment documents as useful evidence becomes available.
- Keep upstream source changes in separate forks or clones.
- Record verified outcomes and the single next action in [PROGRESS.md](PROGRESS.md).

## Next action

Add one recorded child operation to the local application and inspect its parent relationship and timing under the HTTP span in Jaeger.
