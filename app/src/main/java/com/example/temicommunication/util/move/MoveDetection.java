package com.example.temicommunication.util.move;

import android.graphics.PointF;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.ArrayDeque;  //lee
import java.util.Deque;      //lee
import java.util.HashMap;    //lee
import java.util.Map;        //lee

public class MoveDetection {

    // 관찰할 랜드마크(0,2,5,9,10,11,12,23,24)  //lee
    private static final int[] TARGET = new int[]{
            PoseLandmark.NOSE,                 // 0
            PoseLandmark.LEFT_EYE,             // 2
            PoseLandmark.RIGHT_EYE,            // 5
            PoseLandmark.LEFT_MOUTH,           // 9
            PoseLandmark.RIGHT_MOUTH,          // 10
            PoseLandmark.LEFT_SHOULDER,        // 11
            PoseLandmark.RIGHT_SHOULDER,       // 12
            PoseLandmark.LEFT_HIP,             // 23
            PoseLandmark.RIGHT_HIP             // 24
    };

    // ★ 요청대로 1초·60cm로 수정  //lee
    private static final long WINDOW_MS = 1000;     //lee
    private static final float CM_THRESHOLD = 200f;  //lee
    private static final long ALERT_COOLDOWN_MS = 8000;

    private float pxPerCm = -1f;
    private int lastMovedCount = 0;                 //lee

    private static class Sample {
        final float x, y; final long t;
        Sample(float x, float y, long t){ this.x=x; this.y=y; this.t=t; }
    }

    private final Map<Integer, Deque<Sample>> history = new HashMap<>();
    private long lastAlert = 0L;

    public void setPxPerCm(float v){ this.pxPerCm = v; }     //lee
    public float getPxPerCm(){ return pxPerCm; }             //lee
    public int getLastMovedCount(){ return lastMovedCount; } //lee

    /** 1초 내 60cm 이상 이동한 포인트가 3개 이상이면 true */ //lee
    public boolean updateAndCheck(Pose pose, long nowMs){
        if (pose == null) return false;

        // 어깨폭≈38cm로 px→cm 간이 보정
        if (pxPerCm <= 0f){
            PoseLandmark L = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
            PoseLandmark R = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
            if (L!=null && R!=null){
                PointF a = L.getPosition();
                PointF b = R.getPosition();
                float shoulderPx = (float) Math.hypot(a.x-b.x, a.y-b.y);
                if (shoulderPx > 10f) pxPerCm = shoulderPx / 38f; //lee
            }
        }

        int moved = 0;
        float pxTh = (pxPerCm > 0f) ? (CM_THRESHOLD * pxPerCm) : 240f; // 60cm*pxPerCm 불가시 임시값 상향 //lee

        for (int type : TARGET){
            PoseLandmark lm = pose.getPoseLandmark(type);
            if (lm == null) continue;

            PointF p = lm.getPosition();

            // API23 호환(get/put)  //lee
            Deque<Sample> q = history.get(type);
            if (q == null) {
                q = new ArrayDeque<>();
                history.put(type, q);
            }

            q.addLast(new Sample(p.x, p.y, nowMs));

            // 1초 윈도우 유지(NPE 안전)  //lee
            Sample head = q.peekFirst();
            while (head != null && nowMs - head.t > WINDOW_MS) {
                q.removeFirst();
                head = q.peekFirst();
            }

            if (q.size() >= 2) {
                Sample first = q.peekFirst();
                Sample last  = q.peekLast();
                if (first != null && last != null) {
                    float distPx = (float) Math.hypot(last.x - first.x, last.y - first.y);
                    if (distPx >= pxTh) moved++;
                }
            }
        }

        lastMovedCount = moved;  //lee

        if (moved >= 3 && nowMs - lastAlert > ALERT_COOLDOWN_MS){
            lastAlert = nowMs;
            return true;
        }
        return false;
    }
}