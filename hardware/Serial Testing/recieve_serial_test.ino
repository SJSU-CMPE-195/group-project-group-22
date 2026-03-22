#include <SoftwareSerial.h>

// Set up a new serial port on pins D2 (RX) and D3 (TX)
// Connect ESP32 TX to Nano D2.
// We are not sending data back, so Nano D3 can stay disconnected!
SoftwareSerial espSerial(2, 3); 

void setup() {
  // 1. Start the main hardware serial to talk to your computer's Serial Monitor
  Serial.begin(115200); 
  
  // 2. Start the SoftwareSerial port to listen to the ESP32
  // We use 9600 baud because SoftwareSerial is more stable at lower speeds
  espSerial.begin(9600); 
  
  Serial.println("Nano Receiver is ready and listening...");
}

void loop() {
  // Check if the ESP32 has sent any data to D2
  if (espSerial.available()) {
    
    // Read the incoming dataframe until it sees a new line character
    String incomingData = espSerial.readStringUntil('\n');
    
    // Print that exact data directly to your computer screen
    Serial.print("ESP32 says: ");
    Serial.println(incomingData);
  }
}