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
                System.out.println("Porcupine 탐지중");
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

            System.out.println("PorcupineManager 생성 완료");

        } catch (PorcupineException e) {
            System.out.println("Porcupine 생성 실패" + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start() {
        if (porcupineManager == null) {
            throw new IllegalArgumentException("porcupineManager가 생성되지 않았습니다.");
        }

        try {
            System.out.println("PorcupineManager.start() 호출");
            porcupineManager.start();
            System.out.println("PorcupineManager.start() 시작");
        } catch (PorcupineException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (porcupineManager == null) {
            throw new IllegalArgumentException("porcupineManager가 생성되지 않았습니다.");
        }

        try {

            System.out.println("PorcupineManager.stop() 호출");
            porcupineManager.stop();
        } catch (PorcupineException e) {
            e.printStackTrace();
        }
    }

    public void release() {
        if (porcupineManager == null) {
            throw new IllegalArgumentException("porcupineManager가 생성되지 않았습니다.");
        }

        System.out.println("PorcupineManager.delete() 호출");
        porcupineManager.delete();
        porcupineManager = null;
    }
}
