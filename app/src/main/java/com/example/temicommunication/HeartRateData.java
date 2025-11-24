package com.example.temicommunication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class HeartRateData {
    private String CheckDate;
    private String HeartDate;
    private float HeartRate;
    public String getHeartDate(){
        return this.HeartDate;
    }
    public String getCheckData(){
        return this.CheckDate;
    }
    public float getHeartRate(){
        return this.HeartRate;
    }
    public void setHeartDate(String date){
        this.HeartDate = date;
    }
    public void setCheckDate(String date){
        this.CheckDate = date;
    }
    public void setHeartRate(float heartRate){
        this.HeartRate = heartRate;
    }
}
