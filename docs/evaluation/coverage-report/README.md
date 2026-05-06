# Code Coverage Report

JaCoCo generates an HTML coverage report during every `mvn verify` run.

## Latest Results (May 2026)

### JUnit Test Results

![JUnit Test Results](junit-test-results-screenshot.png)

267 tests ran — **267 passed, 0 skipped, 0 failed**.

### JaCoCo Coverage Report

![JaCoCo Coverage Report](jacoco-coverage-report-screenshot.png)

| Counter | Result |
|---|---|
| Instruction coverage | **87%** (745 of 6,140 missed) |
| Branch coverage | **76%** (123 of 531 missed) |
| Line coverage | **88%** (153 of 1,306 missed) |
| Method coverage | **89%** (32 of 286 missed) |
| Class coverage | **98%** (1 of 53 missed) |

#### Per-Package Breakdown

| Package | Instruction Cov. | Branch Cov. |
|---|---|---|
| `pong.ui` | 83% | 70% |
| `hardware.serial` | 84% | 61% |
| `pong.ai` | 79% | 77% |
| `hitthezone.ai` | 84% | 95% |
| `pong.core` | 95% | 83% |
| `hitthezone.core` | 94% | 79% |
| `hardware.signal` | 91% | 87% |
| `player` | 93% | 83% |
| `hitthezone.ui` | 99% | 91% |
| `bidirectionaltest.core` | 98% | 85% |
| `hardware` | 100% | 100% |
| `launcher.core` | 100% | 100% |
| `pong.model` | 100% | n/a |

## Viewing the Report

### Locally

```bash
cd software
mvn verify
open target/site/jacoco/index.html          # macOS
xdg-open target/site/jacoco/index.html      # Linux
start target/site/jacoco/index.html         # Windows
```

### From GitHub Actions (CI)

1. Go to the [CI workflow runs](https://github.com/SJSU-CMPE-195/group-project-group-22/actions/workflows/ci.yml).
2. Open the most recent successful run.
3. Scroll to **Artifacts** and download `jacoco-coverage-report`.
4. Unzip and open `index.html` in a browser.

## Coverage Exclusions

The following classes are excluded from coverage metrics (see `pom.xml`):

| Category | Classes excluded |
|---|---|
| Swing JFrame/JPanel shells | `Launcher`, `HitTheZoneGame`, `PongGame`, `PongFrame`, all of `launcher/ui/*`, `BidirectionalTest`, all of `bidirectionaltest/ui/*`, `HitTheZoneHudPanel`, `HitTheZoneTrackPanel`, `HitTheZoneControlPanel` |
| Hardware adapter | `RealSerialDevice` |
| Enums / models | `PlayerType`, `LauncherProgram`, `PongAction`, `PongGameState`, `PongSnapshot`, `PongState`, `PongTickResult`, `HitTheZoneAction`, `HitTheZoneActionEffect`, `HitTheZoneSnapshot`, `HitTheZoneState` |
| Coverage annotation | `GeneratedExcludeFromCoverage` |

These are excluded because they consist entirely of auto-generated, hardware-dependent, Swing-layout-only, or data-only value types that cannot be meaningfully exercised at unit level.

## Coverage Targets

| Counter | Target | Current | Enforced minimum |
|---|--------|---|---|
| Instruction coverage | 80%+ | ✅ 87% | 80% (enforced via `jacoco-check` in `pom.xml`) |
| Branch coverage (core functionality) | 70%+ | ✅ 76% | none |

> **Note on `pong.ai`:** Instruction coverage for `pong.ai` is 79%, just below the 80% instruction target. The remaining gap is in AI edge-case branches that require hardware simulation to exercise fully.


