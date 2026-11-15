package com.shobarampur.sportsfamily;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ক্লাউডের 'notify' নোড প্রতি ১৫ মিনিটে চেক করে — নতুন খবর অ্যাপের বাইরে
 * (সিস্টেম নোটিফিকেশন বারে) দেখায়। একাধিক অপেক্ষমাণ খবর থাকলে সবগুলো
 * পুরোনো-থেকে-নতুন ক্রমে পাঠায় (এক চালে সর্বোচ্চ ৫টি)। ইন্টারনেট না থাকলে
 * নীরবে পরের চালে আবার চেষ্টা করে — খবর নষ্ট হয় না।
 */
public class NoticePollerWorker extends Worker {

    private static final String CHANNEL_ID = "shobarampur_notices";
    private static final String[] DB_URLS = {
            "https://shovarampur-sports-default-rtdb.firebaseio.com/notify.json",
            "https://shovarampur-sports-default-rtdb.asia-southeast1.firebasedatabase.app/notify.json"
    };

    public NoticePollerWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        for (String u : DB_URLS) {
            try {
                JSONObject root = fetchJson(u);
                if (root == null) continue;
                SharedPreferences sp = getApplicationContext()
                        .getSharedPreferences("srf_notify", Context.MODE_PRIVATE);
                String seen = sp.getString("seenIds", "");
                List<long[]> pending = new ArrayList<>(); // {at, index-into-keys}
                List<String> keys = new ArrayList<>();

                if (root.has("title") || root.has("body")) {
                    /* পুরোনো একক-ফরম্যাট: {title,body,at} */
                    keys.add(null);
                    pending.add(new long[]{root.optLong("at", 0), 0});
                } else {
                    java.util.Iterator<String> it = root.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject item = root.optJSONObject(k);
                        if (item == null) continue;
                        long at = item.optLong("at", 0);
                        if (at <= 0 || seen.contains(k)) continue;
                        keys.add(k);
                        pending.add(new long[]{at, keys.size() - 1});
                    }
                }
                if (pending.isEmpty()) return Result.success();
                Collections.sort(pending, (a, b) -> Long.compare(a[0], b[0]));

                int shown = 0;
                StringBuilder seenNew = new StringBuilder(seen);
                for (long[] pd : pending) {
                    if (shown >= 5) break;
                    String k = keys.get((int) pd[1]);
                    JSONObject item = (k == null) ? root : root.optJSONObject(k);
                    if (item == null) continue;
                    long at = pd[0];
                    if (System.currentTimeMillis() - at > 24 * 3600_000L) continue; /* ২৪ ঘণ্টার পুরোনো বাদ */
                    String title = item.optString("title", "শোভারামপুর স্পোর্টস ফ্যামিলি");
                    String body = item.optString("body", "");
                    show(title, body, at);
                    if (k != null) {
                        if (seenNew.length() > 0) seenNew.append(",");
                        seenNew.append(k);
                    }
                    shown++;
                }
                if (shown > 0) {
                    String sv = seenNew.toString();
                    if (sv.length() > 4000) sv = sv.substring(sv.length() - 4000); /* তালিকা ছোট রাখা */
                    sp.edit().putString("seenIds", sv).apply();
                }
                return Result.success();
            } catch (Exception ignored) {
            }
        }
        return Result.retry();
    }

    private JSONObject fetchJson(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Cache-Control", "no-cache");
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            String t = sb.toString().trim();
            if (t.isEmpty() || "null".equals(t)) return null;
            return new JSONObject(t);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void show(String title, String body, long when) {
        Context ctx = getApplicationContext();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "নোটিশ ও টুর্নামেন্ট",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("নোটিশ, ম্যাচ ও ফলাফলের নোটিফিকেশন");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 1, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(ctx, CHANNEL_ID)
                : new android.app.Notification.Builder(ctx);
        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setWhen(when);

        nm.notify((int) (when % 1000000000L), b.build());
    }
}
