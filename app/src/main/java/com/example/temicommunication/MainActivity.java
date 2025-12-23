package com.example.temicommunication;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.temicommunication.Data.AnalysisData;
import com.example.temicommunication.Data.HeartRateData;
import com.example.temicommunication.Data.SensorData;
import com.example.temicommunication.util.move.MoveDetection;
import com.example.temicommunication.util.sound.PorcupineVoiceDetector;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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
import com.robotemi.sdk.navigation.model.SpeedLevel;

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

import javax.annotation.Nonnull;

public class MainActivity extends AppCompatActivity
        implements OnRobotReadyListener, OnBeWithMeStatusChangedListener, OnTelepresenceEventChangedListener, ValueEventListener {

    private ValueEventListener emergencyCancelButtonListener;
    private PorcupineVoiceDetector voiceDetector;
    private Handler warnHandler = new Handler();
    private Handler dangerHandler = new Handler();
    private Handler missingAlertHandler = new Handler();
    private Runnable warnRunnable;
    private Runnable dangerRunnable;
    private final MoveDetection moveDetection = new MoveDetection();
    private final AtomicBoolean calling = new AtomicBoolean(false);
    private static final int REQUEST_CODE_FOR_MENU = 1001;
    private static final int REQUEST_CODE_FOR_EMERGENCY = 1002;
    private static final int REQUEST_CODE_FOR_CAMERA = 1003;
    private static final int REQUEST_CODE_FOR_AUDIO = 1004;
    private static final int EMERGENCY_COUNT_MAX = 10;
    private static final int AIR_WARNING_INTERVAL = 60000;
    private static final int AIR_DANGER_INTERVAL = 3000;
    private static final int MOVE_DETECTION_INTERVAL = 60000;
    private boolean cameraOnLogged = false;
    private boolean isWarningActive = false;

    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    DatabaseReference analysisRef = firebaseDatabase.getReference("Analysis");
    DatabaseReference heartRateRef = firebaseDatabase.getReference("HeartRate");
    DatabaseReference sensorRef = firebaseDatabase.getReference("Sensor");
    DatabaseReference emergencyCancelRef = firebaseDatabase.getReference("EmergencyCancel");
    DatabaseReference emergencyRef = firebaseDatabase.getReference("Emergency");
    Button buttonEmergency;
    TextView textViewAir;
    ImageButton buttonMenu;
    ImageView imageViewAir;
    PreviewView previewView;
    PoseDetector poseDetector;
    float stableHeartRateAvg = 100;
    boolean isExercise = false;
    boolean isSleep = false;
    boolean emergency = false;
    boolean hideEmergencyButton = true;
    int emergencyHeartCount = 0;
    long heartRateCheckTime;
    long emergencyStartTime;
    long startCallTime;
    long airWarnTime;
    long moveDetectionTime = 0;
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
        buttonMenu = findViewById(R.id.buttonMenu);
        buttonMenu.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, MenuActivity.class);
            intent.putParcelableArrayListExtra("users", new ArrayList<>(robot.getAllContact()));
            startActivityForResult(intent, REQUEST_CODE_FOR_MENU);
            heartRateRef.removeEventListener(MainActivity.this);
        });
        loadGuardianList();
        loadAvgHeartRate();
        setupEmergencyCancelButtonListener();
        imageViewAir = findViewById(R.id.imageViewAir);
        imageViewAir.setContentDescription("정상");
        textViewAir = findViewById(R.id.textViewAir);
        poseDetector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build());
        emergencyCancelRef.setValue(false);
        emergencyRef.setValue(false);
        heartRateCheckTime = System.currentTimeMillis();
        buttonEmergency = findViewById(R.id.buttonEmergency);
        if (hideEmergencyButton) {
            buttonEmergency.setVisibility(View.GONE);
        } else {
            buttonEmergency.setVisibility(View.VISIBLE);
        }
        buttonEmergency.setOnClickListener(view -> {
            if (EmergencyCancelActivity.isRunning) {
                return;
            }
            //여기에 응급상황(의심)발생시의 로직 생성
            TtsRequest ttsRequest = TtsRequest.create("응급상황이 의심됩니다. 응급상황이 아닐경우 기기뒤의 버튼을 누르거나 화면의 빨간부분을 터치해주세요", false);
            robot.speak(ttsRequest);
            emergencyCancelRef.setValue(false);
            emergencyRef.setValue(true);
            emergencyCancelRef.addValueEventListener(emergencyCancelButtonListener);
            emergency = true;
            emergencyStartTime = System.currentTimeMillis();
            Intent intent = new Intent(MainActivity.this, EmergencyCancelActivity.class);
            intent.putExtra("startTime", emergencyStartTime);
            startActivityForResult(intent, REQUEST_CODE_FOR_EMERGENCY);
        });
        warnRunnable = new Runnable() {
            @Override
            public void run() {
                if(System.currentTimeMillis() - airWarnTime > MOVE_DETECTION_INTERVAL){
                    TtsRequest request = TtsRequest.create("환기를 시켜주세요",false);
                    robot.speak(request);
                    warnHandler.postDelayed(this,AIR_WARNING_INTERVAL);
                } else {
                    warnHandler.postDelayed(this,5000);
                }
            }
        };
        dangerRunnable = new Runnable() {
            @Override
            public void run() {
                TtsRequest request = TtsRequest.create("비상", false);
                robot.speak(request);
                buttonEmergency.callOnClick();
                dangerHandler.postDelayed(this,AIR_DANGER_INTERVAL);
            }
        };
        heartRateRef.addValueEventListener(this);
        analysisRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@Nonnull DataSnapshot snapshot) {
                AnalysisData value = snapshot.getValue(AnalysisData.class);
                switch (value.getStatus()) {
                    case 1:
                        warnHandler.removeCallbacks(warnRunnable);
                        dangerHandler.removeCallbacks(dangerRunnable);
                        imageViewAir.setImageResource(R.drawable.smile);
                        imageViewAir.setContentDescription("정상");
                        break;
                    case 2:
                        if(!imageViewAir.getContentDescription().toString().equals("경고")){
                            imageViewAir.setImageResource(R.drawable.warn);
                            warnHandler.post(warnRunnable);
                            imageViewAir.setContentDescription("경고");
                            airWarnTime = System.currentTimeMillis();
                        }
                        break;
                    case 3:
                        if(!imageViewAir.getContentDescription().toString().equals("위험")){
                            imageViewAir.setImageResource(R.drawable.emergency);
                            dangerHandler.post(dangerRunnable);
                            imageViewAir.setContentDescription("위험");
                        }
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onCancelled(@Nonnull DatabaseError error) {
                Log.e("analysis", error.getMessage());
            }
        });
        sensorRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@Nonnull DataSnapshot snapshot) {
                SensorData value = snapshot.getValue(SensorData.class);
                textViewAir.setText("온도 : " + value.getTemperature() + "도    습도 : " + value.getHumidity() + "%   이산화탄소 : " + value.getCO2() + "ppm   미세먼지 : " + value.getPM10() + "   초미세먼지 : "+ value.getPM2_5());
            }

            @Override
            public void onCancelled(@Nonnull DatabaseError error) {
                Log.e("sensor", error.getMessage());
            }
        });
        if (guardians.size() == 0) {
            showNotGuardianDialog();
        }
        if (stableHeartRateAvg == 0) {
            showNotHeartRateDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (voiceDetector != null) {
            voiceDetector.start();
        }
    }

    @Override
    protected void onPause() {
        if (voiceDetector != null) {
            super.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (voiceDetector != null) {
            voiceDetector.release();
            voiceDetector = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FOR_EMERGENCY) {
            if (resultCode != 4) {
                robot.cancelAllTtsRequests();
                emergencyEnded();
            } else {
                callEmergency();
            }
        } else if (requestCode == REQUEST_CODE_FOR_MENU) {
            if ((resultCode & 8) != 0) { //보호자설정
                loadGuardianList();
            }
            if ((resultCode & 4) != 0) { //운동중설정
                isExercise = data.getBooleanExtra("exercise", isExercise);
            }
            if ((resultCode & 2) != 0) { //취침중설정
                isSleep = data.getBooleanExtra("sleep", isSleep);
            }
            if ((resultCode & 1) != 0) { //평균심박수설정
                loadAvgHeartRate();
            }
            if (guardians.isEmpty()) {
                showNotGuardianDialog();
            }
            if (stableHeartRateAvg == 0) {
                showNotHeartRateDialog();
            }
            heartRateRef.addValueEventListener(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @Nonnull String[] permissions, @Nonnull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_FOR_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
        } else if (requestCode == REQUEST_CODE_FOR_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupVoiceDetector();
                if (voiceDetector != null) {
                    voiceDetector.start();
                }
            }
        }
    }

    private void startCamera() {
        cameraOnLogged = false;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
//                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyze);
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, analysis);
            } catch (Exception e) {
                Log.e("카메라", "카메라오류 : " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyze(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        if (poseDetector == null) {
            imageProxy.close();
            return;
        }

        try {
            if (!cameraOnLogged) {
                Log.d("Camera X", "카메라 정상 작동 중");
                cameraOnLogged = true;
            }

            int rot = imageProxy.getImageInfo().getRotationDegrees();
            InputImage input = InputImage.fromMediaImage(imageProxy.getImage(), rot);

            poseDetector.process(input)
                    .addOnSuccessListener((Pose pose) -> {
                        try {
                            long now = System.currentTimeMillis();

                            // 1. MoveDetection 업데이트
                            boolean hit = moveDetection.updateAndCheck(pose, now);

                            // 2. 낙상 감지 (기존 기능)
                            if (hit && System.currentTimeMillis() - moveDetectionTime > 6000) {
                                Log.d("디버그", "넘어짐 감지됨!");
                                buttonEmergency.callOnClick();
                                moveDetectionTime = System.currentTimeMillis();
                            }

                            // 3. 사라짐 감지 (사람 미인식 -> 소리 경고 시작)
                            if (moveDetection.checkMissingPerson(now)) {
                                startMissingWarning(); // 바뀐 함수 호출
                            }

                            // 4. 사라짐 복구 (사람 재인식 -> 경고 취소)
                            // 조건: (경고 중이고) && (사람이 다시 보이면)
                            if (isWarningActive && moveDetection.isPersonVisible()) {
                                runOnUiThread(() -> {
                                    if (isWarningActive) {
                                        Log.d("CheckMissing", "👀 사람 재인식됨! -> 경고 취소");

                                        // 핸들러에 걸린 비상벨 타이머 취소!
                                        missingAlertHandler.removeCallbacksAndMessages(null);

                                        isWarningActive = false; // 경고 상태 해제

                                        // 안심 멘트
                                        robot.speak(TtsRequest.create("사용자가 인식되었습니다.", false));
                                    }
                                });
                            }

                        } catch (Exception e) {
                            Log.e("Analyze", "로직 오류: " + e.getMessage());
                        } finally {
                            imageProxy.close();
                        }
                    })
                    .addOnFailureListener(e -> {
                        imageProxy.close();
                    });

        } catch (Exception e) {
            imageProxy.close();
        }
    }

    private void startMissingWarning() {
        runOnUiThread(() -> {
            // 이미 경고 중이면 또 실행하지 않음 (중복 방지)
            if (isWarningActive) return;

            Log.d("CheckMissing", "🔊 사람이 사라짐 -> 소리 경고 시작");
            isWarningActive = true; // 경고 상태 켜기

            // 1. 로봇이 말로 경고
            robot.speak(TtsRequest.create("괜찮으십니까? 10초 후 비상 알림이 전송됩니다. 알림을 끄려면 카메라 앞에 서주세요.", false));

            // 2. 10초 뒤 실행될 비상벨 행동
            Runnable finalAlertRunnable = () -> {
                Log.d("CheckMissing", "⏰ 10초 타임아웃 -> 비상벨 클릭 실행");

                // 경고 상태 끄기 (비상 상황으로 넘어갔으므로)
                isWarningActive = false;

                // 비상벨 누르기
//                emergency = true;
//                emergencyStartTime = System.currentTimeMillis();
//                onActivityResult(REQUEST_CODE_FOR_EMERGENCY, 4, new Intent());
                buttonEmergency.callOnClick();
            };

            // 3. 타이머 시작 (10초 뒤 발동)
            missingAlertHandler.postDelayed(finalAlertRunnable, 10000);
        });
    }

    private void setupEmergencyCancelButtonListener() {
        if (emergencyCancelButtonListener == null) {
            emergencyCancelButtonListener = new ValueEventListener() {
                @Override
                public void onDataChange(@Nonnull DataSnapshot snapshot) {
                    if (snapshot.getValue(Boolean.class)) {
                        emergencyEnded();
                    }
                }

                @Override
                public void onCancelled(@Nonnull DatabaseError error) {

                }
            };
        }
    }

    private void loadGuardianList() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String json = sharedPreferences.getString("guardian_list", null);
        if (json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<UserInfo>>() {
            }.getType();
            List<UserInfo> savedGuardianList = gson.fromJson(json, type);
            if (savedGuardianList != null && !savedGuardianList.isEmpty()) {
                guardians.clear();
                guardians.addAll(savedGuardianList);
            }
        } else {
            guardians.clear();
        }
    }

    private void loadAvgHeartRate() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        stableHeartRateAvg = sharedPreferences.getFloat("avg_heart_rate", 0);
    }

    private void showNotGuardianDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("보호자 설정").setMessage("보호자가 없습니다! 보호자를 설정해주세요");
        builder.setPositiveButton("확인", (dialog, id) -> {
            dialog.dismiss();
            buttonMenu.callOnClick();
        });
        builder.show();
    }

    private void showNotHeartRateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("안정심박수").setMessage("안정심박수가 없습니다. 측정해주세요!");
        builder.setPositiveButton("확인", (dialog, id) -> {
            dialog.dismiss();
            buttonMenu.callOnClick();
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
                    buttonEmergency.callOnClick();
                    // 10초 후 다시 허용ㅎ
                    new Handler(Looper.getMainLooper())
                            .postDelayed(() -> calling.set(false), 10_000);
                })
        );
    }


    @Override
    public void onStart() {
        super.onStart();
        robot.addOnRobotReadyListener(this);
        robot.addOnBeWithMeStatusChangedListener(this);
        robot.addOnTelepresenceEventChangedListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        robot.removeOnRobotReadyListener(this);
        robot.removeOnBeWithMeStatusChangedListener(this);
        robot.removeOnTelepresenceEventChangedListener(this);
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_FOR_CAMERA);
            } else {
                startCamera();
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_FOR_AUDIO);
            } else {
                setupVoiceDetector();
                if (voiceDetector != null) {
                    voiceDetector.start();
                }
            }
            try {
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //로봇이 따라가기 상태가 변할때 실행하는 메서드
    @Override
    public void onBeWithMeStatusChanged(String status) {
        switch (status) {
            case ABORT:             //따라가기 중단됐을때
                break;
            case CALCULATING:       //따라가는중에 장애물을 발견해서 돌아가는길 계산중일때
                break;
            case SEARCH:            //따라가기모드 실행되서 사람을 찾고있을때
                robot.beWithMe(SpeedLevel.SLOW);
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
    public void onTelepresenceEventChanged(@Nonnull CallEventModel model) {
        if (emergency) {
            if (model.getState() == 1) {
                if (System.currentTimeMillis() - startCallTime > 52000) {
                    calling.set(false);
                    emergencyEnded();
                } else {
                    callEmergency();
                }
            }
        }
    }

    public void callEmergency() {
        //보호자에게전화걸기
        //전화가능상태를 가져와서 전화가능한사람먼저 통화
        //전화거는거는 테미앱, 테미센터(데스크탑)두가지가 존재
        List<MemberStatusModel> statusList = robot.getMembersStatus();
        statusMap.clear();
        for (MemberStatusModel status : statusList) {
            statusMap.put(status.getMemberId(), status);
        }
        if (guardians.size() == calledGuardians.size()) {
            calledGuardians.clear();
        }
        for (UserInfo guardian : guardians) {
            MemberStatusModel status = statusMap.get(guardian.getUserId());
            if (status.getMobileStatus() == 0 && !calledGuardians.contains(guardian)) {
                calling.compareAndSet(false, true);
                startCallTime = System.currentTimeMillis();
                robot.startTelepresence("", guardian.getUserId(), Platform.MOBILE);
                calledGuardians.add(guardian);
                break;
            } else if (status.getCenterStatus() == 0 && !calledGuardians.contains(guardian)) {
                calling.compareAndSet(false, true);
                startCallTime = System.currentTimeMillis();
                robot.startTelepresence("", guardian.getUserId(), Platform.TEMI_CENTER);
                calledGuardians.add(guardian);
                break;
            }
            //이곳은 남은 보호자중 통화할수있는 사람이 아무도 없을때
            if (calledGuardians.size() == 0) {
                TtsRequest ttsRequest = TtsRequest.create("연락할수있는 보호자가 없습니다", false);
                robot.speak(ttsRequest);
            } else {
                calledGuardians.clear();
                callEmergency();
            }
        }
    }

    private void emergencyEnded() {
        if (emergency) {
            emergency = false;
            calledGuardians.clear();
            statusMap.clear();
            emergencyRef.setValue(false);
            emergencyCancelRef.setValue(false);
            emergencyCancelRef.removeEventListener(emergencyCancelButtonListener);
            emergencyHeartCount = 0;
            EmergencyCancelActivity emergencyCancelActivity = EmergencyCancelActivity.getInstance();
            if (emergencyCancelActivity != null) {
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

    @Override
    public void onDataChange(DataSnapshot snapshot) {
        HeartRateData value = snapshot.getValue(HeartRateData.class);
        long checkTime = convertDateStringToTimestamp(value.getCheckDate());
        if (heartRateCheckTime < checkTime) {
            if (!isExercise && !isSleep) {
                if (value.getHeartRate() > stableHeartRateAvg * 1.35 || value.getHeartRate() < stableHeartRateAvg * 0.65) {
                    if (!calling.get()) {
                        if (emergencyHeartCount >= EMERGENCY_COUNT_MAX) {
                            emergency = true;
                            emergencyHeartCount = 0;
                            buttonEmergency.callOnClick();
                        } else {
                            emergencyHeartCount++;
                        }
                    }
                } else {
                    emergencyHeartCount = 0;
                    if (EmergencyCancelActivity.isRunning) {
                        emergencyEnded();
                    }
                }
            } else {
                emergencyHeartCount = 0;
            }
        }
    }

    @Override
    public void onCancelled(DatabaseError error) {
        Log.e("firebase", error.getMessage());
    }
}