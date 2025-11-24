package com.example.temicommunication;


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
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.model.MemberStatusModel;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
implements OnRobotReadyListener {

    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    DatabaseReference analysisRef = firebaseDatabase.getReference("Analysis");
    DatabaseReference heartRateRef = firebaseDatabase.getReference("HeartRate");
    DatabaseReference sensorRef = firebaseDatabase.getReference("Sensor");
    Button buttonCheckHeart;
    Button buttonExercise;
    Button buttonSleep;
    Button buttonEmergency;
    Button buttonSetGuardian;
    float[] stableHeartRate = new float[20];
    float stableHeartRateAvg = 0;
    boolean checkHeartRate = false;
    boolean isExercise = false;
    boolean isSleep = false;
    int checkHeartRateCount = 0;
    LocalDateTime checkHeartRateStartDate;
    LocalDateTime heartRateCheckTime;
    boolean emergency = false;
    int emergencyCount = 0;
    final int emergencyCountMax = 30;
    boolean hideEmergengyButton = true;
    Robot robot;
    List<UserInfo> guardians = new ArrayList<>();

    private static final int REQUEST_CODE_FOR_GUARDIAN = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        robot = Robot.getInstance();
        loadGuardianList();
        heartRateCheckTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        buttonCheckHeart = (Button)findViewById(R.id.buttonCheckHeart);
        buttonCheckHeart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonCheckHeart.getText().equals("심박수측정하기(약20초)")){
                    buttonCheckHeart.setText("안정된 상태입니까?");
                } else if(buttonCheckHeart.getText().equals("안정된 상태입니까?")){
                    checkHeartRate = true;
                    checkHeartRateStartDate = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
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
                //여기에 응급상황발생시의 로직 생성
                TtsRequest ttsRequest = TtsRequest.create("응급상황이 의심됩니다. 응급상황이 아닐경우 기기아래 버튼을 클릭하거나 화면의 버튼을 클릭해주세요",false);
                robot.speak(ttsRequest);
                //보호자에게전화걸기
                //전화가능상태를 가져와서 전화가능한사람먼저 통화
                //전화거는거는 테미앱, 테미센터(데스크탑)두가지가 존재
                List<MemberStatusModel> statusList = robot.getMembersStatus();
                Map<String, MemberStatusModel> statusMap = new HashMap<>();
                for(MemberStatusModel status : statusList){
                    statusMap.put(status.getMemberId(), status);
                }
                for(UserInfo user : guardians) {
                    MemberStatusModel status = statusMap.get(user.getUserId());
                    if(status.getMobileStatus()==0){
                        Log.d("전화",robot.startTelepresence("보호자통화",user.getUserId(),Platform.MOBILE));
                    } else if(status.getCenterStatus() == 0){
                        robot.startTelepresence("보호자통화",user.getUserId(),Platform.TEMI_CENTER);
                    }
                }
            }
        });
        buttonSetGuardian = findViewById(R.id.buttonSetGuardian);
        buttonSetGuardian.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(guardians.size() == 0) {
                    guardians.add(new UserInfo("test_user_1","테스트1","http://example.com/profile.jpg",0));
                }
                Intent intent = new Intent (MainActivity.this,GuardianActivity.class);
                ArrayList<UserInfo> guardianList = new ArrayList<>(guardians);
                intent.putParcelableArrayListExtra("guardians",guardianList);
                ArrayList<UserInfo> users = new ArrayList<>(/*robot.getAllContact()*/);
                if(users.size() == 0) {
                    users.add(guardians.get(0));
                    users.add(new UserInfo("test_user_2","테스트2","http://example.com/profile2.jpg",2));
                }
                intent.putParcelableArrayListExtra("users",users);
                startActivityForResult(intent, REQUEST_CODE_FOR_GUARDIAN);
            }
        });
        heartRateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                HeartRateData value = snapshot.getValue(HeartRateData.class);
                Log.d("confirm", value.toString());
                if(heartRateCheckTime.isBefore(LocalDateTime.parse(value.getCheckData()))){
                    if(checkHeartRate){
                        if(checkHeartRateStartDate.isBefore(LocalDateTime.parse(value.getHeartDate()))){
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
                            if(emergencyCount >= emergencyCountMax) {
                                emergency = true;
                                buttonEmergency.callOnClick();
                            } else {
                                emergencyCount+=1;
                            }
                        } else {
                            emergency = false;
                            emergencyCount = 0;
                        }
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
        builder.setTitle("보호자 설정")
                .setMessage("보호자가 없습니다! 보호자를 설정해주세요");
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
    }

    @Override
    public void onRobotReady(boolean isReady){
        if(isReady){
            try{
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
            } catch(PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}