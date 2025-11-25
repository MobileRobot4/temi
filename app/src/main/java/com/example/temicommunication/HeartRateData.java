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
}
