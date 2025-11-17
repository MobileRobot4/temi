package com.example.mobilerobot.sound.detect;

import android.content.Context;

import ai.picovoice.porcupine.PorcupineException;
import ai.picovoice.porcupine.PorcupineManager;
import ai.picovoice.porcupine.PorcupineManagerCallback;

public class PorcupineVoiceDetector {

    private final Context context;
    private final String[] keywordPaths;
    private final String modelPath;
    private final OnWakeWordListener onWakeWordListener;

    private static final String accessKey = "0L0YImJRF6kkK3bH4z1hXjGE2mHEpFf+Xu1yTcRdijCaRnX6+MylaQ==";

    private PorcupineManager porcupineManager;

    public PorcupineVoiceDetector(
            Context context,
            String[] keywordPaths,
            String modelPath,
            OnWakeWordListener listener
    ) {
        this.context = context.getApplicationContext();
        this.keywordPaths = keywordPaths;
        this.modelPath = modelPath;
        this.onWakeWordListener = listener;
        initPorcupine();
    }

    private void initPorcupine() {
        PorcupineManagerCallback callback = new PorcupineManagerCallback() {
            @Override
            public void invoke(int keywordIndex) {
                if (onWakeWordListener != null) {
                    onWakeWordListener.onWakeWordDetected();
                }
            }
        };

        try {
            porcupineManager = new PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPaths(keywordPaths)
                    .setModelPath(modelPath)
                    .build(context, callback);

        } catch (PorcupineException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (porcupineManager == null) {
            throw new IllegalArgumentException("porcupineManager가 생성되지 않았습니다.");
        }

        try {
            porcupineManager.start();
        } catch (PorcupineException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (porcupineManager == null) {
            throw new IllegalArgumentException("porcupineManager가 생성되지 않았습니다.");
        }

        try {
            porcupineManager.stop();
        } catch (PorcupineException e) {
            e.printStackTrace();
        }
    }

    public void release() {
        if (porcupineManager == null) {
            throw new IllegalArgumentException("porcupineManager가 생성되지 않았습니다.");
        }

        porcupineManager.delete();
        porcupineManager = null;
    }
}
