// ============================================================
//  NeuralSerial.ino — ESP32 Multi-Channel Neural Serial Firmware
//
//  Supports two hardware configurations selected at compile time
//  via CHANNEL_COUNT:
//
//  Config 1  (CHANNEL_COUNT 1) — 3-neuron / HitTheZone
//  ┌─────────────────────────────────────────────────────────┐
//  │ Micro USB  Java ↔ ESP32 @ 115200                       │
//  │ GPIO34 (ADC6)   Neuron output   → ADC read             │
//  │ GPIO25 (DAC1)   Neuron input    ← DAC drive (0–3.3 V)  │
//  │ GPIO2           LED threshold indicator (optional)      │
//  └─────────────────────────────────────────────────────────┘
//
//  Config 2  (CHANNEL_COUNT 2) — 6-neuron / Pong
//  ┌─────────────────────────────────────────────────────────┐
//  │ Micro USB  Java ↔ ESP32 @ 115200                       │
//  │ GPIO34 (ADC6)   Neuron 1 output → ADC read  (LEFT)     │
//  │ GPIO35 (ADC7)   Neuron 2 output → ADC read  (RIGHT)    │
//  │ GPIO25 (DAC1)   Neuron 1 input  ← DAC drive (LEFT)     │
//  │ GPIO26 (DAC2)   Neuron 2 input  ← DAC drive (RIGHT)    │
//  │ GPIO2           LED indicator (optional)                │
//  └─────────────────────────────────────────────────────────┘
//
// ── Upward stream  (ESP32 → Java, USB Serial @ 115200) ───────
//  Single channel:  "<millis>,1,0,<rawADC>"
//  Dual channel:    "<millis>,1,0,<rawADC1>,<rawADC2>"
//  Status messages: "STATUS,<event>[,<detail>]"
//
// ── Downward stream (Java → ESP32, USB Serial) ───────────────
//  INJECT_V:<v>         inject voltage on Ch 1 DAC (0.00–3.30 V)
//  INJECT_V_CH1:<v>     same as above (explicit)
//  INJECT_V_CH2:<v>     inject voltage on Ch 2 DAC (dual only)
//  INJECT:<raw>         inject raw DAC value on Ch 1 (0–4095)
//  INJECT_CH1:<raw>     same as above (explicit)
//  INJECT_CH2:<raw>     inject raw DAC value on Ch 2 (dual only)
//  STOP_INJECT          stop all injection, revert to real ADC
//  STOP_INJECT_CH1      stop Ch 1 injection only
//  STOP_INJECT_CH2      stop Ch 2 injection only (dual only)
//  STATUS               request a one-line status reply
// ============================================================

// ── Configuration ─────────────────────────────────────────────
//  Set CHANNEL_COUNT to 1 for 3-neuron HitTheZone config.
//  Set CHANNEL_COUNT to 2 for 6-neuron Pong config.
#define CHANNEL_COUNT 2

// ── Pin assignments ───────────────────────────────────────────
const int LED_PIN     = 2;

// ADC input pins (both are input-only on ESP32)
const int ADC_PIN_CH1 = 34;   // GPIO34 / ADC6 — always used
const int ADC_PIN_CH2 = 35;   // GPIO35 / ADC7 — dual only

// DAC output pins
const int DAC_PIN_CH1 = 25;   // GPIO25 / DAC1 — always used
const int DAC_PIN_CH2 = 26;   // GPIO26 / DAC2 — dual only

// ── Thresholds ────────────────────────────────────────────────
const int   LED_THRESHOLD_RAW = 1241; // ≈ 1.0 V  (1.0/3.3 * 4095)
const float V_MAX              = 3.3f;
const int   ADC_MAX            = 4095;
const int   DAC_MAX            = 255;  // ESP32 DAC is 8-bit

// ── Timing ────────────────────────────────────────────────────
const unsigned long LOOP_DELAY_MS = 10;

// ── Injection state (per channel) ────────────────────────────
bool injecting[2]   = {false, false};
int  injectedRaw[2] = {0, 0};

// ── Command buffer ────────────────────────────────────────────
String cmdBuffer = "";

// ── Helpers ───────────────────────────────────────────────────

