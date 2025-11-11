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

public class MainActivity extends AppCompatActivity {

    FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    DatabaseReference myRef = firebaseDatabase.getReference();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SwitchCompat switchCompat = (SwitchCompat) findViewById(R.id.switchLed);
        TextView distanceText = (TextView)findViewById(R.id.textDistance);
        Button buttonDistance = (Button)findViewById(R.id.buttonDistance);
        TextView distanceText2 = (TextView)findViewById(R.id.textDistance2);


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
}