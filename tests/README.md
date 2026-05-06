# Tests

All tests for this project are written in Java using **JUnit 5 + Mockito** and live inside the Maven module, following the standard Maven source layout:

```
software/src/test/java/edu/sjsu/spring2026/group32/
├── bidirectionaltest/
│   └── core/               # Unit tests — ActiveDeviceVoltageInjector
├── hardware/
│   ├── serial/             # Unit tests — SerialConnectionManager
│   └── signal/             # Unit tests — NeuralSignalParser, HardwareSignalSource
├── hitthezone/             # Integration tests — HitTheZoneGame
│   ├── ai/                 # Unit tests — HitTheZoneSoftwareAI, HitTheZoneHardwareAI
│   ├── core/               # Unit tests — HitTheZoneEngine, HitTheZonePlayerFactory, HitTheZoneHardwareFactory
│   └── ui/                 # Unit tests — HitTheZoneControlPanel, HitTheZoneHudPanel, HitTheZoneInputController
├── launcher/               # Unit tests — Launcher bootstrap
│   ├── core/               # Unit tests — LauncherHardwareMessages, LauncherProgramRegistry
│   └── ui/                 # Unit tests — ConnectionStatusPanel, SerialConnectionPanel
├── player/                 # Unit tests — HumanPlayer, HardwareAIPlayer, PlayerType
├── pong/                   # Unit tests — PongAction, PlayerVariant, PongGame
│   ├── ai/                 # Unit tests — PongSoftwareAI, PongHardwareAI
│   ├── core/               # Unit tests — PongEngine, PongPlayerFactory, PongHardwareFactory, PongScoreboardService
│   └── ui/                 # Unit tests — PongInputController, PongPauseOverlay, PongToolbar, Scoreboard
├── stress/                 # Stress tests — SerialConnectionManager, HardwareSignalSource, NeuralSignalParser, NeuralHardwareConfig
└── testsupport/            # Shared test helpers — FakeSerialDevice, FakeSerialFirmware, SerialTestRig, MockitoHardwareSupport, TestSerialLines
```

**→ [Go to test source directory](../software/src/test/java/edu/sjsu/spring2026/group32)**


## Test Coverage

JaCoCo generates an HTML report at `software/target/site/jacoco/index.html` after `mvn verify`.
The latest report is also available as the **`jacoco-coverage-report`** artifact on every CI run.

**Current results:** 267 tests — 267 passed, 0 failed. Instruction coverage is **87%** (exceeding the 80% instruction target) and branch coverage is **76%** (exceeding the 70% core functionality target).

See [`docs/evaluation/coverage-report/README.md`](../docs/evaluation/coverage-report/README.md) for the full per-package breakdown, screenshots, and coverage target details.

## Stress Test Results

Documented in [`docs/evaluation/stress-test-results.md`](../docs/evaluation/stress-test-results.md).
