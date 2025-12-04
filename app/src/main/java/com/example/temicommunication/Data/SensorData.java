package com.example.temicommunication.Data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SensorData {
    private int CO2;
    private float Humidity;
    private int PM10;
    private int PM2_5;
    private int TVOC;
    private float Temperature;
}
