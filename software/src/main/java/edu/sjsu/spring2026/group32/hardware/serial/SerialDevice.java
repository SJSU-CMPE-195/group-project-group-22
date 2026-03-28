package edu.sjsu.spring2026.group32.hardware.serial;

import java.io.InputStream;
import java.io.OutputStream;

public interface SerialDevice {
    String getDescriptivePortName();
    String getSystemPortName();
    void setBaudRate(int baudRate);
    void setComPortTimeouts(int timeoutMode, int readTimeout, int writeTimeout);
    boolean openPort();
    boolean isOpen();
    void closePort();
    InputStream getInputStream();
    OutputStream getOutputStream();
}