package com.example.temicommunication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.temicommunication.Data.HeartRateData;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.robotemi.sdk.UserInfo;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MenuActivity extends AppCompatActivity implements ValueEventListener{

    final int REQUEST_CODE_FOR_GUARDIAN = 1001;

    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    DatabaseReference heartRateRef = firebaseDatabase.getReference("HeartRate");
    Button buttonSetGuardian;
    ImageButton buttonExercise;
    ImageButton buttonSleep;
    Button buttonCheckHeart;
    ImageButton buttonClose;
    List<UserInfo> guardians = new ArrayList<>();

    float stableHeartRateAvg = 0;
    float[] stableHeartRate = new float[20];
    long heartRateCheckTime;
    long checkHeartRateStartDate;
    int checkHeartRateCount;
    boolean checkHeartRate;
    boolean isExercise;
    boolean isSleep;
    boolean checkHeartRateResult = false;
    boolean exerciseResult = false;
    boolean sleepResult = false;
    boolean guardianResult = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        Intent receivedIntent = getIntent();
        heartRateCheckTime = System.currentTimeMillis();
        buttonCheckHeart = (Button)findViewById(R.id.buttonCheckHeart);
        buttonCheckHeart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonCheckHeart.getText().equals("심박수측정하기        (약20초)")){
                    buttonCheckHeart.setText("안정된 상태입니까?");
                    buttonCheckHeart.setTextSize(TypedValue.COMPLEX_UNIT_SP,32f);
                } else if(buttonCheckHeart.getText().equals("안정된 상태입니까?")){
                    checkHeartRate = true;
                    checkHeartRateStartDate = System.currentTimeMillis();
                    checkHeartRateCount = 0;
                    buttonCheckHeart.setText("심박수측정중...[0/20]");
                    buttonCheckHeart.setTextSize(TypedValue.COMPLEX_UNIT_SP,43f);
                    heartRateRef.addValueEventListener(MenuActivity.this);
                }
            }
        });
        buttonExercise = findViewById(R.id.buttonExercise);
        buttonExercise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonExercise.getContentDescription().toString().equals("운동")){
                    isExercise = true;
                    buttonExercise.setImageResource(R.drawable.exercise_end);
                    buttonExercise.setContentDescription("운동종료");
                } else if(buttonExercise.getContentDescription().toString().equals("운동종료")){
                    isExercise = false;
                    buttonExercise.setImageResource(R.drawable.exercise);
                    buttonExercise.setContentDescription("운동");
                }
                exerciseResult = true;
            }
        });
        buttonSleep = findViewById(R.id.buttonSleep);
        buttonSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonSleep.getContentDescription().toString().equals("수면")){
                    isSleep = true;
                    buttonSleep.setImageResource(R.drawable.sleep_end);
                    buttonSleep.setContentDescription("수면종료");
                } else if(buttonSleep.getContentDescription().toString().equals("수면종료")){
                    isSleep = false;
                    buttonSleep.setImageResource(R.drawable.sleep);
                    buttonSleep.setContentDescription("수면");
                }
                sleepResult = true;
            }
        });
        buttonSetGuardian = findViewById(R.id.buttonGuardian);
        buttonSetGuardian.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadGuardianList();
                Intent intent = new Intent (MenuActivity.this,GuardianActivity.class);
                ArrayList<UserInfo> guardianList = new ArrayList<>(guardians);
                intent.putParcelableArrayListExtra("guardians",guardianList);
                intent.putParcelableArrayListExtra("users",receivedIntent.getParcelableArrayListExtra("users"));
                startActivityForResult(intent, REQUEST_CODE_FOR_GUARDIAN);
            }
        });
        buttonClose = findViewById(R.id.buttonClose);
        buttonClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                if(sleepResult){
                    intent.putExtra("sleep",isSleep);
                }
                if(exerciseResult){
                    intent.putExtra("exercise",isExercise);
                }
                if(checkHeartRateResult){
                    saveAvgHeartRate();
                }
                setResult(new ResultCodeBuilder()
                        .calHeartRate(checkHeartRateResult)
                        .isExercise(exerciseResult)
                        .isSleep(sleepResult)
                        .setGuardian(guardianResult)
                        .build()
                , intent);
                finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE_FOR_GUARDIAN){
            if(requestCode == RESULT_OK){
                guardianResult = true;
                guardians.clear();
                guardians.addAll(data.getParcelableArrayListExtra("guardians"));
                saveGuardianList();
            }
        }
    }

    @Override
    public void onDataChange(DataSnapshot snapshot) {
        HeartRateData value = snapshot.getValue(HeartRateData.class);
        long checkTime = convertDateStringToTimestamp(value.getCheckDate());
        if(heartRateCheckTime < checkTime) {
            if (checkHeartRate) {
                long heartDateTimestamp = convertDateStringToTimestamp(value.getHeartDate());
                if (checkHeartRateStartDate < heartDateTimestamp) {
                    if (value.getHeartRate() > 0) {
                        stableHeartRate[checkHeartRateCount++] = value.getHeartRate();
                    }
                    if (checkHeartRateCount == 20) {
                        checkHeartRate = false;
                        buttonCheckHeart.setText("심박수측정완료");
                        buttonCheckHeart.setTextSize(TypedValue.COMPLEX_UNIT_SP,42f);
                        stableHeartRateAvg = 0;
                        for (int i = 0; i < 20; i++) {
                            stableHeartRateAvg += stableHeartRate[i];
                        }
                        stableHeartRateAvg /= 20;
                        heartRateRef.removeEventListener(this);
                        checkHeartRateResult = true;
                        try {
                            //심박수측정완료버튼을 3초동안 보이기위해 사용
                            Thread.sleep(3000);
                        } catch (Exception e) {
                            Log.e("스레드", e.getMessage());
                        }
                        buttonCheckHeart.setText("심박수측정하기        (약20초)");
                    } else {
                        buttonCheckHeart.setText("심박수측정중...[" + checkHeartRateCount + "/20]");
                    }
                }
            }
        }
    }

    @Override
    public void onCancelled(DatabaseError error) {
        Log.e("heart", error.getMessage());
    }

    private void saveGuardianList() {
        Gson gson = new Gson();
        String json = gson.toJson(guardians);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("guardian_list",json);
        editor.apply();
    }

    private void saveAvgHeartRate() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat("avg_heart_rate", stableHeartRateAvg);
        editor.apply();
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

    private void loadAvgHeartRate(){
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String json = sharedPreferences.getString("avg_heart_rate",null);
        if(json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<Float>() {}.getType();
            Float savedAvgHeartRate = gson.fromJson(json,type);
            if(savedAvgHeartRate != null) {
                stableHeartRateAvg = savedAvgHeartRate;
            }
        } else {
            stableHeartRateAvg = 0;
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
