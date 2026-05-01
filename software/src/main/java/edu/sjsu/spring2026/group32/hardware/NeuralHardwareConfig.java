package edu.sjsu.spring2026.group32.hardware;

/**
 * Shared defaults for neural-hardware integration.
 */
public final class NeuralHardwareConfig {
    public static final double MIN_GAMEPLAY_VOLTAGE = 0.0;
    public static final double MAX_GAMEPLAY_VOLTAGE = 3.3;
    public static final int MIN_PONG_HARDWARE_PADDLE_SPEED = 10;
    public static final int MAX_PONG_HARDWARE_PADDLE_SPEED = 120;

    /**
     * Default voltage threshold used by hardware-backed game AIs to treat
     * an input as a firing event.
     */
    public static final double DEFAULT_FIRING_THRESHOLD_VOLTS = 0.5;

    /**
     * Constant voltage injected into the HTZ hardware while the ball is in the zone.
     */
    public static final double DEFAULT_HIT_THE_ZONE_INJECTION_VOLTAGE = 3.0;

    /**
     * Constant voltage injected into the Pong hardware to drive the left or right
     * 3-neuron channel toward the ball.  Adjust during hardware testing to tune
     * how reliably the neurons fire each game tick.
     */
    public static final double DEFAULT_PONG_LEFT_INJECTION_VOLTAGE = 3.0;
    public static final double DEFAULT_PONG_RIGHT_INJECTION_VOLTAGE = 3.0;

    /**
     * Voltage threshold at or above which a Pong ADC reading is treated as a
     * neural spike (i.e. a LEFT or RIGHT action is registered).
     * Tune independently of {@link #DEFAULT_FIRING_THRESHOLD_VOLTS} to account
     * for the Pong neuron culture's baseline and noise floor.
     */
    public static final double DEFAULT_HIT_THE_ZONE_THRESHOLD_VOLTS = DEFAULT_FIRING_THRESHOLD_VOLTS;
    public static final double DEFAULT_PONG_LEFT_THRESHOLD_VOLTS = 0.5;
    public static final double DEFAULT_PONG_RIGHT_THRESHOLD_VOLTS = 0.5;

    /**
     * Pixels the hardware-controlled paddle moves per game tick when the
     * corresponding neuron fires.  Decoupled from the software {@code PADDLE_SPEED}
     * constant in {@code PongGame} so the hardware response can be tuned
     * independently without affecting human or software-AI paddle movement.
     */
    public static final int DEFAULT_PONG_HARDWARE_PADDLE_SPEED = 70;

    private static volatile double hitTheZoneInjectionVoltage = DEFAULT_HIT_THE_ZONE_INJECTION_VOLTAGE;
    private static volatile double hitTheZoneThresholdVoltage = DEFAULT_HIT_THE_ZONE_THRESHOLD_VOLTS;
    private static volatile double pongLeftInjectionVoltage = DEFAULT_PONG_LEFT_INJECTION_VOLTAGE;
    private static volatile double pongRightInjectionVoltage = DEFAULT_PONG_RIGHT_INJECTION_VOLTAGE;
    private static volatile double pongLeftThresholdVoltage = DEFAULT_PONG_LEFT_THRESHOLD_VOLTS;
    private static volatile double pongRightThresholdVoltage = DEFAULT_PONG_RIGHT_THRESHOLD_VOLTS;
    private static volatile int pongHardwarePaddleSpeed = DEFAULT_PONG_HARDWARE_PADDLE_SPEED;

    private NeuralHardwareConfig() {
        // Utility class.
    }

    public static int getPongHardwarePaddleSpeed() {
        return pongHardwarePaddleSpeed;
    }

    public static double getHitTheZoneInjectionVoltage() {
        return hitTheZoneInjectionVoltage;
    }

    public static double getHitTheZoneThresholdVoltage() {
        return hitTheZoneThresholdVoltage;
    }

    public static double getPongLeftInjectionVoltage() {
        return pongLeftInjectionVoltage;
    }

    public static double getPongRightInjectionVoltage() {
        return pongRightInjectionVoltage;
    }

    public static double getPongLeftThresholdVoltage() {
        return pongLeftThresholdVoltage;
    }

    public static double getPongRightThresholdVoltage() {
        return pongRightThresholdVoltage;
    }

    public static void setHitTheZoneInjectionVoltage(double voltage) {
        hitTheZoneInjectionVoltage = validateGameplayVoltage(voltage, "Hit The Zone injection voltage");
    }

    public static void setHitTheZoneThresholdVoltage(double voltage) {
        hitTheZoneThresholdVoltage = validateGameplayVoltage(voltage, "Hit The Zone threshold voltage");
    }

    public static void setPongLeftInjectionVoltage(double voltage) {
        pongLeftInjectionVoltage = validateGameplayVoltage(voltage, "Pong Channel 1 injection voltage");
    }

    public static void setPongRightInjectionVoltage(double voltage) {
        pongRightInjectionVoltage = validateGameplayVoltage(voltage, "Pong Channel 2 injection voltage");
    }

    public static void setPongLeftThresholdVoltage(double voltage) {
        pongLeftThresholdVoltage = validateGameplayVoltage(voltage, "Pong Channel 1 threshold voltage");
    }

    public static void setPongRightThresholdVoltage(double voltage) {
        pongRightThresholdVoltage = validateGameplayVoltage(voltage, "Pong Channel 2 threshold voltage");
    }

    public static void setPongHardwarePaddleSpeed(int speed) {
        if (speed < MIN_PONG_HARDWARE_PADDLE_SPEED || speed > MAX_PONG_HARDWARE_PADDLE_SPEED) {
            throw new IllegalArgumentException(
                    "Pong hardware paddle speed must be between "
                            + MIN_PONG_HARDWARE_PADDLE_SPEED
                            + " and "
                            + MAX_PONG_HARDWARE_PADDLE_SPEED
                            + '.');
        }
        pongHardwarePaddleSpeed = speed;
    }

    private static double validateGameplayVoltage(double voltage, String label) {
        if (voltage < MIN_GAMEPLAY_VOLTAGE || voltage > MAX_GAMEPLAY_VOLTAGE) {
            throw new IllegalArgumentException(
                    label
                            + " must be between "
                            + MIN_GAMEPLAY_VOLTAGE
                            + " and "
                            + MAX_GAMEPLAY_VOLTAGE
                            + " V.");
        }
        return voltage;
    }
}
