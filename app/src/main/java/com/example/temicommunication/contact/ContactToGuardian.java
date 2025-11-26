package com.example.temicommunication.contact;

import android.content.Context;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.UserInfo;

import java.util.List;

public class ContactToGuardian {

    private final Context context;

    public ContactToGuardian(Context context) {
        this.context = context.getApplicationContext();
    }

    public void callGuardian(String userName) {
        Robot temi = Robot.getInstance();
        UserInfo guardian = findGuardian(userName, temi);

        if (guardian == null) {
//            Toast.makeText(this.context, "보호자 정보를 찾지 못했습니다.", Toast.LENGTH_LONG).show();
            System.out.println("보호자 정보를 찾지 못했습니다.");
            return;
        }

        temi.startTelepresence(
                guardian.getName(),
                guardian.getUserId()
        );

    }

    private UserInfo findGuardian(String userName, Robot temi) {
        List<UserInfo> contacts = temi.getAllContact();
        if (contacts == null) {
            System.out.println("Temi 연락처가 비어 있습니다.");
            return null;
        }

        for (UserInfo user : contacts) {
            if (user.getName().equals(userName)) {
                return user;
            }
        }
        return null;
    }
}
