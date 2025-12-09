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

    // 엉덩이(23,24)는 Temi 화면에서 잘릴 수 있으므로, 상체(코, 어깨) 위주로 판단합니다.
    private static final int[] TARGET = new int[]{
            PoseLandmark.NOSE,            // 0
            PoseLandmark.LEFT_SHOULDER,   // 11
            PoseLandmark.RIGHT_SHOULDER   // 12
    };

    // 설정값 튜닝
    private static final long WINDOW_MS = 600;       // 관찰 시간
    private static final float FALL_VELOCITY_CM = 60f; // 속도 임계값 (조절 가능)
    private static final float FALL_ANGLE_DEG = 30f;   // 어깨 기울기 (45도 이상이면 위험)

    // ✅ 핵심 추가: 상체 무너짐 판단 비율
    // 코와 어깨 사이의 수직 거리가 어깨 너비의 20% 이하로 줄어들면 '수평(넘어짐)'으로 간주
    private static final float TORSO_COLLAPSE_RATIO = 0.2f;

    private static final long ALERT_COOLDOWN_MS = 5000;

    private float pxPerCm = -1f;

    // 데이터 저장용 클래스
    private static class Sample {
        final float x, y; final long t;
        Sample(float x, float y, long t){ this.x=x; this.y=y; this.t=t; }
    }

    private final Map<Integer, Deque<Sample>> history = new HashMap<>();
    private long lastAlert = 0L;

    public boolean updateAndCheck(Pose pose, long nowMs) {
        if (pose == null) return false;

        // 1. 주요 랜드마크 추출
        PoseLandmark nose = pose.getPoseLandmark(PoseLandmark.NOSE);
        PoseLandmark lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        // 상체가 안 보이면 판단 불가
        if (nose == null || lShoulder == null || rShoulder == null) return false;

        PointF pN = nose.getPosition();
        PointF pL = lShoulder.getPosition();
        PointF pR = rShoulder.getPosition();

        // 2. 픽셀-cm 비율 갱신 (어깨 너비 기준, 38cm 가정)
        // Temi가 움직여서 거리가 변해도, 비율 기반이므로 어느정도 보정됨
        float shoulderWidthPx = (float) Math.hypot(pL.x - pR.x, pL.y - pR.y);
        if (shoulderWidthPx > 20f) {
            pxPerCm = shoulderWidthPx / 38f;
        }
        float currentPxPerCm = (pxPerCm > 0) ? pxPerCm : 5.0f;

        // 3. 히스토리 업데이트
        updateHistory(PoseLandmark.NOSE, pN, nowMs);
        updateHistory(PoseLandmark.LEFT_SHOULDER, pL, nowMs);
        updateHistory(PoseLandmark.RIGHT_SHOULDER, pR, nowMs);

        // ---------------------------------------------------------
        // 🚀 낙상 감지 알고리즘 개선 (속도 + 기하학적 구조)
        // ---------------------------------------------------------

        // [조건 1] 하강 속도 (Y축)
        // 코와 어깨의 평균 하강 속도를 봅니다.
        float noseSpeed = getVerticalSpeed(PoseLandmark.NOSE, currentPxPerCm, nowMs);
        float shoulderSpeed = (getVerticalSpeed(PoseLandmark.LEFT_SHOULDER, currentPxPerCm, nowMs) +
                getVerticalSpeed(PoseLandmark.RIGHT_SHOULDER, currentPxPerCm, nowMs)) / 2f;

        // 코나 어깨 중 하나라도 빠르게 떨어지고 있어야 함
        boolean isFastDrop = (noseSpeed > FALL_VELOCITY_CM) || (shoulderSpeed > FALL_VELOCITY_CM);

        // [조건 2] 상체 수직성 (Sitting vs Falling 구분 핵심) ✅
        // 앉을 때는 코가 어깨보다 확실히 위에 있음 (Y값이 작음).
        // 넘어지면 코와 어깨의 Y값이 비슷해짐.
        float shoulderMidY = (pL.y + pR.y) / 2f;
        float verticalDist = shoulderMidY - pN.y; // 양수여야 정상(코가 위)

        // 수직 거리를 어깨 너비로 나눈 비율 (체격 차이 보정)
        float torsoRatio = verticalDist / shoulderWidthPx;

        // 비율이 낮으면(예: 0.2 미만) 코와 어깨 높이가 비슷함 -> 누웠거나 엎드림
        boolean isTorsoCollapsed = (torsoRatio < TORSO_COLLAPSE_RATIO);

        // [조건 3] 어깨 기울기 (좌우 균형 붕괴)
        float dy = pR.y - pL.y;
        float dx = pR.x - pL.x;
        double angleDeg = Math.abs(Math.toDegrees(Math.atan2(dy, dx)));
        boolean isTilted = angleDeg > FALL_ANGLE_DEG && angleDeg < (180 - FALL_ANGLE_DEG);

        // ---------------------------------------------------------
        // 최종 판단:
        // "빠르게 하강함" AND ("상체가 무너짐(수평)" OR "심하게 기울어짐")
        // ---------------------------------------------------------

        if (isFastDrop && (isTorsoCollapsed || isTilted)) {
            // 달리기 필터링: 달리기는 X축 이동이 많음 (여기서는 생략했으나, 필요 시 추가 가능)
            // 앉기 필터링: 앉기는 isFastDrop일 수 있어도, isTorsoCollapsed가 false임 (상체 꼿꼿)

            if (nowMs - lastAlert > ALERT_COOLDOWN_MS) {
                Log.e("FallDetection", "낙상 감지! Speed:" + noseSpeed + " Ratio:" + torsoRatio + " Angle:" + angleDeg);
                lastAlert = nowMs;
                return true;
            }
        }

        return false;
    }

    private void updateHistory(int type, PointF p, long nowMs) {
        Deque<Sample> q = history.get(type);
        if (q == null) {
            q = new ArrayDeque<>();
            history.put(type, q);
        }
        q.addLast(new Sample(p.x, p.y, nowMs));
        while (!q.isEmpty() && nowMs - q.peekFirst().t > WINDOW_MS) {
            q.removeFirst();
        }
    }

    // Y축 하강 속도 (cm/sec) - 아래로 떨어질 때만 양수 반환
    private float getVerticalSpeed(int type, float currentPxPerCm, long nowMs) {
        Deque<Sample> q = history.get(type);
        if (q == null || q.size() < 2) return 0f;

        Sample start = q.peekFirst();
        Sample end = q.peekLast();

        float timeSec = (end.t - start.t) / 1000f;
        if (timeSec < 0.2f) return 0f; // 너무 짧은 시간은 노이즈로 처리

        float distY_px = end.y - start.y; // +가 아래쪽(하강)

        // 올라가는 동작(앉았다 일어나기)은 무시
        if (distY_px <= 0) return 0f;

        return (distY_px / currentPxPerCm) / timeSec;
    }
}