
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
    private static final float FALL_VELOCITY_CM = 50f; // 초속 50cm 이상 하강 시 의심
    private static final float FALL_ANGLE_DEG = 30f;   // 어깨 기울기가 30도 이상 틀어지면 의심
    private static final long ALERT_COOLDOWN_MS = 5000;
    //private static final float CM_THRESHOLD = 100f;  //lee
    private static final float MIN_SHOULDER_PX = 200f; //+
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
        // pose 자체가 없으면: 사람 없음(사라짐 감지는 lastVisibleTime 기반)
        if (pose == null) {
            return checkDisappear(nowMs);
        }

        PoseLandmark L = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark R = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark N = pose.getPoseLandmark(PoseLandmark.NOSE);

        // 핵심 랜드마크가 없으면: 사람 없음
        if (L == null || R == null || N == null) {
            return checkDisappear(nowMs);
        }

        PointF pL = L.getPosition();
        PointF pR = R.getPosition();
        PointF pN = N.getPosition();

        float shoulderPx = (float) Math.hypot(pL.x - pR.x, pL.y - pR.y);

        // 신뢰도 필터(튜닝 포인트)
        final float MIN_LIK = 0.6f;
        float lLik = L.getInFrameLikelihood();
        float rLik = R.getInFrameLikelihood();
        float nLik = N.getInFrameLikelihood();

        boolean nearAndReliable =
                (shoulderPx >= MIN_SHOULDER_PX) &&
                        (lLik >= MIN_LIK) && (rLik >= MIN_LIK) && (nLik >= MIN_LIK);

        // ★ 멀리 있음/신뢰도 낮음 = "사람 없음"으로 강제 처리
        if (!nearAndReliable) {
            lastVisibleTime = 0L;     // 멀리있는 사람 때문에 사라짐 알림 울리는 것도 막고 싶다면 유지
            history.clear();          // (권장) 이전 히스토리 제거
            pxPerCm = -1f;            // (권장) 거리비율도 재계산하도록 리셋
            return false;
        }

        // 가까운 사람으로 확정된 경우에만 갱신
        lastVisibleTime = nowMs;

        // pxPerCm 계산(어깨너비 38cm 가정)
        if (pxPerCm <= 0f) {
            if (shoulderPx > 20f) pxPerCm = shoulderPx / 38f;
        }
        float currentPxPerCm = (pxPerCm > 0) ? pxPerCm : 5.0f;

        // 히스토리 업데이트
        updateHistory(PoseLandmark.NOSE, pN, nowMs);
        updateHistory(PoseLandmark.LEFT_SHOULDER, pL, nowMs);
        updateHistory(PoseLandmark.RIGHT_SHOULDER, pR, nowMs);

        // 속도 계산
        float leftSpeed  = getVerticalSpeed(PoseLandmark.LEFT_SHOULDER, currentPxPerCm, nowMs);
        float rightSpeed = getVerticalSpeed(PoseLandmark.RIGHT_SHOULDER, currentPxPerCm, nowMs);
        float avgDropSpeed = (leftSpeed + rightSpeed) / 2f;

        // 어깨 기울기
        float dx = pR.x - pL.x;
        float dy = pR.y - pL.y;
        double angleDeg = Math.abs(Math.toDegrees(Math.atan2(dy, dx)));

        boolean isFastDrop = avgDropSpeed > FALL_VELOCITY_CM;   // FALL_VELOCITY_CM 주석도 50에 맞게 수정 추천
        boolean isTilted   = angleDeg > FALL_ANGLE_DEG && angleDeg < (180 - FALL_ANGLE_DEG);

        if (isFastDrop && isTilted) {
            if (nowMs - lastAlert > ALERT_COOLDOWN_MS) {
                lastAlert = nowMs;
                return true;
            }
        }
        return false;
    }

    // "사람 없음" 상태에서만 사라짐 알림 판단
    private boolean checkDisappear(long nowMs) {
        if (lastVisibleTime > 0 && (nowMs - lastVisibleTime > DISAPPEAR_MS)) {
            if (nowMs - lastAlert > ALERT_COOLDOWN_MS) {
                lastAlert = nowMs;
                return true;
            }
        }
        return false;
    }

//    /** 1초 내 60cm 이상 이동한 포인트가 3개 이상이면 true */ //lee
//    public boolean updateAndCheck(Pose pose, long nowMs){
//        if (pose == null) return false;
//
//        // 어깨폭≈38cm로 px→cm 간이 보정
//        if (pxPerCm <= 0f){
//            PoseLandmark L = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
//            PoseLandmark R = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
//            if (L!=null && R!=null){
//                PointF a = L.getPosition();
//                PointF b = R.getPosition();
//                float shoulderPx = (float) Math.hypot(a.x-b.x, a.y-b.y);
//                if (shoulderPx > 10f) pxPerCm = shoulderPx / 38f; //lee
//            }
//        }
//
//        int moved = 0;
//        float pxTh = (pxPerCm > 0f) ? (CM_THRESHOLD * pxPerCm) : 240f; // 60cm*pxPerCm 불가시 임시값 상향 //lee
//
//        for (int type : TARGET){
//            PoseLandmark lm = pose.getPoseLandmark(type);
//            if (lm == null) continue;
//
//            PointF p = lm.getPosition();
//
//            // API23 호환(get/put)  //lee
//            Deque<Sample> q = history.get(type);
//            if (q == null) {
//                q = new ArrayDeque<>();
//                history.put(type, q);
//            }
//
//            q.addLast(new Sample(p.x, p.y, nowMs));
//
//            // 1초 윈도우 유지(NPE 안전)  //lee
//            Sample head = q.peekFirst();
//            while (head != null && nowMs - head.t > WINDOW_MS) {
//                q.removeFirst();
//                head = q.peekFirst();
//            }
//
//            if (q.size() >= 2) {
//                Sample first = q.peekFirst();
//                Sample last  = q.peekLast();
//                if (first != null && last != null) {
//                    float distPx = (float) Math.hypot(last.x - first.x, last.y - first.y);
//                    if (distPx >= pxTh) moved++;
//                }
//            }
//        }
//
//        lastMovedCount = moved;  //lee
//
//        if (moved >= 3 && nowMs - lastAlert > ALERT_COOLDOWN_MS){
//            lastAlert = nowMs;
//            return true;
//        }
//        return false;
//    }
//// ======================================================
//
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

        return (distY_px / currentPxPerCm) / timeSec;
    }

}
