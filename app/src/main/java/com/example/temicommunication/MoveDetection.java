package com.example.temicommunication;

import android.graphics.PointF;
import android.util.Log;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class MoveDetection {

    // 필요한 랜드마크 (코, 어깨)
    private static final int[] TARGET = new int[]{
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER
    };

    // ★ 설정값 (테스트하며 조절하세요)
    private static final long WINDOW_MS = 700;         // 0.6초간의 변화 감지
    private static final float FALL_VELOCITY_CM = 80f; // 초속 80cm 이상 빠르게 하강하면 의심
    private static final float FALL_ANGLE_DEG = 30f;   // 어깨가 30도 이상 기울어지면 의심
    private static final long ALERT_COOLDOWN_MS = 5000;

    private float pxPerCm = -1f;
    private long lastAlert = 0L;

    // 데이터 저장용 클래스
    private static class Sample {
        final float x, y; final long t;
        Sample(float x, float y, long t){ this.x=x; this.y=y; this.t=t; }
    }
    private final Map<Integer, Deque<Sample>> history = new HashMap<>();

    public void setPxPerCm(float v){ this.pxPerCm = v; }
    public float getPxPerCm(){ return pxPerCm; }

    /**
     * 최종 로직: "빠른 하강 속도" + "기울어진 자세" 동시 체크
     * try-catch로 감싸서 앱 꺼짐(크래시) 완벽 방지
     */
    public boolean updateAndCheck(Pose pose, long nowMs){
        if (pose == null) return false;

        try {
            // 1. 주요 관절(어깨, 코) 데이터 가져오기
            PoseLandmark L = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
            PoseLandmark R = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
            PoseLandmark N = pose.getPoseLandmark(PoseLandmark.NOSE);

            if (L == null || R == null || N == null) return false; // 하나라도 안 보이면 패스

            PointF pL = L.getPosition();
            PointF pR = R.getPosition();
            PointF pN = N.getPosition();

            // 2. 픽셀-cm 비율 계산 (어깨 너비 기준)
            if (pxPerCm <= 0f){
                float shoulderPx = (float) Math.hypot(pL.x - pR.x, pL.y - pR.y);
                if (shoulderPx > 20f) pxPerCm = shoulderPx / 38f;
            }
            float currentPxPerCm = (pxPerCm > 0) ? pxPerCm : 5.0f;

            // 3. 히스토리(과거 데이터) 업데이트
            updateHistorySafe(PoseLandmark.LEFT_SHOULDER, pL, nowMs);
            updateHistorySafe(PoseLandmark.RIGHT_SHOULDER, pR, nowMs);
            // (필요 시 코 데이터도 업데이트 가능)

            // =========================================================
            // ★ 원하시는 [속도 + 기울기] 로직 (안전하게 구현됨)
            // =========================================================

            // [Check 1] 하강 속도 (Velocity Y)
            // 안전한 함수(getVerticalSpeedSafe)를 사용하여 0으로 나누기 에러 방지
            float leftSpeed  = getVerticalSpeedSafe(PoseLandmark.LEFT_SHOULDER, currentPxPerCm);
            float rightSpeed = getVerticalSpeedSafe(PoseLandmark.RIGHT_SHOULDER, currentPxPerCm);
            float avgDropSpeed = (leftSpeed + rightSpeed) / 2f;

            // [Check 2] 어깨 기울기 (Posture Angle)
            float dx = pR.x - pL.x;
            float dy = pR.y - pL.y;
            // atan2는 (0,0)만 아니면 에러 안 남 (어깨 너비가 있으므로 안전)
            double angleDeg = Math.abs(Math.toDegrees(Math.atan2(dy, dx)));

            // [최종 판단]
            // 조건: "빠르게 떨어짐(속도)" AND "몸이 기울어짐(각도)"
            boolean isFastDrop = avgDropSpeed > FALL_VELOCITY_CM;
            boolean isTilted   = angleDeg > FALL_ANGLE_DEG && angleDeg < (180 - FALL_ANGLE_DEG);

            // 로그로 값 확인해보기 (Logcat에서 MoveDetection 태그 검색)
            // Log.d("MoveDetection", "Speed: " + (int)avgDropSpeed + "cm/s | Angle: " + (int)angleDeg + "deg");

            if (isFastDrop && isTilted) {
                if (nowMs - lastAlert > ALERT_COOLDOWN_MS) {
                    lastAlert = nowMs;
                    return true; // 낙상 감지!
                }
            }

        } catch (Exception e) {
            // ★ 에러가 나도 여기서 잡히므로 카메라는 꺼지지 않음!
            Log.e("MoveDetection", "Error ignored: " + e.getMessage());
            return false;
        }

        return false;
    }

    // 큐 업데이트 (안전 버전)
    private void updateHistorySafe(int type, PointF p, long nowMs) {
        if (!history.containsKey(type)) {
            history.put(type, new ArrayDeque<>());
        }
        Deque<Sample> q = history.get(type);
        if (q != null) {
            q.addLast(new Sample(p.x, p.y, nowMs));
            while (!q.isEmpty() && nowMs - q.peekFirst().t > WINDOW_MS) {
                q.removeFirst();
            }
        }
    }

    // ★ 가장 중요한 함수: 안전한 속도 계산 (이전 코드의 꺼짐 원인 해결)
    private float getVerticalSpeedSafe(int type, float currentPxPerCm) {
        Deque<Sample> q = history.get(type);
        if (q == null || q.size() < 2) return 0f;

        Sample start = q.peekFirst();
        Sample end   = q.peekLast();

        // 시간 차이 계산 (초 단위)
        float timeSec = (end.t - start.t) / 1000f;

        // ★ [핵심] 시간이 너무 짧으면(0.1초 미만) 속도 계산 시 무한대(Infinity)가 나와서 앱이 꺼짐
        // 이를 방지하기 위해 0.1초 미만이면 그냥 0을 리턴
        if (timeSec < 0.1f) return 0f;

        // Y축 변화량 (내려갈 때 양수)
        float distY_px = end.y - start.y;

        // 위로 올라가는 경우(음수)는 무시 (낙상 아님)
        if (distY_px < 0) return 0f;

        // 속도 = 거리 / 시간
        return (distY_px / currentPxPerCm) / timeSec;
    }
}