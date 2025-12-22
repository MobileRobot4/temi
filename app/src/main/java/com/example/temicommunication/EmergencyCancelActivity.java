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

public class EmergencyCancelActivity extends AppCompatActivity {

    Button buttonEmergencyCancel;
    TextView textViewSecond;
    long emergencyStartTime;

    private Handler handler = new Handler();
    private static final int INTERVAL_MS = 1000;
    private static final long MAX_WAIT_SECONDS = 15;
    private static EmergencyCancelActivity instance;

    public static EmergencyCancelActivity getInstance(){
        return instance;
    }
    public static boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_cancel);
        instance = this;
        Intent receivedIntent = getIntent();
        emergencyStartTime = receivedIntent.getLongExtra("startTime",0L);
        if (emergencyStartTime > 0L) {
            startTimer();
        } else {
            Log.e("EmergencyCancel", "startTime Intent 데이터가 유효하지 않습니다. (0L)");
            // 유효하지 않으면 즉시 종료합니다.
            setResult(RESULT_CANCELED);
            finish();
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
            long now = System.currentTimeMillis();
            long elapsedMillis = now - emergencyStartTime;
            long seconds = elapsedMillis / 1000;
            textViewSecond.setText("[" + seconds + "초/" + MAX_WAIT_SECONDS + "초]");
            if(seconds >= MAX_WAIT_SECONDS + 1){
                // 7초까지 표시 후 8초가 되는 순간 종료
                stopTimer();
                setResult(4); // 응급 상황 발생 코드로 설정
                finish();
            } else {
                handler.postDelayed(this, INTERVAL_MS);
            }
        }
    };

    // 💡 액티비티가 화면에서 사라질 때 타이머 중지
    @Override
    protected void onStop() {
        super.onStop();
        isRunning = false;
        stopTimer();
    }

    // 💡 액티비티가 완전히 종료될 때 타이머 중지
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        stopTimer();
        if(instance == this) {
            instance = null;
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        isRunning = true;
    }

    @Override
    protected void onPause(){
        super.onPause();
        isRunning = false;
    }


    private void startTimer() {
        // 즉시 실행 및 1초 간격으로 반복 시작
        handler.post(updateTimeRunnable);
    }

    private void stopTimer() {
        // 예약된 모든 콜백을 제거하여 반복을 중지합니다.
        handler.removeCallbacks(updateTimeRunnable);
    }

}
