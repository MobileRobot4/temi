package com.example.temicommunication.sound;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter @Setter
public class Emergency {

    private EmergencyType type;
    private long timestamp;

    public Emergency(EmergencyType type, long timestamp) {
        this.type = type;
        this.timestamp = timestamp;
    }


}
