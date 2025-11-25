package com.example.temicommunication.detect;

public class PersonDetector {
    private static final float HUMAN_TEMP_THRESHOLD = 30.0f;
    private static final float MIN_HOT_RATIO = 0.02f;

    public static class DetectionResult {
        public boolean isHuman;
        public float maxTemp;
        public float hotPixelRatio;

        public DetectionResult(boolean isHuman, float maxTemp, float hotPixelRatio) {
            this.isHuman = isHuman;
            this.maxTemp = maxTemp;
            this.hotPixelRatio = hotPixelRatio;
        }
    }

    public static DetectionResult analyzeFrame(float[][] frame) {
        float maxTemp = Float.MIN_VALUE;
        int hot = 0;
        int total = 0;

        for (float[] row : frame) {
            for (float v : row) {
                total++;
                if (v > maxTemp) maxTemp = v;
                if (v >= HUMAN_TEMP_THRESHOLD) hot++;
            }
        }

        float ratio = (float) hot / total;
        boolean isHuman = ratio >= MIN_HOT_RATIO;

        return new DetectionResult(isHuman, maxTemp, ratio);
    }
}
