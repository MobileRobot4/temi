package com.example.temicommunication.sound.detect;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import ai.picovoice.porcupine.PorcupineException;
import ai.picovoice.porcupine.PorcupineManager;
import ai.picovoice.porcupine.PorcupineManagerCallback;

public class PorcupineVoiceDetector {

    private final Context context;
    private final String[] assetKeywordFiles ;
    private final String assetModelFile ;
    private final OnWakeWordListener onWakeWordListener;

    private static final String accessKey = "0L0YImJRF6kkK3bH4z1hXjGE2mHEpFf+Xu1yTcRdijCaRnX6+MylaQ==";

    private PorcupineManager porcupineManager;

    public PorcupineVoiceDetector(
            Context context,
            String[] assetKeywordFiles ,
            String assetModelFile ,
            OnWakeWordListener listener
    ) {
        this.context = context.getApplicationContext();
        this.assetKeywordFiles  = assetKeywordFiles ;
        this.assetModelFile  = assetModelFile ;
        this.onWakeWordListener = listener;
        initPorcupine();
    }

    private void initPorcupine() {
        PorcupineManagerCallback callback = new PorcupineManagerCallback() {
            @Override
            public void invoke(int keywordIndex) {
                System.out.println("Porcupine 탐지됨 : idx = " + keywordIndex);
                if (onWakeWordListener != null) {
                    onWakeWordListener.onWakeWordDetected();
                }
            }
        };

        try {
            // 1) assets → 내부저장소 복사
            String modelPath = copyAssetToFile(assetModelFile);
            String[] keywordPaths = new String[assetKeywordFiles .length];
            for (int i = 0; i < assetKeywordFiles.length; i++) {
                keywordPaths[i] = copyAssetToFile(assetKeywordFiles[i]);
            }

            porcupineManager = new PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPaths(keywordPaths)
                    .setModelPath(modelPath)
                    .setSensitivities(new float[]{0.8f, 0.7f})
                    .build(context, callback);

            System.out.println("PorcupineManager 생성 완료");

        } catch (PorcupineException e) {
            System.out.println("Porcupine 생성 실패" + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("assets 복사 실패" + e.getMessage());
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

    private String copyAssetToFile(String assetName) throws IOException {
        AssetManager am = context.getAssets();
        File outFile = new File(context.getFilesDir(), assetName);
        if (outFile.exists() && outFile.length() > 0) {
            return outFile.getAbsolutePath();
        }

        // 부모 디렉터리 보장
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir: " + parent);
        }

        try (InputStream is = am.open(assetName);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new IOException("Copied file is empty: " + outFile.getAbsolutePath());
        }
        return outFile.getAbsolutePath();
    }
}
