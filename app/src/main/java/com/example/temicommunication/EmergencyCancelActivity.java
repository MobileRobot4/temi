package com.example.temicommunication;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@SuppressLint("NewApi")
public class EmergencyCancelActivity extends AppCompatActivity {

    Button buttonEmergencyCancel;
    TextView textViewSecond;
    LocalDateTime emergencyStartTime;

    private Handler handler = new Handler();
    private static final int INTERVAL_MS = 1000;
    private static EmergencyCancelActivity instance;

    public static EmergencyCancelActivity getInstance(){
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_cancel);
        instance = this;
        Intent receivedIntent = getIntent();
        emergencyStartTime = (LocalDateTime)receivedIntent.getSerializableExtra("startTime");
        Serializable serializableTime = receivedIntent.getSerializableExtra("startTime");
        if (serializableTime instanceof LocalDateTime) {
            emergencyStartTime = (LocalDateTime) serializableTime;
        } else {
            Log.e("EmergencyCancel", "startTime Intent 데이터가 유효하지 않습니다.");
            // 데이터가 유효하지 않으면 타이머를 시작할 수 없습니다.
        }
        if (emergencyStartTime != null) {
            startTimer();
        }
        buttonEmergencyCancel = findViewById(R.id.buttonEmergencyCancel);
        textViewSecond = findViewById(R.id.textViewSecond);
        buttonEmergencyCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopTimer();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            LocalDateTime checkTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
            if (emergencyStartTime != null) {
                Duration duration = Duration.between(emergencyStartTime, checkTime);
                long seconds = duration.getSeconds();
                textViewSecond.setText("[" + seconds + "초/7초]");
                Log.d("응급상황","[" + seconds + "초/7초]");
                if(seconds == 8){
                    setResult(4);
                    finish();
                } else {
                    handler.postDelayed(this, INTERVAL_MS);
                }
            }
        }
    };

    // 💡 액티비티가 화면에서 사라질 때 타이머 중지
    @Override
    protected void onStop() {
        super.onStop();
        stopTimer();
    }

    // 💡 액티비티가 완전히 종료될 때 타이머 중지
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        if(instance == this) {
            instance = null;
        }
    }


    private void startTimer() {
        // 즉시 실행 및 1초 간격으로 반복 시작
        handler.post(updateTimeRunnable);
        Log.d("Timer", "타이머 시작");
    }

    private void stopTimer() {
        // 예약된 모든 콜백을 제거하여 반복을 중지합니다.
        handler.removeCallbacks(updateTimeRunnable);
        Log.d("Timer", "타이머 중지");
    }

}
