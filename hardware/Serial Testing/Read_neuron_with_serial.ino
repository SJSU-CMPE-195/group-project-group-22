const int ledPin = 2; 
const int analogPin = 34;

void setup() {
  pinMode(ledPin, OUTPUT);
  
  // Start the serial monitor for text output
  Serial.begin(115200);
  Serial.println("ESP32 Test Program Started");
  Serial2.begin(9600, SERIAL_8N1, 16, 17);         //serial2 sends via tx2 and rx2 pins
}

void loop() 
{
  int rawValue = analogRead(analogPin);

  float voltage = (rawValue / 4095.0)*3.3;

  if(voltage > 1.0)
  {
    digitalWrite(ledPin, HIGH);
  }
  else
    digitalWrite(ledPin, LOW);
 // Serial.printf("raw adc value: %d \n", rawValue);
 // Serial.printf("calculated voltage value: %f\n", voltage);
 // delay(1000);

  // Send 4-field CSV over USB Serial so Java NeuralSignalParser can read it.
  // Format: millis,1,0,rawADC  (parser reads parts[3] as the raw ADC integer)
  Serial.println(String(millis()) + ",1,0," + String(rawValue));

  // Still send the human-readable payload over Serial2 for the Nano receiver.
  String payload = "<" + String(rawValue) + "," + String(voltage, 2) + ">";
  Serial2.println(payload);   //payload goes out over tx2 gpio17
  delay(10);
}