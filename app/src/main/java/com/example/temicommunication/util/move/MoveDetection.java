package com.example.temicommunication.util.move;

import android.graphics.PointF;
import android.util.Log;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class MoveDetection {

//    // 필요한 랜드마크 (코, 어깨 필수)
//    private static final int[] TARGET = new int[]{
//            PoseLandmark.NOSE,            // 0
//            PoseLandmark.LEFT_SHOULDER,   // 11
//            PoseLandmark.RIGHT_SHOULDER   // 12
//    };

    // [설정값 튜닝]
    private boolean isPersonVisible = false;
    private static final long WINDOW_MS = 500;       // 0.5초 동안의 변화 관찰
    private static final float FALL_VELOCITY_CM = 60f; // 초속 60cm 이상 하강 시 (민감도 조절)
    private static final float FALL_ANGLE_DEG = 45f;   // 45도 이상 기울어지면 위험
    private static final long ALERT_COOLDOWN_MS = 3000;

    // [새로 추가된 임계값] 앉기 vs 낙상 구분용
    private static final float COLLAPSE_RATIO_THRESHOLD = 0.3f;

    // ✅ [추가 1] 사람이 사라짐 감지용 변수
    private static final long MISSING_THRESHOLD_MS = 10000; // 10초
    private long lastValidPoseTime = System.currentTimeMillis(); // 마지막으로 사람이 보인 시간
    private boolean isMissingAlertSent = false; // 알림 중복 방지용

    private float pxPerCm = -1f;

    // 데이터 저장용
    private static class Sample {
        final float x, y; final long t;
        Sample(float x, float y, long t){ this.x=x; this.y=y; this.t=t; }
    }
    private final Map<Integer, Deque<Sample>> history = new HashMap<>();
    private long lastAlert = 0L;

    // 감지 여부를 판단할 주요 관절 7개 (0:코, 2:왼눈, 5:오눈, 9:왼입, 10:오입, 11:왼어깨, 12:오어깨)
    private static final int[] PRESENCE_CHECK_TARGETS = {
            PoseLandmark.NOSE, PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_MOUTH, PoseLandmark.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER
    }; //lee_plus

    public void setPxPerCm(float v){ this.pxPerCm = v; }

    /**
     * 메인 감지 함수 (낙상 감지)
     */
    public boolean updateAndCheck(Pose pose, long nowMs) {
        // 사람이 아예 없거나 인식이 안 되면 -> 시간 업데이트를 안 함 (lastValidPoseTime 유지)
        if (pose == null) return false;

        // ---------------------------------------------------------
        // [수정된 부분] 사람 존재 여부 판단 로직 시작 lee_plus
        // ---------------------------------------------------------
        int detectedCount = 0;
        for (int landmarkId : PRESENCE_CHECK_TARGETS) {
            PoseLandmark landmark = pose.getPoseLandmark(landmarkId);
            if (landmark != null && landmark.getInFrameLikelihood() > 0.5f) {
                detectedCount++;
            }
        }

        // 관절이 4개 이상이면 '사람 있음'
        if (detectedCount >= 4) {
            lastValidPoseTime = nowMs;
            isMissingAlertSent = false;
            isPersonVisible = true; // [추가] 사람이 보임!
        } else {
            isPersonVisible = false; // [추가] 사람이 안 보임!
        }


        // 7개의 주요 관절을 하나씩 확인해서 인식된 개수를 셉니다.
        for (int landmarkId : PRESENCE_CHECK_TARGETS) {
            PoseLandmark landmark = pose.getPoseLandmark(landmarkId);
            // 랜드마크가 있고, 신뢰도(정확도)가 50% 이상이면 인식된 것으로 칩니다.
            if (landmark != null && landmark.getInFrameLikelihood() > 0.5f) {
                detectedCount++;
            }
        }

        // [조건] "4개 이상의 점이 인식되지 않음" = "인식된 점이 3개 이하"
        // 따라서, 인식된 점이 4개 이상(detectedCount >= 4)일 때만 '사람이 있다'고 판단합니다.
        if (detectedCount >= 4) {
//            if (nowMs - lastValidPoseTime > MISSING_THRESHOLD_MS && !isMissingAlertSent) {
//                isMissingAlertSent = true; // 알림 보냄 처리 (중복 방지)
//                return true;
//            }
//            else
                lastValidPoseTime = nowMs; // 사람이 보이므로 마지막 감지 시간 갱신
                isMissingAlertSent = false; // 알림 상태 초기화
        }
        // 인식된 점이 3개 이하라면 lastValidPoseTime을 갱신하지 않으므로 시간이 흐릅니다.
        // ---------------------------------------------------------
        // [수정된 부분] 끝
        // --------------------------------------------------------- lee_plus


        // 1. 랜드마크 가져오기
        PoseLandmark nose = pose.getPoseLandmark(PoseLandmark.NOSE);
        PoseLandmark leftSh = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightSh = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        // 필수 포인트가 없으면 패스 -> 시간 업데이트 안 함
        if (nose == null || leftSh == null || rightSh == null) return false;

        PointF pN = nose.getPosition();
        PointF pL = leftSh.getPosition();
        PointF pR = rightSh.getPosition();

        // 2. 픽셀-cm 비율 갱신 (어깨 너비 38cm 가정)
        float currentShoulderWidthPx = (float) Math.hypot(pL.x - pR.x, pL.y - pR.y);
        if (pxPerCm <= 0f && currentShoulderWidthPx > 20f) {
            pxPerCm = currentShoulderWidthPx / 38f;
        }
        float currentPxPerCm = (pxPerCm > 0) ? pxPerCm : 5.0f;

        // 3. 히스토리 업데이트
        updateHistory(PoseLandmark.NOSE, pN, nowMs);
        updateHistory(PoseLandmark.LEFT_SHOULDER, pL, nowMs);
        updateHistory(PoseLandmark.RIGHT_SHOULDER, pR, nowMs);

        // ... (낙상 감지 로직은 그대로 유지) ...

        // [로직 1] 하강 속도 체크
        float noseSpeed = getDownwardVelocity(PoseLandmark.NOSE, currentPxPerCm);
        float shoulderSpeed = (getDownwardVelocity(PoseLandmark.LEFT_SHOULDER, currentPxPerCm) +
                getDownwardVelocity(PoseLandmark.RIGHT_SHOULDER, currentPxPerCm)) / 2f;
        float maxDropSpeed = Math.max(noseSpeed, shoulderSpeed);
        boolean isFastDrop = maxDropSpeed > FALL_VELOCITY_CM;

        // [로직 2] 상체 무너짐 비율
        float shoulderMidY = (pL.y + pR.y) / 2f;
        float verticalTorsoLen = shoulderMidY - pN.y;
        float torsoRatio = verticalTorsoLen / currentShoulderWidthPx;
        boolean isCollapsed = torsoRatio < COLLAPSE_RATIO_THRESHOLD;

        // [로직 3] 몸 기울기
        float dx = pR.x - pL.x;
        float dy = pR.y - pL.y;
        double angleDeg = Math.abs(Math.toDegrees(Math.atan2(dy, dx)));
        boolean isTilted = angleDeg > FALL_ANGLE_DEG && angleDeg < (180 - FALL_ANGLE_DEG);

        // [최종 판단]
        if (isFastDrop && (isCollapsed || isTilted)) {
            if (nowMs - lastAlert > ALERT_COOLDOWN_MS) {
                lastAlert = nowMs;
                Log.w("FallDetection", "FALL DETECTED! Speed:" + maxDropSpeed + " Ratio:" + torsoRatio + " Angle:" + angleDeg);
                return true;
            }
        }

        return false;
    }

    /**
     * ✅ [추가 3] 사람이 10초 이상 사라졌는지 확인하는 함수
     * MainActivity에서 매 프레임마다 호출해주어야 함.
     * @return true면 알림이 필요한 상태 (한 번만 true 반환)
     */
    public boolean checkMissingPerson(long nowMs) {
        // 마지막 인식 시간으로부터 10초(MISSING_THRESHOLD_MS)가 지났고, 아직 알림을 안 보냈다면
        if (nowMs - lastValidPoseTime > MISSING_THRESHOLD_MS && !isMissingAlertSent) {
            isMissingAlertSent = true; // 알림 보냄 처리 (중복 방지)
            return true;
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

    private float getDownwardVelocity(int type, float currentPxPerCm) {
        Deque<Sample> q = history.get(type);
        if (q == null || q.size() < 2) return 0f;

        Sample start = q.peekFirst();
        Sample end = q.peekLast();
        float timeSec = (end.t - start.t) / 1000f;
        if (timeSec < 0.1f) return 0f;

        float distY_px = end.y - start.y;
        if (distY_px <= 0) return 0f;

        return (distY_px / currentPxPerCm) / timeSec;
    }

    public boolean isPersonVisible() {
        return isPersonVisible;
    }
}