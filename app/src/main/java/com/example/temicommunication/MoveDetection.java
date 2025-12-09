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

    // 필요한 랜드마크만 정의 (코, 어깨 위주)
    private static final int[] TARGET = new int[]{
            PoseLandmark.NOSE,                 // 0
            PoseLandmark.LEFT_SHOULDER,        // 11
            PoseLandmark.RIGHT_SHOULDER        // 12
            // 엉덩이(HIP)는 Temi 시야각 문제로 제외하거나 보조로만 사용
    };

    // 설정값
    private static final long WINDOW_MS = 500;       // 1초 -> 0.5초로 단축 (낙상은 순간적임)
    private static final float FALL_VELOCITY_CM = 80f; // 초속 80cm 이상 하강 시 의심
    private static final float FALL_ANGLE_DEG = 30f;   // 어깨 기울기가 30도 이상 틀어지면 의심
    private static final long ALERT_COOLDOWN_MS = 5000;

    private float pxPerCm = -1f;
    private long lastAlert = 0L;

    // 랜드마크별 과거 데이터 저장
    private static class Sample {
        final float x, y; final long t;
        Sample(float x, float y, long t){ this.x=x; this.y=y; this.t=t; }
    }
    private final Map<Integer, Deque<Sample>> history = new HashMap<>();

    public void setPxPerCm(float v){ this.pxPerCm = v; }
    public float getPxPerCm(){ return pxPerCm; }

    /**
     * 낙상 감지 로직
     * 조건 1: 어깨/머리의 Y축 좌표가 급격히 증가 (화면 아래로 떨어짐)
     * 조건 2: 어깨의 수평 기울기가 깨짐 (앉기와 구별)
     */
    public boolean updateAndCheck(Pose pose, long nowMs){
        if (pose == null) return false;

        // 1. 픽셀-cm 비율 갱신 (어깨 너비 기준)
        PoseLandmark L = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark R = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark N = pose.getPoseLandmark(PoseLandmark.NOSE);

        // 중요 포인트가 없으면 판단 불가
        if (L == null || R == null || N == null) return false;

        PointF pL = L.getPosition();
        PointF pR = R.getPosition();
        PointF pN = N.getPosition();

        // 어깨 너비(약 38cm)를 기준으로 비율 계산
        if (pxPerCm <= 0f){
            float shoulderPx = (float) Math.hypot(pL.x - pR.x, pL.y - pR.y);
            if (shoulderPx > 20f) pxPerCm = shoulderPx / 38f;
        }

        // 비율이 아직 계산 안됐으면 기본값 방어 (임의로 1cm = 5px 가정)
        float currentPxPerCm = (pxPerCm > 0) ? pxPerCm : 5.0f;

        // 2. 히스토리 업데이트 (각 랜드마크별)
        updateHistory(PoseLandmark.NOSE, pN, nowMs);
        updateHistory(PoseLandmark.LEFT_SHOULDER, pL, nowMs);
        updateHistory(PoseLandmark.RIGHT_SHOULDER, pR, nowMs);

        // 3. 낙상 판단 로직 시작

        // [Check 1] 하강 속도 (Velocity Y)
        // 양쪽 어깨의 Y축 변화량을 체크합니다. (X축 이동인 달리기 제외)
        float leftSpeed  = getVerticalSpeed(PoseLandmark.LEFT_SHOULDER, currentPxPerCm, nowMs);
        float rightSpeed = getVerticalSpeed(PoseLandmark.RIGHT_SHOULDER, currentPxPerCm, nowMs);
        float avgDropSpeed = (leftSpeed + rightSpeed) / 2f;

        // [Check 2] 어깨 기울기 (Posture Angle)
        // 서 있거나 앉을 때는 0도에 가깝음. 넘어지면 각도가 커짐.
        float dx = pR.x - pL.x;
        float dy = pR.y - pL.y;
        double angleRad = Math.atan2(dy, dx);
        double angleDeg = Math.abs(Math.toDegrees(angleRad)); // 0~180도

        // [최종 판단]
        // 조건: "빠르게 하강하고 있다" AND ("몸이 기울어졌다" OR "머리가 어깨보다 낮아졌다")
        // * 앉을 때는 속도는 빠를 수 있어도 angleDeg가 0에 가까워서 걸러짐.
        // * 달릴 때는 Y축 속도가 낮아서 걸러짐.
        boolean isFastDrop = avgDropSpeed > FALL_VELOCITY_CM; // 초속 80cm 이상 하강
        boolean isTilted   = angleDeg > FALL_ANGLE_DEG && angleDeg < (180 - FALL_ANGLE_DEG);

        // 디버깅용 로그 (필요시 주석 해제)
        // Log.d("FallCheck", "Speed: " + avgDropSpeed + " | Angle: " + angleDeg);

        if (isFastDrop && isTilted) {
            if (nowMs - lastAlert > ALERT_COOLDOWN_MS) {
                lastAlert = nowMs;
                return true; // 낙상 감지!
            }
        }

        return false;
    }

    // 특정 랜드마크의 데이터를 큐에 넣고 오래된 데이터 삭제
    private void updateHistory(int type, PointF p, long nowMs) {
        Deque<Sample> q = history.get(type);
        if (q == null) {
            q = new ArrayDeque<>();
            history.put(type, q);
        }
        q.addLast(new Sample(p.x, p.y, nowMs));

        // 윈도우 시간(0.5초) 지난 데이터 삭제
        while (!q.isEmpty() && nowMs - q.peekFirst().t > WINDOW_MS) {
            q.removeFirst();
        }
    }

    // 특정 랜드마크의 Y축 하강 속도 계산 (cm/sec)
    private float getVerticalSpeed(int type, float currentPxPerCm, long nowMs) {
        Deque<Sample> q = history.get(type);
        if (q == null || q.size() < 2) return 0f;

        Sample start = q.peekFirst(); // 약 0.5초 전
        Sample end   = q.peekLast();  // 현재

        // 시간 차이 (초 단위)
        float timeSec = (end.t - start.t) / 1000f;
        if (timeSec < 0.1f) return 0f; // 너무 짧으면 계산 스킵

        // Y축 변화량 (Android는 아래쪽이 +Y)
        // 떨어질 때: end.y > start.y
        float distY_px = end.y - start.y;

        // 올라가는 경우(음수)는 낙상이 아니므로 0 처리
        if (distY_px < 0) return 0f;

        float speedCm = (distY_px / currentPxPerCm) / timeSec;
        return speedCm;
    }
}