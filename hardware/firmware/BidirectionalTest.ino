// ============================================================
//  Test.ino — ESP32 Bidirectional Serial Firmware
//  Reads analog pin 34, streams CSV to Java, and accepts
//  voltage-injection commands from Java over USB Serial.
//
//  Upward stream  (ESP32 → Java, USB Serial @ 115200):
//    Normal data : "<millis>,1,0,<rawADC>"
//    Status msgs : "STATUS,<event>[,<detail>]"
//
//  Downward stream (Java → ESP32, USB Serial):
//    INJECT:<raw>       — inject a raw ADC value (0–4095)
//    INJECT_V:<volts>   — inject by voltage  (0.00–3.30 V)
//    STOP_INJECT        — revert to real ADC readings
//    STATUS             — request a one-line status reply
//
//  Secondary stream (ESP32 → Nano over Serial2, GPIO 16/17):
//    "<rawADC,voltage>"  — same human-readable format as before
// ============================================================

// ── Pin assignments ─────────────────────────────────────────
const int LED_PIN    = 2;
const int ANALOG_PIN = 34;

// ── Injection state ──────────────────────────────────────────
bool injecting    = false;
int  injectedRaw  = 0;

// ── Serial2 (to Nano) ────────────────────────────────────────
const int SERIAL2_RX = 16;
const int SERIAL2_TX = 17;

// ── Timing ───────────────────────────────────────────────────
const unsigned long LOOP_DELAY_MS = 10;

// ── Command buffer ───────────────────────────────────────────
String cmdBuffer = "";

// ============================================================
void setup() {
  pinMode(LED_PIN, OUTPUT);

  // USB serial — communicates with Java
  Serial.begin(115200);
  Serial.println("STATUS,BOOT,ESP32 Test Program Ready");

  // Serial2 — communicates with downstream Nano
  Serial2.begin(9600, SERIAL_8N1, SERIAL2_RX, SERIAL2_TX);
}

// ============================================================
void loop() {
  // 1. Process any commands that have arrived from Java
  handleIncomingCommands();

  // 2. Choose the signal source
  int rawValue = injecting ? injectedRaw : analogRead(ANALOG_PIN);

  // 3. Convert to voltage
  float voltage = (rawValue / 4095.0f) * 3.3f;

  // 4. Drive the on-board LED (threshold: 1.0 V)
  digitalWrite(LED_PIN, (voltage > 1.0f) ? HIGH : LOW);

  // 5. Send 4-field CSV upstream to Java
  //    Format kept identical to Read_neuron_with_serial.ino so that
  //    NeuralSignalParser continues to work unchanged.
  Serial.println(String(millis()) + ",1,0," + String(rawValue));

  // 6. Send human-readable payload to Nano over Serial2
  String payload = "<" + String(rawValue) + "," + String(voltage, 2) + ">";
  Serial2.println(payload);

  delay(LOOP_DELAY_MS);
}

// ============================================================
//  handleIncomingCommands
//  Non-blocking character accumulation so that reading partial
//  USB lines never stalls the main loop.
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
      // Guard against buffer overflow from malformed input
      if (cmdBuffer.length() > 64) {
        cmdBuffer = "";
      }
    }
  }
}

// ============================================================
//  processCommand  — parse and execute a single command string
// ============================================================
void processCommand(const String& cmd) {

  // ── INJECT:<rawADC>  (integer 0–4095) ───────────────────
  if (cmd.startsWith("INJECT:")) {
    int raw = constrain(cmd.substring(7).toInt(), 0, 4095);
    injectedRaw = raw;
    injecting   = true;
    float v = (raw / 4095.0f) * 3.3f;
    Serial.println("STATUS,INJECT_START," + String(raw) + "," + String(v, 3) + "V");

  // ── INJECT_V:<volts>  (float 0.00–3.30) ─────────────────
  } else if (cmd.startsWith("INJECT_V:")) {
    float v = constrain(cmd.substring(9).toFloat(), 0.0f, 3.3f);
    int raw = (int)((v / 3.3f) * 4095.0f);
    injectedRaw = raw;
    injecting   = true;
    Serial.println("STATUS,INJECT_START," + String(raw) + "," + String(v, 3) + "V");

  // ── STOP_INJECT ─────────────────────────────────────────
  } else if (cmd == "STOP_INJECT") {
    injecting = false;
    Serial.println("STATUS,INJECT_STOPPED");

  // ── STATUS ───────────────────────────────────────────────
  } else if (cmd == "STATUS") {
    String mode = injecting ? "INJECTING" : "NORMAL";
    float v = (injectedRaw / 4095.0f) * 3.3f;
    Serial.println("STATUS,OK," + mode + "," + String(injectedRaw) + "," + String(v, 3) + "V");

  // ── Unknown command ──────────────────────────────────────
  } else {
    Serial.println("STATUS,UNKNOWN_CMD," + cmd);
  }
}
