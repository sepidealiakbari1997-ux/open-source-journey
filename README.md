# Open Source Journey

A public engineering journal documenting my learning, experiments, and progress toward meaningful contributions to open-source software.

My initial focus is **OpenTelemetry Java Instrumentation**. I plan to learn observability and distributed tracing fundamentals, explore the Java agent through a small Java/Spring Boot application, and investigate focused instrumentation behavior before working toward an upstream contribution.

This repository will document:

- Technical notes and reflections on what I learn.
- Reproducible experiments with setup details, commands, and observations.
- Investigations, tests, and links to upstream issues or pull requests when available.

Upstream source changes will live in separate project forks or clones. This repository records the learning process and evidence of work as it happens.

## Current project

The local Spring Boot experiment now records incoming requests and two sequential outgoing HTTP calls in Jaeger. Echoed headers also verified outgoing trace context against a recorded client span ID.

- [Learning notes](projects/otel/learning-notes.md): operations, spans, context propagation, and instrumentation.
- [Experiments](projects/otel/experiments.md): setup, versions, the Java agent startup command, and observations.
- [Contribution status](projects/otel/contributions.md): preparation and remaining upstream work.
- [Progress](PROGRESS.md) and [roadmap](ROADMAP.md): verified milestones and the next action.
