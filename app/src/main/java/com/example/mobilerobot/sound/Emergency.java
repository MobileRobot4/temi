package com.example.mobilerobot.sound;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class Emergency {

    private EmergencyType type;
    private long timestamp;

}
