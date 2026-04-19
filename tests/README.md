# Tests

All tests for this project are written in Java using **JUnit 5 + Mockito** and live inside the Maven module, following the standard Maven source layout:

```
software/src/test/java/edu/sjsu/spring2026/group32/
├── hardware/               # Unit tests — NeuralSignalParser, SerialConnectionManager, HardwareSignalSource
├── launcher/               # Unit tests — Launcher UI bootstrap
├── player/                 # Unit tests — HumanPlayer, HardwareAIPlayer, PlayerType
├── pong/                   # Unit tests — PongSoftwareAI, PongHardwareAI, PongGame, PongState, UI components
│   └── ui/
├── sandbox/                # Unit + integration tests — HitTheZone AI, PoC game loop
└── stress/                 # Throughput / stress tests — AI decisions/sec, parser parses/sec
```

**→ [Go to test source directory](../software/src/test/java/edu/sjsu/spring2026/group32)**

## Running the Tests

```bash
# All tests (unit + integration) with JaCoCo coverage report
cd software
mvn verify

# Stress / throughput benchmarks only (no Swing required)
mvn test -Dgroups=stress

# A single test class
mvn test -Dtest=NeuralSignalParserTest
```

> Tests that instantiate Swing components (e.g. `PoCTest`, `PongGameTest`) require a virtual display on Linux.
> Prefix the command with `xvfb-run --auto-servernum` or run them via CI, which sets up Xvfb automatically.

## Test Coverage

JaCoCo generates an HTML report at `software/target/site/jacoco/index.html` after `mvn verify`.
The latest report is also available as the **`jacoco-coverage-report`** artifact on every CI run.

See [`docs/evaluation/coverage-report/README.md`](../docs/evaluation/coverage-report/README.md) for details on exclusions and coverage targets.

## Stress Test Results

Documented in [`docs/evaluation/stress-test-results.md`](../docs/evaluation/stress-test-results.md).
