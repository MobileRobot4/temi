package com.example.temicommunication.Data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HeartRateData {
    private String CheckDate;
    private String HeartDate;
    private float HeartRate;
}