/** Convert a raw ADC value (0–4095) to an 8-bit DAC value (0–255). */
inline int adcToDac(int rawAdc) {
    return constrain(rawAdc / 16, 0, DAC_MAX);
}

/** Convert a voltage (0.00–3.30 V) to an 8-bit DAC value. */
inline int voltsToDac(float v) {
    return constrain((int)((v / V_MAX) * DAC_MAX), 0, DAC_MAX);
}

/** Convert a voltage (0.00–3.30 V) to a 12-bit raw ADC equivalent. */
inline int voltsToRaw(float v) {
    return constrain((int)((v / V_MAX) * ADC_MAX), 0, ADC_MAX);
}

// ============================================================
void setup() {
    pinMode(LED_PIN, OUTPUT);

    // USB serial — communicates with Java
    Serial.begin(115200);
    Serial.println("STATUS,BOOT,NeuralSerial Ready,CH=" + String(CHANNEL_COUNT));
    Serial.println("#INFO:NeuralSignal,CH=" + String(CHANNEL_COUNT));
}

// ============================================================
void loop() {
    // 1. Process any commands from Java
    handleIncomingCommands();

    // 2. Read each active channel — always sample real hardware so that
    //    the serial stream and LED reflect the neuron's actual response,
    //    even while injection is driving the DAC input.
    int rawAdc[2];
    rawAdc[0] = analogRead(ADC_PIN_CH1);

#if CHANNEL_COUNT >= 2
    rawAdc[1] = analogRead(ADC_PIN_CH2);
#endif

    // 3. Drive DAC outputs (injection → DAC, else 0 V)
    dacWrite(DAC_PIN_CH1, injecting[0] ? adcToDac(injectedRaw[0]) : 0);

#if CHANNEL_COUNT >= 2
    dacWrite(DAC_PIN_CH2, injecting[1] ? adcToDac(injectedRaw[1]) : 0);
#endif

    // 4. LED: on when any active channel exceeds threshold
    bool ledOn = (rawAdc[0] > LED_THRESHOLD_RAW);
#if CHANNEL_COUNT >= 2
    ledOn = ledOn || (rawAdc[1] > LED_THRESHOLD_RAW);
#endif
    digitalWrite(LED_PIN, ledOn ? HIGH : LOW);

    // 5. Stream CSV to Java
    //    Single:  "<millis>,1,0,<raw1>"
    //    Dual:    "<millis>,1,0,<raw1>,<raw2>"
    String csv = String(millis()) + ",1,0," + String(rawAdc[0]);
#if CHANNEL_COUNT >= 2
    csv += "," + String(rawAdc[1]);
#endif
    Serial.println(csv);

    delay(LOOP_DELAY_MS);
}

// ============================================================
//  handleIncomingCommands
//  Non-blocking character accumulation — never stalls the loop.
// ============================================================
void handleIncomingCommands() {
    while (Serial.available() > 0) {
        char c = (char)Serial.read();
        if (c == '\n' || c == '\r') {
            if (cmdBuffer.length() > 0) {
                processCommand(cmdBuffer);
                cmdBuffer = "";
            }
        } else {
            cmdBuffer += c;
            if (cmdBuffer.length() > 64) cmdBuffer = "";  // overflow guard
        }
    }
}

