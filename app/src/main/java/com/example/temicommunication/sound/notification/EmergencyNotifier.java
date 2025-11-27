package com.example.temicommunication.sound.notification;

import android.content.Context;

import com.example.temicommunication.sound.Emergency;
import com.example.temicommunication.sound.EmergencyType;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class EmergencyNotifier {

    private final Context context;
    private final DatabaseReference reference;

    private static final String REF_PATH = "emergency";

    public EmergencyNotifier(Context context, String temiId) {
        this.context = context.getApplicationContext();
        this.reference = FirebaseDatabase.getInstance()
                .getReference(REF_PATH)
                .child(temiId);
    }

    public void sendVoiceHelp() {
        long now = System.currentTimeMillis();
        Emergency emergency = new Emergency(EmergencyType.VOICE_TYPE, now);

        reference.setValue(emergency)
                .addOnSuccessListener(unused ->
//                        Toast.makeText(context, "긴급 신호 전송 완료", Toast.LENGTH_SHORT).show()
                                System.out.println("긴급 신호 전송 완료")
                )
                .addOnFailureListener(e ->
//                        Toast.makeText(context, "Firebase 전송 실패: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                System.out.println("Firebase 전송 실패")
                );
    }


}
