package com.example.temicommunication;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.UserInfo;
import com.robotemi.sdk.constants.Platform;
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.listeners.OnTelepresenceStatusChangedListener;
import com.robotemi.sdk.model.MemberStatusModel;
import com.robotemi.sdk.telepresence.CallState;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressLint("NewApi")
public class MainActivity extends AppCompatActivity
        implements OnRobotReadyListener, OnBeWithMeStatusChangedListener {

    private ValueEventListener emergencyCancelButtonListener;
    private OnTelepresenceStatusChangedListener callStatusListener;
    private static final int REQUEST_CODE_FOR_GUARDIAN = 1001;
    private static final int REQUEST_CODE_FOR_EMERGENCY = 1002;
    private static final int EMERGENCY_COUNT_MAX = 2; //파이어베이스주기가 약5초로확인됨 실제 초수는 곱하기5해줘야됨
    private static final long EMERGENCY_CANCEL_WAIT_TIME = 8;

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
    float[] stableHeartRate = new float[20];
    float stableHeartRateAvg = 100;
    boolean checkHeartRate = false;
    boolean isExercise = false;
    boolean isSleep = false;
    boolean emergency = false;
    boolean hideEmergengyButton = true;
    int checkHeartRateCount = 0;
    int emergencyHeartCount = 0;
    long checkHeartRateStartDate;
    long heartRateCheckTime;
    long emergencyStartTime;
    Robot robot;
    List<UserInfo> guardians = new ArrayList<>();
    List<UserInfo> calledGuardians = new ArrayList<>();
    Map<String, MemberStatusModel> statusMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        robot = Robot.getInstance();
        loadGuardianList();
        setupEmergencyCancelButtonListener();
        setupCallStatusListener();
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
        if(hideEmergengyButton){
            buttonEmergency.setVisibility(View.GONE);
        } else {
            buttonEmergency.setVisibility(View.VISIBLE);
        }
        buttonEmergency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
        buttonSetGuardian = findViewById(R.id.buttonSetGuardian);
        buttonSetGuardian.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if(guardians.size() == 0) {
//                    guardians.add(new UserInfo("test_user_1","테스트1","https://temi-media-public.s3.us-east-1.amazonaws.com/profile-images/3c7d47258ab81fa7b96b7350be84d1bc/2e2f07cd-cce1-4ed4-9fbd-7b2266963dba.jpeg",0));
//                }
                Intent intent = new Intent (MainActivity.this,GuardianActivity.class);
                ArrayList<UserInfo> guardianList = new ArrayList<>(guardians);
                intent.putParcelableArrayListExtra("guardians",guardianList);
                ArrayList<UserInfo> users = new ArrayList<>(robot.getAllContact());
//                if(users.size() == 0) {
//                    users.add(guardians.get(0));
//                    for(int i = 0; i<20 ; i++) {
//                        users.add(new UserInfo("test_user_" + i,"테스트" + i,"https://temi-media-public.s3.us-east-1.amazonaws.com/profile-images/f90924b852d82738de251e10956cd2ba/c7183245-e8d6-4d47-ac1b-30563c02907c.jpeg",2));
//                    }
//                }
                intent.putParcelableArrayListExtra("users",users);
                startActivityForResult(intent, REQUEST_CODE_FOR_GUARDIAN);
            }
        });
        heartRateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                HeartRateData value = snapshot.getValue(HeartRateData.class);
                Log.d("confirm", value.toString());
                long checkTime = convertDateStringToTimestamp(value.getCheckDate());
                if(heartRateCheckTime < checkTime){
                    if(emergency){
                        if(emergencyStartTime + (EMERGENCY_CANCEL_WAIT_TIME * 1000) < checkTime){
                            callEmergency();
                            emergencyStartTime = emergencyStartTime + (1000L * 365 * 24 * 60 * 60 * 1000);
                        }
                    }
                    if(checkHeartRate){
                        long heartDateTimestamp = convertDateStringToTimestamp(value.getHeartDate());
                        if(checkHeartRateStartDate < heartDateTimestamp){
                            stableHeartRate[checkHeartRateCount++] = value.getHeartRate();
                            if(checkHeartRateCount == 21){
                                checkHeartRate = false;
                                buttonCheckHeart.setText("심박수측정완료");
                                stableHeartRateAvg = 0;
                                for(int i=0; i<20; i++) {
                                    stableHeartRateAvg+=stableHeartRate[i];
                                }
                                stableHeartRateAvg/=20;
                                try {
                                    Thread.sleep(3000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    e.printStackTrace();
                                }
                                buttonCheckHeart.setText("심박수측정하기(약20초)");
                            } else {
                                buttonCheckHeart.setText("심박수측정중...[" + checkHeartRateCount + "/20]");
                            }
                        } else {
                            Log.d("checkHeartRate","심박수데이터 최신화 안됨 : " + LocalDateTime.now(ZoneId.of("Asia/Seoul")));
                        }
                    } else if(!isExercise && !isSleep){
                        if(value.getHeartRate()>stableHeartRateAvg*1.35 || value.getHeartRate()<stableHeartRateAvg*0.65) {
                            if(emergencyHeartCount >= EMERGENCY_COUNT_MAX) {
                                emergency = true;
                                emergencyHeartCount = 0;
                                buttonEmergency.callOnClick();
                            } else {
                                emergencyHeartCount ++;
                                Log.d("emergencyHeartCount", "카운트 : " + emergencyHeartCount);
                            }
                        } else {
                            emergencyHeartCount = 0;
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
                Log.d("analysis", value.toString());
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
                Log.d("sensor", value.toString());
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
                        Log.d("guardians", "보호자받음 : " + guardians.toString());
                    }
                }
            } else if(resultCode == Activity.RESULT_CANCELED){
                Log.d("guardians","취소되었습니다");
            }
        } else if(requestCode == REQUEST_CODE_FOR_EMERGENCY){
            if(resultCode != 4) {
                emergencyEnded();
            } else {
                callEmergency();
            }
        }
    }

    private void saveGuardianList() {
        Gson gson = new Gson();
        String json = gson.toJson(guardians);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("guardian_list",json);
        editor.apply();
        Log.d("guardians", "로컬저장완료 : " + guardians.toString());
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

    private void setupCallStatusListener(){
        if(callStatusListener == null) {
            callStatusListener = new OnTelepresenceStatusChangedListener("") {
                @Override
                public void onTelepresenceStatusChanged(@org.jetbrains.annotations.NotNull CallState callState) {
                    switch (callState.getState()){
                        case ENDED:                 //전화끝났을때
                            emergencyEnded();
                            break;
                        case DECLINED:              //전화거절당했을때
                            callEmergency();
                            break;
                        case NOT_ANSWERED:          //전화걸고 상대방이 전화를 안받을때
                            callEmergency();
                            break;
                        case BUSY:                  //전화걸려고했는데 상대방이 BUSY상태일때
                            callEmergency();
                            break;
                        case STARTED:               //상대방이전화수락하고 전화가 시작했을때
                            break;
                        case INITIALIZED:           //전화걸고 상대방이 반응하기전까지
                            break;
                        case POOR_CONNECTION:       //연결이슈로 전화 안될때
                            callEmergency();
                            break;
                        case CANT_JOIN:             //Cannot join the call - 전화에 합류할수 없을때?
                            callEmergency();
                            break;
                        default:
                            break;
                    }
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
                Log.d("guardians", "로컬에서 불러옴 : " + guardians.toString());
            }
        } else {
            guardians.clear();
            Log.d("guardians", "로컬에 데이터 없음");
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


    @Override
    public void onStart() {
        super.onStart();
        robot.addOnRobotReadyListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        robot.removeOnRobotReadyListener(this);
        robot.removeOnBeWithMeStatusChangedListener(this);
    }

    @Override
    public void onRobotReady(boolean isReady){
        if(isReady){
            robot.addOnBeWithMeStatusChangedListener(this);
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
        robot.addOnTelepresenceStatusChangedListener(callStatusListener);
        for(UserInfo guardian : guardians) {
            MemberStatusModel status = statusMap.get(guardian.getUserId());
            if(status.getMobileStatus()==0 && !calledGuardians.contains(guardian)){
                robot.startTelepresence("보호자통화",guardian.getUserId(),Platform.MOBILE);
                calledGuardians.add(guardian);
                break;
            } else if(status.getCenterStatus() == 0 && !calledGuardians.contains(guardian)){
                robot.startTelepresence("보호자통화",guardian.getUserId(),Platform.TEMI_CENTER);
                calledGuardians.add(guardian);
                break;
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
            robot.removeOnTelepresenceStatusChangedListener(callStatusListener);
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