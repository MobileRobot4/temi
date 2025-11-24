package com.example.temicommunication;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

//import com.robotemi.sdk.Robot;
//import com.robotemi.sdk.UserInfo;

import java.util.ArrayList;
import java.util.List;

import com.example.temicommunication.UserInfo;

public class GuardianActivity extends AppCompatActivity {

    List<UserInfo> guardians;
    List<UserInfo> users;
    RecyclerView recyclerView;
    RecyclerAdapter recyclerAdapter;
    Button buttonConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guaridan);
        Intent receivedIntent = getIntent();
        guardians = receivedIntent.getParcelableArrayListExtra("guardians");
        users = receivedIntent.getParcelableArrayListExtra("users");
        recyclerView = findViewById(R.id.recyclerViewGuardian);
        recyclerAdapter = new RecyclerAdapter();
        recyclerView.setAdapter(recyclerAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerAdapter.setGuardianList(new ArrayList<>(guardians));
        recyclerAdapter.setUserInfos(new ArrayList<>(users));
        buttonConfirm = findViewById(R.id.buttonConfirm);
        buttonConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent resultIntent = new Intent();
                resultIntent.putParcelableArrayListExtra("guardians",new ArrayList<>(recyclerAdapter.getGuardianList()));
                setResult(Activity.RESULT_OK,resultIntent);
                finish();
            }
        });
    }

}
