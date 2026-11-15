package com.shobarampur.sportsfamily;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * ফায়ারবেস পুশ নোটিফিকেশন — FIREBASE_SETUP.md অনুযায়ী google-services.json
 * বসালেই এডমিন ফায়ারবেস কনসোল থেকে সবার ফোনে নোটিশ পাঠাতে পারবেন।
 * ডেটা পেলোড: {"title": "...", "body": "..."}
 */
public class MyFirebaseService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "shobarampur_notices";

    @Override
    public void onNewToken(String token) {
        // ভবিষ্যতে সার্ভার থেকে পাঠানোর জন্য টোকেন ফোনে জমা রাখা হলো
        SharedPreferences sp = getSharedPreferences("fcm", Context.MODE_PRIVATE);
        sp.edit().putString("token", token).apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String title = null, body = null;
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        if (message.getData() != null) {
            if (message.getData().containsKey("title")) title = message.getData().get("title");
            if (message.getData().containsKey("body")) body = message.getData().get("body");
        }
        if (title != null || body != null) {
            show(title == null ? "শোভারামপুর স্পোর্টস ফ্যামিলি" : title,
                    body == null ? "" : body);
        }
    }

    private void show(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "নোটিশ ও টুর্নামেন্ট",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("নোটিশ, ম্যাচ ও ফলাফলের নোটিফিকেশন");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, CHANNEL_ID)
                : new android.app.Notification.Builder(this);
        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify((int) System.currentTimeMillis(), b.build());
    }
}
