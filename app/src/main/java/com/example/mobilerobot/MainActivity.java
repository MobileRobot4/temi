package com.example.mobilerobot;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mobilerobot.FirebaseDataFormat;
import com.example.mobilerobot.contact.ContactToGuardian;
import com.example.mobilerobot.sound.detect.PorcupineVoiceDetector;
import com.example.mobilerobot.sound.notification.EmergencyNotifier;
import com.example.temicommunication.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

import lombok.NonNull;

public class

MainActivity extends AppCompatActivity implements OnRobotReadyListener {
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private PorcupineVoiceDetector voiceDetector;
    private EmergencyNotifier emergencyNotifier;
    private ContactToGuardian contactToGuardian;
    private Robot robot;

    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    DatabaseReference myRef = firebaseDatabase.getReference();

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SwitchCompat switchCompat = (SwitchCompat) findViewById(R.id.switchLed);
        TextView distanceText = (TextView)findViewById(R.id.textDistance);
        Button buttonDistance = (Button)findViewById(R.id.buttonDistance);
        TextView distanceText2 = (TextView)findViewById(R.id.textDistance2);


        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NotNull DataSnapshot snapshot) {
                FirebaseDataFormat value = snapshot.getValue(FirebaseDataFormat.class);
                System.out.println("Success to read value : " + value.toString());
                distanceText.setText("success");
            }

            @Override
            public void onCancelled(@NotNull DatabaseError error) {
                System.out.println("Failed to read value : " + error.toException());
                distanceText.setText("fail");
            }
        });

        robot = Robot.getInstance();
        robot.addOnRobotReadyListener(this);
    }


    @Override
    public void onRobotReady(boolean ready) {
        if (!ready) {
            System.out.println("temi가 아직 준비되지 않았습니다.");
            return;
        }
        // 알림 객체 생성
        String temiId = robot.getSerialNumber();

        if (temiId == null) {
//            throw new IllegalArgumentException("temi가 준비되지 않았습니다.");
            System.out.println("temi가 준비되지 않았습니다.");
        }
        emergencyNotifier = new EmergencyNotifier(this, temiId);
        contactToGuardian = new ContactToGuardian(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO
            );
        } else {
            setupVoiceDetector();
        }
    }

    // porcupineDetector 생성
    private void setupVoiceDetector() {
        voiceDetector = new PorcupineVoiceDetector(
                this,
                new String[]{"sallyeojuseyo_ko_android_v3_0_0.ppn"}, // assets 안의 키워드 파일 이름
                "porcupine_params_ko.pv",
                () -> runOnUiThread(() -> {
                    // 여기서 "살려주세요" 인식됨
//                    Toast.makeText(
//                            MainActivity.this,
//                            "살려주세요 인식됨! Firebase로 긴급신호 전송",
//                            Toast.LENGTH_SHORT
//                    ).show();
                    System.out.println("살려주세요 인식됨");
                    emergencyNotifier.sendVoiceHelp();
                    contactToGuardian.callGuardian("강하나");
                })
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (voiceDetector != null) {
            voiceDetector.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (voiceDetector != null) {
            voiceDetector.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceDetector != null) {
            voiceDetector.release();
            voiceDetector = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupVoiceDetector();
                if (voiceDetector != null) {
                    voiceDetector.start();
                }
            } else {
                Toast.makeText(
                        this,
                        "마이크 권한이 없어 음성 인식을 사용할 수 없습니다.",
                        Toast.LENGTH_LONG
                ).show();
                System.out.println("마이크 권한이 없어 음성 인식을 사용할 수 없습니다.");
            }
        }
    }

}