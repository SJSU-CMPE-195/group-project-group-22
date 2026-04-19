# Code Coverage Report

JaCoCo generates an HTML coverage report during every `mvn verify` run.

## Viewing the Report

### Locally

```bash
cd software
mvn verify
open target/site/jacoco/index.html   # macOS
xdg-open target/site/jacoco/index.html  # Linux
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
| Swing UI | `Launcher`, `SerialConnectionPanel`, `PongToolbar`, `Scoreboard`, `PongGame`, `BidirectionalTest` |
| Enums | `PlayerType`, `PongAction`, `PlayerVariant`, `HitTheZoneAction` |
| Records / DTOs | `PongState`, `HitTheZoneState` |
| Hardware-coupled | `HardwareSignalSource` |

These are excluded because their bytecode consists entirely of auto-generated or hardware-dependent code that cannot be meaningfully tested at unit level.

## Coverage Targets

| Counter | Target | Status |
|---|--------|---|
| Instruction coverage | 80 %+  | See latest CI run |

The JaCoCo `check` goal enforces this threshold during `mvn verify`.
Build fails if coverage drops below the minimum.
