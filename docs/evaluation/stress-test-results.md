# Stress Test Results

See also: [`tests/README.md`](../../tests/README.md) · [`docs/evaluation/coverage-report/README.md`](coverage-report/README.md)

### Test Configuration
- Tool: JUnit 5 stress suite with fake firmware/device support in `software/src/test/java/edu/sjsu/spring2026/group32/testsupport`
- Total Suite Runtime: 900 ms
- Virtual Users: Not applicable
- Target: Java hardware integration path
  - `SerialConnectionManager`
  - `HardwareSignalSource`
  - `NeuralSignalParser`
  - `NeuralHardwareConfig`
- Stress Scenarios:
  - Sustained mixed firmware traffic with valid samples, malformed lines, `STATUS`, `INFO?`, and injection commands
  - Random disconnect and reconnect recovery cycles
  - Signal burst, spike detection, and recovery after disconnect
  - Boundary and invalid-value sweeps for hardware configuration

### Results
| Metric | Value |
|--------|-------|
| Mixed Traffic Iterations | 750 |
| Disconnect/Reconnect Cycles | 10 |
| Signal Recovery Cycles | 12 |
| Passed Scenarios | 4 |
| Failed Scenarios | 0 |
| Total Stress Suite Runtime | 900 ms |

> The total suite runtime (900 ms) exceeds the sum of individual scenario runtimes (881 ms) by ~19 ms. This is normal JUnit framework overhead — test class initialization, JVM warmup between test classes, and test runner bookkeeping.

### Scenario Results
| Scenario | Result | Runtime | Notes |
|---|---|---|---|
| `serialConnectionManager_sustainedMixedFirmwareTraffic` | PASS | 31 ms | samples=750, malformed injected under mixed command traffic |
| `serialConnectionManager_randomDisconnectReconnectCycles` | PASS | 363 ms | cycles=10, seeded random burst traffic and full state reset checks |
| `neuralHardwareConfig_boundaryAndFailureSweep` | PASS | 1 ms | 25 boundary update rounds plus invalid voltage and speed rejection checks |
| `hardwareSignalSource_burstAndRecoveryStress` | PASS | 486 ms | cycles=12, repeated spike plateaus with reconnect recovery |

### Observations
- The stress suite is designed around firmware-style load rather than HTTP request load, so traditional response-time metrics are replaced by recovery, stability, and throughput-of-processing checks inside the Java runtime.
- The highest-value paths under stress are `hardware.serial` and `hardware.signal`, because those packages must tolerate malformed data, command churn, and connection instability without corrupting game state.
- The new suite specifically checks that disconnects clear `latestSampleFrame`, the next-line buffer (`getNextLine()`), device metadata, and signal state before reconnecting and resuming normal processing.
- The `hardware` package is also exercised through repeated boundary and invalid-value sweeps in `NeuralHardwareConfig`, which helps close a known low-coverage area.
- This measured run completed with 4 passing scenarios and 0 failures, so the simulated firmware path handled the current stress workload without instability.
- `hardwareSignalSource_burstAndRecoveryStress` was the slowest scenario at 486 ms, which makes sense because it includes asynchronous voltage propagation, spike detection, disconnect resets, and reconnect recovery in one loop.
- `serialConnectionManager_randomDisconnectReconnectCycles` was next at 363 ms, reflecting the cost of repeatedly tearing down and rebuilding serial state.
- `serialConnectionManager_sustainedMixedFirmwareTraffic` completed in 31 ms, which suggests the Java-side sample parsing and listener dispatch path is comfortably handling the current fake-firmware load volume.
- If failures appear, the first optimization targets should be:
  - serial read/recovery stability in `SerialConnectionManager`
  - listener state reset behavior in `HardwareSignalSource`
  - flaky wait timing in stress and lifecycle tests
  - any malformed-input parsing paths that create unnecessary exception overhead
