# Roadmap

This journal tracks learning and progress toward meaningful open-source contributions. The initial target is **OpenTelemetry Java Instrumentation**; the plan will adapt to findings and upstream guidance.

## Learning and contribution milestones

1. **Understand observability fundamentals.** Learn the roles of traces, spans, context propagation, and instrumentation. Explain how spans represent work within a single request using a small Java example.
2. **Observe a local application.** Run a small Java/Spring Boot application with the OpenTelemetry Java agent and a local trace backend. Record versions, configuration, commands, and observations so the experiment can be reproduced.
3. **Explore Java instrumentation.** Understand how a Java agent instruments application code and where Byte Buddy fits. Connect these concepts to concrete instrumentation code.
4. **Inspect the upstream project.** Clone OpenTelemetry Java Instrumentation into a separate directory. Read its contribution and build instructions, check tool requirements, and run an appropriate existing test.
5. **Investigate a focused behavior.** Explore an area such as JDBC instrumentation. Reproduce a specific behavior, inspect the relevant code, and write a focused test when appropriate.
6. **Work toward an upstream contribution.** Select a suitable issue based on evidence and maintainer guidance. Prepare a focused change, run the required checks, and seek explicit approval before opening a pull request.

These milestones describe intended work, not completed contributions. There is no fixed deadline or guaranteed issue or pull request outcome.

## Working approach

- Take one small step at a time, with explanation and review before advancing.
- Add notes and experiment documents as useful evidence becomes available.
- Keep upstream source changes in separate forks or clones.
- Record verified outcomes and the single next action in [PROGRESS.md](PROGRESS.md).

## Next action

Explain a trace and its spans through a small Java request-flow example, then check understanding before setting up an application.
