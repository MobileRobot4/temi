package com.example.temicommunication;

import android.graphics.PointF;                   // ★ 2D 포인트
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class MoveDetection {

    // 감시할 랜드마크 (0,2,5,9,10,11,12,23,24)
    private static final int[] TARGET = new int[] {
            PoseLandmark.NOSE,                // 0
            PoseLandmark.LEFT_EYE,            // 2
            PoseLandmark.RIGHT_EYE,           // 5
            PoseLandmark.LEFT_MOUTH,          // 9  ★ 이름 수정
            PoseLandmark.RIGHT_MOUTH,         // 10 ★ 이름 수정
            PoseLandmark.LEFT_SHOULDER,       // 11
            PoseLandmark.RIGHT_SHOULDER,      // 12
            PoseLandmark.LEFT_HIP,            // 23
            PoseLandmark.RIGHT_HIP            // 24
    };

    private static final long WINDOW_MS = 3000;   // 3초
    private static final float CM_THRESHOLD = 30f;// 30 cm

    private float pxPerCm = -1f;

    private static class Sample {
        final float x, y; final long t;
        Sample(float x, float y, long t) { this.x = x; this.y = y; this.t = t; }
    }

    private final Map<Integer, Deque<Sample>> history = new HashMap<>();
    private long lastAlert = 0L;
    private static final long ALERT_COOLDOWN_MS = 8000L;

    public void setPxPerCm(float v) { this.pxPerCm = v; }

    /** 3초 내 30cm 이상 이동한 포인트가 3개 이상이면 true */
    public boolean updateAndCheck(Pose pose, long nowMs) {
        if (pose == null) return false;

        // 1) px↔cm 간이 보정: 어깨폭 ≈ 38 cm (현장 보정 권장)
        if (pxPerCm <= 0f) {
            PoseLandmark L = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
            PoseLandmark R = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
            if (L != null && R != null) {
                PointF a = L.getPosition();     // ★ PointF 사용
                PointF b = R.getPosition();
                float dx = a.x - b.x, dy = a.y - b.y;
                float shoulderPx = (float) Math.hypot(dx, dy);
                if (shoulderPx > 10f) pxPerCm = shoulderPx / 38f; // 임시 보정값
            }
        }

        // 2) 이동량 집계
        int moved = 0;
        float pxTh = (pxPerCm > 0f) ? (CM_THRESHOLD * pxPerCm) : 120f; // 보정 전 임시값

        for (int type : TARGET) {
            PoseLandmark lm = pose.getPoseLandmark(type);
            if (lm == null) continue;
            PointF p = lm.getPosition();       // ★ PointF
            Deque<Sample> q = history.get(type);                    //lee
            if (q == null) {                                        //lee
                q = new ArrayDeque<>();                             //lee
                history.put(type, q);                               //lee
            }
            q.addLast(new Sample(p.x, p.y, nowMs));
            // 3초 윈도우 유지
            while (!q.isEmpty() && nowMs - q.peekFirst().t > WINDOW_MS) q.removeFirst();

            if (q.size() >= 2) {
                Sample first = q.peekFirst();
                Sample last  = q.peekLast();
                float distPx = (float) Math.hypot(last.x - first.x, last.y - first.y);
                if (distPx >= pxTh) moved++;
            }
        }

        // 3) 판정 + 쿨다운
        if (moved >= 3 && nowMs - lastAlert > ALERT_COOLDOWN_MS) {
            lastAlert = nowMs;
            return true;
        }
        return false;
    }
}
