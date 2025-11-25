package com.example.temicommunication.thermal;

import android.os.Handler;
import android.os.Looper;

import java.util.Random;

public class Mlx90640DummyCamera implements ThermalCamera {

    private boolean running = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    @Override
    public void startStreaming(FrameCallback callback) {
        running = true;

        Runnable loop = new Runnable() {
            @Override
            public void run() {
                if (!running) return;

                int rows = 24;
                int cols = 32;
                float[][] frame = new float[rows][cols];

                float base = 24f;

                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        frame[r][c] = base + random.nextFloat() * 2f;
                    }
                }

                for (int r = 8; r < 16; r++) {
                    for (int c = 10; c < 20; c++) {
                        frame[r][c] = 33f + random.nextFloat() * 3f;
                    }
                }

                callback.onFrame(frame);
                handler.postDelayed(this, 500);
            }
        };

        handler.post(loop);
    }

    @Override
    public void stopStreaming() {
        running = false;
    }
}
