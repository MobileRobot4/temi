package com.example.temicommunication.log;

import com.example.temicommunication.detect.PersonDetector;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class FirebaseLogger {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static void logDetection(PersonDetector.DetectionResult result) {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", new Date());
        data.put("isHuman", result.isHuman);
        data.put("maxTemp", result.maxTemp);
        data.put("hotPixelRatio", result.hotPixelRatio);

        db.collection("detections").add(data);
    }
}
