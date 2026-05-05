int pwmPin = 9; // Make sure to use a PWM pin (marked with a ~ on the board)

void setup() {
  Serial.begin(115200);
  pinMode(pwmPin, OUTPUT);
}

void loop() 
{
  // Go UP: Step from 0 to 168 (which is approx 3.3V on a 5V board)
  for(int i = 0; i <= 168; i++) {
    analogWrite(pwmPin, i);
    
    // 4-field CSV matching NeuralSignalParser format: millis,1,0,rawPWM
    // NeuralSignalParser reads parts[3] as raw ADC; using PWM value i here.
    Serial.println(String(millis()) + ",1,0," + String(i));
    
    delay(10); // Slows down the wave so you can see it
  }

  // Go DOWN: Step from 168 back down to 0
  for(int i = 168; i >= 0; i--) {
    analogWrite(pwmPin, i);
    
    // 4-field CSV matching NeuralSignalParser format: millis,1,0,rawPWM
    Serial.println(String(millis()) + ",1,0," + String(i));
    
    delay(10);
  }
}