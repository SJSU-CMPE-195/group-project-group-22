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
    
    // Convert the PWM value back to a voltage just so we can print/plot it nicely
    float voltage = (i / 255.0) * 5.0; 
    Serial.println(voltage);
    
    delay(10); // Slows down the wave so you can see it
  }

  // Go DOWN: Step from 168 back down to 0
  for(int i = 168; i >= 0; i--) {
    analogWrite(pwmPin, i);
    
    float voltage = (i / 255.0) * 5.0;
    Serial.println(voltage);
    
    delay(10);
  }
}