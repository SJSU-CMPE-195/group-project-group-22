# Stress Test Results

## Overview

Throughput benchmarks for the three core non-UI components of the Hardware SNN project.
Tests are located in `software/src/test/java/.../stress/StressTest.java` and tagged `@Tag("stress")` so they can be run independently of the full suite.

## How to Run

```bash
# From the project root
cd software
mvn test -Dgroups=stress
```

Expected console output (one line per benchmark):

```
[StressTest] HitTheZone AI              : 260,559,159 decisions/sec  (elapsed 3.8 ms)
[StressTest] HitTheZone AI (jitter=5)   : 219,992,960 decisions/sec  (elapsed 4.5 ms)
[StressTest] Pong AI (hard)             :  66,913,801 decisions/sec  (elapsed 14.9 ms)
[StressTest] NeuralSignalParser         :  14,766,272 parses/sec      (elapsed 67.7 ms)
[StressTest] NeuralSignalParser (malformed) : 1,916,962 parses/sec   (elapsed 521.7 ms)
```

---

## Results

> **Environment:** Java 17 (Temurin) · 1,000,000 iterations per benchmark · JIT warmup of 10,000 iterations applied before timing

| Benchmark | Iterations | Throughput (ops/s) | Elapsed (ms) | Minimum threshold | Pass? |
|---|---|---|---|---|---|
| HitTheZone AI (jitter=0) | 1,000,000 | 260,559,159 | 3.8 | 100,000 | ✅ |
| HitTheZone AI (jitter=5) | 1,000,000 | 219,992,960 | 4.5 | 100,000 | ✅ |
| Pong AI (hard preset) | 1,000,000 | 66,913,801 | 14.9 | 100,000 | ✅ |
| NeuralSignalParser (valid input) | 1,000,000 | 14,766,272 | 67.7 | 100,000 | ✅ |
| NeuralSignalParser (malformed input) | 1,000,000 | 1,916,962 | 521.7 | 100,000 | ✅ |

---

## Analysis

### HitTheZone AI

The `HitTheZoneSoftwareAI` decision loop is a pure Java state machine with no allocations in the hot path.
All state objects (`HitTheZoneState`) are pre-allocated and reused across iterations, so GC pressure is negligible.
Measured throughput of **260 M decisions/s** (jitter=0) and **220 M decisions/s** (jitter=5) confirms the JIT fully optimises this path.
The ~15% gap between presets is attributable to the extra `Random.nextInt()` call when jitter is active.

### Pong AI

`PongSoftwareAI.getNextMove()` performs one `Random.nextDouble()` call plus two integer comparisons per tick.
Measured throughput of **67 M decisions/s** is lower than the HitTheZone AI primarily because the hard preset sets `reactionProbability=1.0`, meaning the RNG is exercised on every call without the cheap early-exit path.
The benchmark pre-allocates all 1 M `PongState` records before timing begins, isolating AI logic from object-creation cost.

### NeuralSignalParser

`parseVoltage()` performs a `String.split()`, `Integer.parseInt()`, and one EMA multiply-add per call.
Measured throughput of **14.8 M parses/s** vastly exceeds the ESP32 serial output rate of ~100–1000 Hz, providing a **>14,000× safety margin** over real hardware requirements.

### Malformed Input

The malformed-input path measured **1.9 M parses/s** — approximately **8× slower** than the valid-input path.
This slowdown is caused by JVM exception creation overhead: `NumberFormatException` is thrown and caught on nearly every iteration.
Despite the slowdown, throughput still exceeds the 100 k ops/s minimum by **19×**, and more importantly still exceeds the real-time serial rate by **>1,900×**.
No performance cliff or hang was observed; the parser degrades gracefully.

---

## Conclusion

All five benchmarks pass the minimum threshold of 100,000 ops/s by a wide margin.
The game loop runs at 60 fps — each component is called at most 60 times per second in production.
The worst-case measured throughput (1.9 M ops/s, malformed parser input) provides a **>31,000× safety margin** over real-time requirements, confirming that none of these components will ever be a performance bottleneck.
