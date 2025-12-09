package com.example.temicommunication;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.temicommunication.Data.AnalysisData;
import com.example.temicommunication.Data.HeartRateData;
import com.example.temicommunication.Data.SensorData;
import com.example.temicommunication.util.sound.PorcupineVoiceDetector;
import com.example.temicommunication.util.move.MoveDetection;
import com.example.temicommunication.util.move.PoseOverlay;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.UserInfo;
import com.robotemi.sdk.constants.Platform;
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.listeners.OnTelepresenceEventChangedListener;
import com.robotemi.sdk.model.CallEventModel;
import com.robotemi.sdk.model.MemberStatusModel;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.NonNull;

public class MainActivity extends AppCompatActivity
        implements OnRobotReadyListener, OnBeWithMeStatusChangedListener, OnTelepresenceEventChangedListener{

    private ValueEventListener emergencyCancelButtonListener;
    private PoseOverlay poseOverlay;
    private PorcupineVoiceDetector voiceDetector;
    private final MoveDetection moveDetection = new MoveDetection();
    private final AtomicBoolean calling = new AtomicBoolean(false);
    private static final int REQUEST_CODE_FOR_GUARDIAN = 1001;
    private static final int REQUEST_CODE_FOR_EMERGENCY = 1002;
    private static final int REQUEST_CODE_FOR_CAMERA = 1003;
    private static final int REQUEST_CODE_FOR_AUDIO = 1004;
    private static final int EMERGENCY_COUNT_MAX = 10;
    private static final int NORMAL_COLOR = Color.parseColor("#E8F5E9");
    private static final int WARNING_COLOR = Color.parseColor("#FFF3E0");
    private static final int DANGER_COLOR = Color.parseColor("#B71C1C");

    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    DatabaseReference analysisRef = firebaseDatabase.getReference("Analysis");
    DatabaseReference heartRateRef = firebaseDatabase.getReference("HeartRate");
    DatabaseReference sensorRef = firebaseDatabase.getReference("Sensor");
    DatabaseReference emergencyCancelRef = firebaseDatabase.getReference("EmergencyCancel");
    DatabaseReference emergencyRef = firebaseDatabase.getReference("Emergency");
    Button buttonCheckHeart;
    Button buttonExercise;
    Button buttonSleep;
    Button buttonEmergency;
    Button buttonSetGuardian;
    Button buttonCamera;
    ConstraintLayout viewMain;
    PreviewView previewView;
    PoseDetector poseDetector;
    float[] stableHeartRate = new float[20];
    float stableHeartRateAvg = 100;
    boolean checkHeartRate = false;
    boolean isExercise = false;
    boolean isSleep = false;
    boolean emergency = false;
    boolean hideEmergencyButton = true;
    int checkHeartRateCount = 0;
    int emergencyHeartCount = 0;
    int count = 0;
    long checkHeartRateStartDate;
    long heartRateCheckTime;
    long emergencyStartTime;
    long startCallTime;
    Robot robot;
    List<UserInfo> guardians = new ArrayList<>();
    List<UserInfo> calledGuardians = new ArrayList<>();
    Map<String, MemberStatusModel> statusMap = new HashMap<>();
    CameraSelector selector = new CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build();


    @SuppressLint("checkResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        robot = Robot.getInstance();
        loadGuardianList();
        setupEmergencyCancelButtonListener();
        viewMain = findViewById(R.id.viewMain);
        viewMain.setBackgroundColor(NORMAL_COLOR);
        poseOverlay = findViewById(R.id.poseOverlay);
        poseOverlay.setFlipY(true);
        poseOverlay.setMirror(false);
        poseOverlay.setVisibility(View.GONE);
        previewView = findViewById(R.id.viewFinder);
        previewView.setVisibility(View.GONE);
        poseDetector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                                                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                                                .build());
        buttonCamera = findViewById(R.id.buttonCameraDebug);
        buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(previewView.getVisibility() != View.GONE){
                    previewView.setVisibility(View.GONE);
                    poseOverlay.setVisibility(View.GONE);
                } else {
                    previewView.setVisibility(View.VISIBLE);
                    poseOverlay.setVisibility(View.VISIBLE);
                }
            }
        });
        emergencyCancelRef.setValue(false);
        emergencyRef.setValue(false);
        heartRateCheckTime = System.currentTimeMillis();
        buttonCheckHeart = (Button)findViewById(R.id.buttonCheckHeart);
        buttonCheckHeart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonCheckHeart.getText().equals("심박수측정하기(약20초)")){
                    buttonCheckHeart.setText("안정된 상태입니까?");
                } else if(buttonCheckHeart.getText().equals("안정된 상태입니까?")){
                    checkHeartRate = true;
                    checkHeartRateStartDate = System.currentTimeMillis();
                    checkHeartRateCount = 0;
                    buttonCheckHeart.setText("심박수측정중...[0/20]");
                }
            }
        });
        buttonExercise = findViewById(R.id.buttonExercise);
        buttonExercise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonExercise.getText().equals("운동시작")){
                    isExercise = true;
                    buttonExercise.setText("운동종료");
                } else if(buttonExercise.getText().equals("운동종료")){
                    isExercise = false;
                    buttonExercise.setText("운동시작");
                }
            }
        });
        buttonSleep = findViewById(R.id.buttonSleep);
        buttonSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonSleep.getText().equals("취침시작")){
                    isSleep = true;
                    buttonSleep.setText("기상");
                } else if(buttonSleep.getText().equals("기상")){
                    isSleep = false;
                    buttonSleep.setText("취침시작");
                }
            }
        });
        buttonEmergency = findViewById(R.id.buttonEmergency);
        if(hideEmergencyButton){
            buttonEmergency.setVisibility(View.GONE);
        } else {
            buttonEmergency.setVisibility(View.VISIBLE);
        }
        buttonEmergency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(EmergencyCancelActivity.isRunning){
                    return;
                }
                //여기에 응급상황(의심)발생시의 로직 생성
                TtsRequest ttsRequest = TtsRequest.create("응급상황이 의심됩니다. 응급상황이 아닐경우 기기아래 버튼을 클릭하거나 화면의 버튼을 클릭해주세요",false);
                robot.speak(ttsRequest);
                emergencyCancelRef.setValue(false);
                emergency=true;
                emergencyRef.setValue(true);
                emergencyCancelRef.addValueEventListener(emergencyCancelButtonListener);
                emergencyStartTime = System.currentTimeMillis();
                Intent intent = new Intent(MainActivity.this,EmergencyCancelActivity.class);
                intent.putExtra("startTime", emergencyStartTime);
                startActivityForResult(intent,REQUEST_CODE_FOR_EMERGENCY);
            }
        });
        buttonSetGuardian = findViewById(R.id.buttonClose);
        buttonSetGuardian.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent (MainActivity.this,GuardianActivity.class);
                ArrayList<UserInfo> guardianList = new ArrayList<>(guardians);
                intent.putParcelableArrayListExtra("guardians",guardianList);
                ArrayList<UserInfo> users = new ArrayList<>(robot.getAllContact());
                intent.putParcelableArrayListExtra("users",users);
                startActivityForResult(intent, REQUEST_CODE_FOR_GUARDIAN);
            }
        });
        heartRateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                //Log.d("디버그","카운트 : " + count++);
                HeartRateData value = snapshot.getValue(HeartRateData.class);
                long checkTime = convertDateStringToTimestamp(value.getCheckDate());
                if(heartRateCheckTime < checkTime){
                    if(checkHeartRate){
                        long heartDateTimestamp = convertDateStringToTimestamp(value.getHeartDate());
                        if(checkHeartRateStartDate < heartDateTimestamp){
                            if(value.getHeartRate() > 0){
                                stableHeartRate[checkHeartRateCount++] = value.getHeartRate();
                            }
                            if(checkHeartRateCount == 20){
                                checkHeartRate = false;
                                buttonCheckHeart.setText("심박수측정완료");
                                stableHeartRateAvg = 0;
                                for(int i=0; i<20; i++) {
                                    stableHeartRateAvg+=stableHeartRate[i];
                                }
                                stableHeartRateAvg/=20;
                                try{
                                    //심박수측정완료버튼을 3초동안 보이기위해 사용
                                    Thread.sleep(3000);
                                } catch (Exception e){
                                    Log.e("스레드",e.getMessage());
                                }
                                buttonCheckHeart.setText("심박수측정하기(약20초)");
                            } else {
                                buttonCheckHeart.setText("심박수측정중...[" + checkHeartRateCount + "/20]");
                            }
                        } else {
                        }
                    } else if(!isExercise && !isSleep){
                        if(value.getHeartRate()>stableHeartRateAvg*1.35 || value.getHeartRate()<stableHeartRateAvg*0.65) {
                            if(!calling.get()){
                                if(emergencyHeartCount >= EMERGENCY_COUNT_MAX) {
                                    emergency = true;
                                    emergencyHeartCount = 0;
                                    buttonEmergency.callOnClick();
                                } else {
                                    emergencyHeartCount ++;
                                }
                            }
                        } else {
                            emergencyHeartCount = 0;
                            if(EmergencyCancelActivity.isRunning){
                                emergencyEnded();
                            }
                        }
                    } else if(isExercise || isSleep){
                        emergencyHeartCount = 0;
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("heart", error.getMessage());
            }
        });
        analysisRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NotNull DataSnapshot snapshot) {
                AnalysisData value = snapshot.getValue(AnalysisData.class);
                switch(value.getStatus()){
                    case 1:
                        viewMain.setBackgroundColor(NORMAL_COLOR);
                        break;
                    case 2:
                        viewMain.setBackgroundColor(WARNING_COLOR);
                        break;
                    case 3:
                        viewMain.setBackgroundColor(DANGER_COLOR);
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onCancelled(@NotNull DatabaseError error) {
                Log.e("analysis", error.getMessage());
            }
        });
        sensorRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                SensorData value = snapshot.getValue(SensorData.class);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("sensor", error.getMessage());
            }
        });
        if(guardians.size() == 0) {
            showNotGuardianDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(voiceDetector != null) {
            voiceDetector.start();
        }
    }

    @Override
    protected void onPause(){
        if(voiceDetector != null){
            super.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        if(voiceDetector != null) {
            voiceDetector.release();
            voiceDetector = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE_FOR_GUARDIAN){
            if(resultCode == Activity.RESULT_OK){
                if(data != null) {
                    guardians = data.getParcelableArrayListExtra("guardians");
                    if(guardians.isEmpty()) {
                        showNotGuardianDialog();
                    } else {
                        saveGuardianList();
                    }
                }
            } else if(resultCode == Activity.RESULT_CANCELED){
            }
        } else if(requestCode == REQUEST_CODE_FOR_EMERGENCY){
            if(resultCode != 4) {
                emergencyEnded();
            } else {
                callEmergency();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CODE_FOR_CAMERA){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                startCamera();
            }
        } else if(requestCode == REQUEST_CODE_FOR_AUDIO){
            if(grantResults.length > 0 &&grantResults[0] == PackageManager.PERMISSION_GRANTED){
                //setupVoiceDetector();
                if(voiceDetector != null){
                    voiceDetector.start();
                }
            }
        }
    }

    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try{
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyze);
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, analysis);
            } catch (Exception e){
                Log.e("카메라", "카메라오류 : " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyze(ImageProxy imageProxy){
        if(imageProxy.getImage() == null){
            imageProxy.close();
            return;
        }
        int rot = imageProxy.getImageInfo().getRotationDegrees();
        int srcW = imageProxy.getWidth();
        int srcH = imageProxy.getHeight();
        InputImage input = InputImage.fromMediaImage(imageProxy.getImage(), rot);
        poseDetector.process(input)
                .addOnSuccessListener((Pose pose) -> {
                    long now = System.currentTimeMillis();
                    boolean hit = moveDetection.updateAndCheck(pose, now);
                    int moved = moveDetection.getLastMovedCount();
                    poseOverlay.setPose(pose, srcW, srcH, rot);
                    if(hit){
                        buttonEmergency.callOnClick();
                    }
                    imageProxy.close();
                }).addOnFailureListener(e -> {imageProxy.close();});
    }

    private void saveGuardianList() {
        Gson gson = new Gson();
        String json = gson.toJson(guardians);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("guardian_list",json);
        editor.apply();
    }

    private void setupEmergencyCancelButtonListener() {
        if(emergencyCancelButtonListener == null) {
            emergencyCancelButtonListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if(snapshot.getValue(Boolean.class)==true){
                        emergencyEnded();
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {

                }
            };
        }
    }

    private void loadGuardianList(){
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String json = sharedPreferences.getString("guardian_list",null);
        if(json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<UserInfo>>() {}.getType();
            List<UserInfo> savedGuardianList = gson.fromJson(json,type);
            if(savedGuardianList != null && !savedGuardianList.isEmpty()) {
                guardians.clear();
                guardians.addAll(savedGuardianList);
            }
        } else {
            guardians.clear();
        }
    }

    private void showNotGuardianDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("보호자 설정").setMessage("보호자가 없습니다! 보호자를 설정해주세요");
        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id){
                dialog.dismiss();
                buttonSetGuardian.callOnClick();
            }
        });
        builder.show();
    }

    // porcupineDetector 생성
    private void setupVoiceDetector() {
        voiceDetector = new PorcupineVoiceDetector(
                this,
                new String[]{"sallyeojuseyo_ko_android_v3_0_0.ppn", "dowajueo_ko_android_v3_0_0.ppn"}, // assets 안의 키워드 파일 이름
                "porcupine_params_ko.pv",
                () -> runOnUiThread(() -> {
                    if (!calling.compareAndSet(false, true)) return;
                    //Log.d("디버그", "살려주세요 인식됨");
                    buttonEmergency.callOnClick();
                    // 10초 후 다시 허용
                    new Handler(Looper.getMainLooper())
                            .postDelayed(() -> calling.set(false), 10_000);
                })
        );
    }


    @Override
    public void onStart() {
        Log.d("디버그","onStart");
        super.onStart();
        robot.addOnRobotReadyListener(this);
        robot.addOnBeWithMeStatusChangedListener(this);
        robot.addOnTelepresenceEventChangedListener(this);
    }

    @Override
    public void onStop() {
        Log.d("디버그","onStop");
        super.onStop();
        robot.removeOnRobotReadyListener(this);
        robot.removeOnBeWithMeStatusChangedListener(this);
        robot.removeOnTelepresenceEventChangedListener(this);
    }

    @Override
    public void onRobotReady(boolean isReady){
        Log.d("디버그", "로봇준비됨");
        if(isReady){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQUEST_CODE_FOR_CAMERA);
            } else {
                startCamera();
            }
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_CODE_FOR_AUDIO);
            } else {
                setupVoiceDetector();
                if(voiceDetector != null){
                    voiceDetector.start();
                }
            }
            try{
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
            } catch(PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    //로봇이 따라가기 상태가 변할때 실행하는 메서드
    @Override
    public void onBeWithMeStatusChanged(String status) {
        Log.d("디버그","따라가기상태변화");
        switch(status) {
            case ABORT:             //따라가기 중단됐을때
                break;
            case CALCULATING:       //따라가는중에 장애물을 발견해서 돌아가는길 계산중일때
                break;
            case SEARCH:            //따라가기모드 실행되서 사람을 찾고있을때
                break;
            case START:             //따라가기모드 실행되고나서 사람을 찾고 따라가기 시작했을떄
                break;
            case TRACK:             //따라가기중일때
                break;
            case OBSTACLE_DETECTED: //따라가는중 장애물 감지했을때
                break;
            default:
                break;
        }
    }

    @Override
    public void onTelepresenceEventChanged(CallEventModel model){
        if(emergency){
            if(model.getState() == 1){
                if(System.currentTimeMillis()-startCallTime > 52000){
                    calling.set(false);
                    emergencyEnded();
                } else {
                    callEmergency();
                }
            }
        }
    }

    public void callEmergency(){
        //보호자에게전화걸기
        //전화가능상태를 가져와서 전화가능한사람먼저 통화
        //전화거는거는 테미앱, 테미센터(데스크탑)두가지가 존재
        List<MemberStatusModel> statusList = robot.getMembersStatus();
        statusMap.clear();
        for(MemberStatusModel status : statusList){
            statusMap.put(status.getMemberId(), status);
        }
        if(guardians.size() == calledGuardians.size()) {
            calledGuardians.clear();
        }
        for(UserInfo guardian : guardians) {
            MemberStatusModel status = statusMap.get(guardian.getUserId());
            if(status.getMobileStatus()==0 && !calledGuardians.contains(guardian)){
                calling.compareAndSet(false,true);
                startCallTime = System.currentTimeMillis();
                robot.startTelepresence("",guardian.getUserId(),Platform.MOBILE);
                calledGuardians.add(guardian);
                break;
            } else if(status.getCenterStatus() == 0 && !calledGuardians.contains(guardian)){
                calling.compareAndSet(false,true);
                startCallTime = System.currentTimeMillis();
                robot.startTelepresence("",guardian.getUserId(),Platform.TEMI_CENTER);
                calledGuardians.add(guardian);
                break;
            }
            //이곳은 남은 보호자중 통화할수있는 사람이 아무도 없을때
            if(calledGuardians.size() == 0){
                TtsRequest ttsRequest = TtsRequest.create("연락할수있는 보호자가 없습니다",false);
                robot.speak(ttsRequest);
            } else {
                calledGuardians.clear();
                callEmergency();
            }
        }
    }

    private void emergencyEnded() {
        if(emergency){
            emergency = false;
            calledGuardians.clear();
            statusMap.clear();
            emergencyRef.setValue(false);
            emergencyCancelRef.setValue(false);
            emergencyCancelRef.removeEventListener(emergencyCancelButtonListener);
            emergencyHeartCount=0;
            EmergencyCancelActivity emergencyCancelActivity = EmergencyCancelActivity.getInstance();
            if(emergencyCancelActivity != null){
                emergencyCancelActivity.finish();
            }
        }
    }

    private long convertDateStringToTimestamp(String dateString) {
        // 1. 문자열을 밀리초까지만 남도록 전처리 (마이크로초 제거)
        int dotIndex = dateString.indexOf('.');

        // 마침표가 있고, 그 뒤에 최소 3자리(밀리초) 이상의 데이터가 있는 경우
        if (dotIndex > 0 && dateString.length() > dotIndex + 3) {
            // 밀리초 3자리만 남기고 뒤의 마이크로초를 잘라냅니다. (dotIndex + 4 = 마침표 + 3자리)
            dateString = dateString.substring(0, dotIndex + 4);
        }
        // 만약 포맷이 이상하거나 마침표가 없다면 파싱은 어차피 실패할 수 있으나, 일단 시도는 진행합니다.

        // 2. 파싱 패턴: "yyyy-MM-dd'T'HH:mm:ss.SSS"
        // 이 패턴은 자른 문자열(예: "2025-11-25T18:23:59.385")과 일치합니다.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());

        try {
            Date date = sdf.parse(dateString);
            return date != null ? date.getTime() : 0L;
        } catch (ParseException e) {
            Log.e("DateConvert", "최종 파싱 실패: " + dateString, e);
            return 0L;
        }
    }
}