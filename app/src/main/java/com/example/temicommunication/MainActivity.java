package com.example.temicommunication;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;

// 기존 import 아래에 정확히 이걸로
import android.Manifest;                                //lee
//import android.annotation.NonNull;                     //lee  // ← android.annotation 말고 androidx!
import androidx.core.app.ActivityCompat;                //lee
import androidx.core.content.ContextCompat;             //lee
import androidx.camera.view.PreviewView;                //lee
import androidx.camera.core.CameraSelector;             //lee
import androidx.camera.core.ImageAnalysis;              //lee
import androidx.camera.core.ImageProxy;                 //lee
import androidx.camera.core.Preview;                    //lee
import androidx.camera.lifecycle.ProcessCameraProvider; //lee
import com.google.common.util.concurrent.ListenableFuture; //lee

import com.google.mlkit.vision.common.InputImage;       //lee
import com.google.mlkit.vision.pose.Pose;               //lee
import com.google.mlkit.vision.pose.PoseDetection;      //lee
import com.google.mlkit.vision.pose.PoseDetector;       //lee
//import com.google.mlkit.vision.pose.PoseDetectorOptions;//lee

import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.robotemi.sdk.Robot;                          //lee
import com.robotemi.sdk.TtsRequest;                     //lee
import java.util.concurrent.Executors;                  //lee



public class MainActivity extends AppCompatActivity {

    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    DatabaseReference myRef = firebaseDatabase.getReference();

    // ===== 아래는 추가 필드 =====
    private PreviewView previewView;                        //lee
    private PoseDetector poseDetector;                      //lee
    private final MoveDetection moveDetection = new MoveDetection(); //lee
    private Robot robot;                                    //lee
    private TextView distanceText2Lee;                      //lee
    // ==========================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SwitchCompat switchCompat = (SwitchCompat) findViewById(R.id.switchLed);
        TextView distanceText = (TextView)findViewById(R.id.textDistance);
        Button buttonDistance = (Button)findViewById(R.id.buttonDistance);
        TextView distanceText2 = (TextView)findViewById(R.id.textDistance2);

        // ===== 추가: 카메라 프리뷰/Temi/포즈 초기화 =====
        previewView = findViewById(R.id.viewFinder);        //lee
        robot = Robot.getInstance();                        //lee
        poseDetector = PoseDetection.getClient(             //lee
                new PoseDetectorOptions.Builder()           //lee
                        .setDetectorMode(PoseDetectorOptions.STREAM_MODE) //lee
                        .build()                            //lee
        );                                                  //lee
        distanceText2Lee = findViewById(R.id.textDistance2);//lee
        ActivityCompat.requestPermissions(                  //lee
                this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                1000
        );                                                  //lee
        // ===============================================

        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NotNull DataSnapshot snapshot) {
                FirebaseDataFormat value = snapshot.getValue(FirebaseDataFormat.class);
                System.out.println("Success to read value : " + value.toString());
                distanceText.setText("success");
            }

            @Override
            public void onCancelled(@NotNull DatabaseError error) {
                System.out.println("Failed to read value : " + error.toException());
                distanceText.setText("fail");
            }
        });
    }

    // ===== 추가: 권한 콜백에서 카메라 시작 =====
    @Override                                                   //lee
    public void onRequestPermissionsResult(                     //lFee
                                                                int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) { //lee
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);          //lee
        if (requestCode == 1000) startCamera();                   //lee
    }                                                             //lee
    // =======================================================

    // ===== 추가: CameraX 파이프라인 =====
    private void startCamera() {                                  //lee
        ListenableFuture<ProcessCameraProvider> future =          //lee
                ProcessCameraProvider.getInstance(this);          //lee
        future.addListener(() -> {                                //lee
            try {                                                 //lee
                ProcessCameraProvider provider = future.get();    //lee
                Preview preview = new Preview.Builder().build();  //lee
                preview.setSurfaceProvider(previewView.getSurfaceProvider()); //lee

                ImageAnalysis analysis = new ImageAnalysis.Builder()          //lee
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //lee
                        .build();                                             //lee
                analysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyze); //lee

                CameraSelector selector = new CameraSelector.Builder()        //lee
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)  //lee
                        .build();                                             //lee

                provider.unbindAll();                                         //lee
                provider.bindToLifecycle(this, selector, preview, analysis);  //lee
            } catch (Exception e) {                                           //lee
                Log.e("CameraX", "startCamera error", e);                     //lee
            }                                                                 //lee
        }, ContextCompat.getMainExecutor(this));                              //lee
    }                                                                         //lee
    // ===================================

    // ===== 추가: 프레임 분석 + 낙상 판단 =====
    private void analyze(ImageProxy imageProxy) {                              //lee
        if (imageProxy.getImage() == null) { imageProxy.close(); return; }     //lee
        InputImage input = InputImage.fromMediaImage(                          //lee
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees()); //lee

        poseDetector.process(input)                                            //lee
                .addOnSuccessListener((Pose pose) -> {                         //lee
                    long now = System.currentTimeMillis();                     //lee
                    if (moveDetection.updateAndCheck(pose, now)) {             //lee
                        robot.speak(TtsRequest.create(                         //lee
                                "경고: 넘어짐이 감지되었습니다. 도움이 필요하신가요?", true)); //lee
                        runOnUiThread(() -> distanceText2Lee.setText("FALL DETECTED"));   //lee
                    }                                                          //lee
                    imageProxy.close();                                        //lee
                })                                                             //lee
                .addOnFailureListener(e -> {                                   //lee
                    Log.e("Pose", "process fail", e);                          //lee
                    imageProxy.close();                                        //lee
                });                                                            //lee
    }                                                                           //lee
    // ========================================
}
