package com.example.mobilerobot;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class FirebaseDataFormat {

    //열화상카메라 체온
    private float temperature;
    //공기질측정모듈 데이터
    private int co2eq;
    private int tvoc;
    private int pm1_0;
    private int pm2_5;
    private int pm10;

}