// ============================================================
//  processCommand — parse and execute a single command string
// ============================================================
void processCommand(const String& cmd) {

    // ── INJECT_V:<volts>  or  INJECT_V_CH1:<volts> ──────────
    if (cmd.startsWith("INJECT_V_CH1:") || cmd.startsWith("INJECT_V:")) {
        int colon = cmd.indexOf(':');
        float v = constrain(cmd.substring(colon + 1).toFloat(), 0.0f, V_MAX);
        injectedRaw[0] = voltsToRaw(v);
        injecting[0]   = true;
        dacWrite(DAC_PIN_CH1, voltsToDac(v));
        Serial.println("STATUS,INJECT_START,CH1," +
                       String(injectedRaw[0]) + "," + String(v, 3) + "V");

    // ── INJECT_V_CH2:<volts>  (dual only) ───────────────────
    } else if (cmd.startsWith("INJECT_V_CH2:")) {
#if CHANNEL_COUNT >= 2
        float v = constrain(cmd.substring(13).toFloat(), 0.0f, V_MAX);
        injectedRaw[1] = voltsToRaw(v);
        injecting[1]   = true;
        dacWrite(DAC_PIN_CH2, voltsToDac(v));
        Serial.println("STATUS,INJECT_START,CH2," +
                       String(injectedRaw[1]) + "," + String(v, 3) + "V");
#else
        Serial.println("STATUS,ERR,CH2_NOT_AVAILABLE");
#endif

    // ── INJECT:<raw>  or  INJECT_CH1:<raw> ─────────────────
    } else if (cmd.startsWith("INJECT_CH1:") || cmd.startsWith("INJECT:")) {
        int colon = cmd.indexOf(':');
        int raw = constrain(cmd.substring(colon + 1).toInt(), 0, ADC_MAX);
        injectedRaw[0] = raw;
        injecting[0]   = true;
        dacWrite(DAC_PIN_CH1, adcToDac(raw));
        float v = (raw / (float)ADC_MAX) * V_MAX;
        Serial.println("STATUS,INJECT_START,CH1," + String(raw) + "," + String(v, 3) + "V");

    // ── INJECT_CH2:<raw>  (dual only) ───────────────────────
    } else if (cmd.startsWith("INJECT_CH2:")) {
#if CHANNEL_COUNT >= 2
        int raw = constrain(cmd.substring(11).toInt(), 0, ADC_MAX);
        injectedRaw[1] = raw;
        injecting[1]   = true;
        dacWrite(DAC_PIN_CH2, adcToDac(raw));
        float v = (raw / (float)ADC_MAX) * V_MAX;
        Serial.println("STATUS,INJECT_START,CH2," + String(raw) + "," + String(v, 3) + "V");
#else
        Serial.println("STATUS,ERR,CH2_NOT_AVAILABLE");
#endif

    // ── STOP_INJECT  (all channels) ─────────────────────────
    } else if (cmd == "STOP_INJECT") {
        injecting[0] = false;
        injecting[1] = false;
        dacWrite(DAC_PIN_CH1, 0);
#if CHANNEL_COUNT >= 2
        dacWrite(DAC_PIN_CH2, 0);
#endif
        Serial.println("STATUS,INJECT_STOPPED,ALL");

    // ── STOP_INJECT_CH1 ─────────────────────────────────────
    } else if (cmd == "STOP_INJECT_CH1") {
        injecting[0] = false;
        dacWrite(DAC_PIN_CH1, 0);
        Serial.println("STATUS,INJECT_STOPPED,CH1");

    // ── STOP_INJECT_CH2  (dual only) ────────────────────────
    } else if (cmd == "STOP_INJECT_CH2") {
#if CHANNEL_COUNT >= 2
        injecting[1] = false;
        dacWrite(DAC_PIN_CH2, 0);
        Serial.println("STATUS,INJECT_STOPPED,CH2");
#else
        Serial.println("STATUS,ERR,CH2_NOT_AVAILABLE");
#endif

    // ── STATUS ───────────────────────────────────────────────
    } else if (cmd == "STATUS") {
        String mode0 = injecting[0] ? "INJECTING" : "NORMAL";
        float  v0    = (injectedRaw[0] / (float)ADC_MAX) * V_MAX;
        String reply = "STATUS,OK,CH=" + String(CHANNEL_COUNT) +
                       ",CH1=" + mode0 +
                       "," + String(injectedRaw[0]) +
                       "," + String(v0, 3) + "V";
#if CHANNEL_COUNT >= 2
        String mode1 = injecting[1] ? "INJECTING" : "NORMAL";
        float  v1    = (injectedRaw[1] / (float)ADC_MAX) * V_MAX;
        reply += ",CH2=" + mode1 +
                 "," + String(injectedRaw[1]) +
                 "," + String(v1, 3) + "V";
#endif
        Serial.println(reply);

    // ── INFO? : respond with device capabilities ─────────────────
    } else if (cmd == "INFO?") {
        Serial.println("#INFO:NeuralSignal,CH=" + String(CHANNEL_COUNT));

    // ── Unknown command ──────────────────────────────────────
    } else {
        Serial.println("STATUS,UNKNOWN_CMD," + cmd);
    }
}
