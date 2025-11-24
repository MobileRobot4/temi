package com.example.temicommunication;

import android.os.Parcel;
import android.os.Parcelable; // Parcelable 인터페이스 import
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo implements Parcelable { // 🟢 1. Parcelable 상속

    // Lombok이 getter/setter, equals, hashCode 등을 자동으로 생성합니다.
    String UserId;
    String name;
    String picUrl;
    int role;

    // 🟢 2. Parcelable 구현을 위한 보조 생성자 (시스템에서 사용)
    protected UserInfo(Parcel in) {
        UserId = in.readString();
        name = in.readString();
        picUrl = in.readString();
        role = in.readInt();
    }

    // 🟢 3. CREATOR 필드 정의 (필수)
    public static final Parcelable.Creator<UserInfo> CREATOR = new Parcelable.Creator<UserInfo>() {
        @Override
        public UserInfo createFromParcel(Parcel in) {
            return new UserInfo(in); // 위에서 정의한 보조 생성자 호출
        }

        @Override
        public UserInfo[] newArray(int size) {
            return new UserInfo[size];
        }
    };

    // 🟢 4. writeToParcel 메서드 오버라이드 (객체를 Parcel에 쓰는 로직)
    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        // 객체의 필드 순서대로 Parcel에 작성합니다.
        parcel.writeString(UserId);
        parcel.writeString(name);
        parcel.writeString(picUrl);
        parcel.writeInt(role);
    }

    // 🟢 5. describeContents 메서드 오버라이드
    @Override
    public int describeContents() {
        return 0; // 보통 0을 반환합니다.
    }
}