package com.example.temicommunication;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;


import com.example.temicommunication.databinding.ActivityMainBinding;
import com.example.temicommunication.detect.PersonDetector;
import com.example.temicommunication.log.FirebaseLogger;
import com.example.temicommunication.thermal.Mlx90640DummyCamera;
import com.example.temicommunication.thermal.ThermalCamera;

public class MainActivity extends AppCompatActivity {

    // ViewBinding 객체
    private ActivityMainBinding binding;

    // 열화상 카메라 (지금은 더미, 나중에 진짜 MLX90640용 클래스로 교체)
    private ThermalCamera thermalCamera = new Mlx90640DummyCamera();

    // 연속으로 사람 감지됐을 때 알림이 너무 자주 뜨지 않게 쿨다운
    private long lastNotifyTime = 0L;
    private final long notifyCooldownMs = 5000L; // 5초

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // viewBinding으로 레이아웃 inflate
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupUi();
    }

    /**
     * 버튼 클릭 리스너 등 UI 세팅
     */
    private void setupUi() {
        // "열화상 사람 감지 시작" 버튼
        binding.btnStart.setOnClickListener(v -> startThermalDetection());

        // "정지" 버튼
        binding.btnStop.setOnClickListener(v -> stopThermalDetection());
    }

    /**
     * 열화상 스트리밍 시작 + 사람 판별 로직
     */
    private void startThermalDetection() {
        binding.tvStatus.setText("검출 중...");

        // ThermalCamera에서 프레임이 들어올 때마다 콜백 실행
        thermalCamera.startStreaming(frame -> {

            // 1) 사람 여부 분석
            PersonDetector.DetectionResult result =
                    PersonDetector.analyzeFrame(frame);

            // 2) UI 업데이트 (메인 스레드에서 실행)
            runOnUiThread(() -> {
                binding.tvTemp.setText("maxTemp: " + String.format("%.1f", result.maxTemp) + " °C");
                binding.tvRatio.setText("hotRatio: " + String.format("%.3f", result.hotPixelRatio));
                binding.tvIsHuman.setText(
                        result.isHuman ? "사람 감지됨" : "사람 아님(배경/사물)"
                );
            });

            // 3) Firebase에 로그 저장
            FirebaseLogger.logDetection(result);

            // 4) 일정 주기(5초)마다 사람 감지 알림 Toast
            long now = System.currentTimeMillis();
            if (result.isHuman && now - lastNotifyTime > notifyCooldownMs) {
                lastNotifyTime = now;
                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "사람을 감지했습니다.",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        });
    }

    /**
     * 열화상 스트리밍 정지
     */
    private void stopThermalDetection() {
        thermalCamera.stopStreaming();
        binding.tvStatus.setText("정지");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 화면 나갈 때도 스트리밍 정지
        thermalCamera.stopStreaming();
    }
}
