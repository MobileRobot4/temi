package com.example.temicommunication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;


import com.google.firebase.database.annotations.Nullable;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.Arrays;
import java.util.List;

public class PoseOverlay extends View {
    private final Paint pLandmark = new Paint();    //lee
    private final Paint pLine = new Paint();        //lee

    private Pose pose;                               //lee
    private int srcW = 0, srcH = 0, rotation = 0;    //lee
    private boolean mirror = true;                   // 전면카메라면 true //lee
    private boolean flipY = false;

    // 연결선(간단 버전)  //lee
    private static final int[][] PAIRS = new int[][]{
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER},
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP},
            {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP},
            {PoseLandmark.NOSE, PoseLandmark.LEFT_SHOULDER},
            {PoseLandmark.NOSE, PoseLandmark.RIGHT_SHOULDER},
            {PoseLandmark.LEFT_MOUTH, PoseLandmark.RIGHT_MOUTH}
    };
    // 표시할 포인트(요청한 9개)  //lee
    private static final List<Integer> TARGET = Arrays.asList(
            PoseLandmark.NOSE, PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_MOUTH, PoseLandmark.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP
    );

    public PoseOverlay(Context c) { super(c); init(); }               //lee
    public PoseOverlay(Context c, @Nullable AttributeSet a) { super(c, a); init(); } //lee

    private void init(){                                              //lee
        pLandmark.setColor(0xFFFFCC00); // 노랑  //lee
        pLandmark.setStyle(Paint.Style.FILL);                        //lee
        pLandmark.setStrokeWidth(6f);                                //lee

        pLine.setColor(0xFFFF4444);   // 빨강  //lee
        pLine.setStyle(Paint.Style.STROKE);                          //lee
        pLine.setStrokeWidth(5f);                                    //lee
        setWillNotDraw(false);                                       //lee
    }

    public void setPose(Pose pose, int srcW, int srcH, int rotation){ //lee
        this.pose = pose;
        this.srcW = srcW;
        this.srcH = srcH;
        this.rotation = rotation;
        invalidate();
    }

    public void setMirror(boolean v){ this.mirror = v; invalidate(); }   //lee
    public void setFlipY(boolean v){ this.flipY = v; invalidate(); }     //lee

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (pose == null || srcW <= 0 || srcH <= 0) return;

        // 선 그리기  //lee
        for (int[] pr : PAIRS){
            PoseLandmark a = pose.getPoseLandmark(pr[0]);
            PoseLandmark b = pose.getPoseLandmark(pr[1]);
            if (a == null || b == null) continue;
            float[] A = map(a.getPosition().x, a.getPosition().y);
            float[] B = map(b.getPosition().x, b.getPosition().y);
            c.drawLine(A[0], A[1], B[0], B[1], pLine);
        }

        // 포인트 그리기  //lee
        for (int type : TARGET){
            PoseLandmark lm = pose.getPoseLandmark(type);
            if (lm == null) continue;
            float[] P = map(lm.getPosition().x, lm.getPosition().y);
            c.drawCircle(P[0], P[1], 8f, pLandmark);
        }
    }

    // 이미지 좌표(x,y / srcW x srcH) → View 좌표로 변환 (회전+스케일+오프셋+미러)  //lee
    private float[] map(float x, float y){
        // 1) 회전 보정: 입력(ML Kit)은 이미지 좌표 기준, imageProxy.rotation 반영   //lee
        float rx = x, ry = y;
        switch (rotation){
            case 0:   rx = x;          ry = y;         break;
            case 90:  rx = y;          ry = srcW - x;  break;
            case 180: rx = srcW - x;   ry = srcH - y;  break;
            case 270: rx = srcH - y;   ry = x;         break;
        }

        // 2) 회전 후의 "이미지 가로세로" 결정                                  //lee
        float imgW = (rotation % 180 == 0) ? srcW : srcH;
        float imgH = (rotation % 180 == 0) ? srcH : srcW;

        // 3) PreviewView의 FILL_CENTER와 동일한 스케일 계산                    //lee
        float vw = getWidth(), vh = getHeight();
        float sx = vw / imgW, sy = vh / imgH;
        float scale = Math.max(sx, sy); // FILL_CENTER는 더 큰 스케일을 사용

        // 4) 중앙 정렬 오프셋(여백)                                             //lee
        float drawW = imgW * scale, drawH = imgH * scale;
        float offX = (vw - drawW) * 0.5f;
        float offY = (vh - drawH) * 0.5f;

        // 5) 좌우 미러링(전면카메라면 보통 true)                                //lee
        float vx = offX + rx * scale;
        float vy = offY + ry * scale;

// ★ 좌우 미러(전면카메라면 필요할 수 있음)
        if (mirror) vx = getWidth() - vx;

// ★ 테미에서 위아래가 뒤집혀 보이는 문제 고정
        if (flipY)  vy = getHeight() - vy;

        return new float[]{vx, vy};
    }
}