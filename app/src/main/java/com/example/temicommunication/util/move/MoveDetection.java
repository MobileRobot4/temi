package com.example.temicommunication.util.move;

import android.graphics.PointF;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.ArrayDeque;  //lee
import java.util.Deque;      //lee
import java.util.HashMap;    //lee
import java.util.Map;        //lee

public class MoveDetection {

    // 필요한 랜드마크만 정의 (코, 어깨 위주)
    private static final int[] TARGET = new int[]{
            PoseLandmark.NOSE,                 // 0
            PoseLandmark.LEFT_EYE,             // 2
            PoseLandmark.RIGHT_EYE,            // 5
            PoseLandmark.LEFT_MOUTH,           // 9
            PoseLandmark.RIGHT_MOUTH,          // 10
            PoseLandmark.LEFT_SHOULDER,        // 11
            PoseLandmark.RIGHT_SHOULDER,       // 12
    };

    // 설정값
    private static final long WINDOW_MS = 600;       // 1초 -> 0.6초로 단축 (낙상은 순간적임)
    private static final float FALL_VELOCITY_CM = 50f; // 초속 60cm 이상 하강 시 의심
    private static final float FALL_ANGLE_DEG = 30f;   // 어깨 기울기가 30도 이상 틀어지면 의심
    private static final long ALERT_COOLDOWN_MS = 5000;
    //private static final float CM_THRESHOLD = 100f;  //lee
    private static final float MIN_SHOULDER_PX = 500f; //+
    private static final long DISAPPEAR_MS = 5000;
    private float pxPerCm = -1f;
    private int lastMovedCount = 0;                 //lee

    // 랜드마크별 과거 데이터 저장
    private static class Sample {
        final float x, y; final long t;
        Sample(float x, float y, long t){ this.x=x; this.y=y; this.t=t; }
    }

    private final Map<Integer, Deque<Sample>> history = new HashMap<>();
    private long lastAlert = 0L;

    private long lastVisibleTime = 0L; //+
    //public void setPxPerCm(float v){ this.pxPerCm = v; }     //lee
    //public float getPxPerCm(){ return pxPerCm; }             //lee
    public int getLastMovedCount(){ return lastMovedCount; } //lee


    /**
     * 낙상 감지 로직
     * 조건 1: 어깨/머리의 Y축 좌표가 급격히 증가 (화면 아래로 떨어짐)
     * 조건 2: 어깨의 수평 기울기가 깨짐 (앉기와 구별)
     * @return
     */
    public boolean updateAndCheck(Pose pose, long nowMs) {
        if (pose == null) return false;

        // 1. 픽셀-cm 비율 갱신 (어깨 너비 기준)
        PoseLandmark L = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark R = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark N = pose.getPoseLandmark(PoseLandmark.NOSE);

        // [수정] 중요 포인트가 존재하는지 확인 (Nose, Left Shoulder, Right Shoulder)
        boolean isVisible = (L != null && R != null && N != null);

        if (isVisible) {
            // [추가] 관절이 보이면 마지막 인식 시간을 현재 시간으로 갱신
            lastVisibleTime = nowMs;
        } else {
            // [추가] 관절이 안 보일 경우: 사라진 지 0.5초가 지났는지 체크
            // (lastVisibleTime > 0 조건은 앱 켜자마자 알림 울리는 것 방지)
            if (lastVisibleTime > 0 && (nowMs - lastVisibleTime > DISAPPEAR_MS)) {

                // 쿨다운 체크 (이미 알림을 보냈으면 패스)
                if (nowMs - lastAlert > ALERT_COOLDOWN_MS) {
                    lastAlert = nowMs;
                    return true; // 경고 알림 발생! (사라짐 감지)
                }
            }
        }
        // [추가된 기능] 거리 필터링 (멀리 있는 사람 무시)
        // ---------------------------------------------------------
        if (isVisible) {
            PointF pL_temp = L.getPosition();
            PointF pR_temp = R.getPosition();

            // 어깨 너비 계산 (피타고라스 정리)
            float currentShoulderPx = (float) Math.hypot(pL_temp.x - pR_temp.x, pL_temp.y - pR_temp.y);

            // 어깨가 너무 좁다(=멀리 있다)면 아예 계산하지 않고 종료
            if (currentShoulderPx < MIN_SHOULDER_PX) {
                // 주의: 멀리 있는 경우에도 '사라짐 알림'이 울리지 않게 하려면
                // lastVisibleTime을 갱신해주는 것이 좋습니다. (선택사항)
                // lastVisibleTime = nowMs;
                return false;
            }
        } //+

        // 중요 포인트가 없으면 판단 불가
        if (L == null || R == null || N == null) return false;

        PointF pL = L.getPosition();
        PointF pR = R.getPosition();
        PointF pN = N.getPosition();

        // 어깨 너비(약 38cm)를 기준으로 비율 계산
        if (pxPerCm <= 0f) {
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
        float leftSpeed = getVerticalSpeed(PoseLandmark.LEFT_SHOULDER, currentPxPerCm, nowMs);
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
        boolean isTilted = angleDeg > FALL_ANGLE_DEG && angleDeg < (180 - FALL_ANGLE_DEG);

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

        return (distY_px / currentPxPerCm) / timeSec;
    }

}