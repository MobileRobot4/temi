package com.example.temicommunication.thermal;

public interface ThermalCamera {
    void startStreaming(FrameCallback callback);
    void stopStreaming();

    interface FrameCallback {
        void onFrame(float[][] frame);
    }
}