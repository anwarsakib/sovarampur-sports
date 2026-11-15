package com.shobarampur.sportsfamily;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.firebase.FirebaseApp;

/**
 * পুরো অ্যাপটা একটা WebView-এর ভেতরে চলে — index.html অ্যাপের ভেতরেই এমবেড করা,
 * তাই ইন্টারনেট ছাড়াও সব ফিচার (হিসাব, সদস্য, টুর্নামেন্ট, নোটিশ) কাজ করে।
 */
public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 101;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);   // হিসাব-সদস্য-টুর্নামেন্টের ডেটা ফোনে জমা থাকে
        st.setDatabaseEnabled(true);
        st.setAllowFileAccess(true);
        st.setAllowContentAccess(true); // গ্যালারি থেকে ছবি বাছা
        st.setLoadWithOverviewMode(true);
        st.setUseWideViewPort(true);
        st.setTextZoom(100);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    startActivityForResult(Intent.createChooser(intent, "ছবি বাছুন"), FILE_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("file:///android_asset/index.html");

        // ফায়ারবেস কনফিগার থাকলে: ব্যবহারের হিসাব (APP_OPEN)
        if (FirebaseApp.initializeApp(this) != null) {
            try {
                com.google.firebase.analytics.FirebaseAnalytics.getInstance(this)
                        .logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.APP_OPEN, null);
            } catch (Exception ignored) {
            }
        }
        // Android 13+ নোটিফিকেশন অনুমতি (ফায়ারবেস থাকুক বা না থাকুক)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        // প্রতি ১৫ মিনিটে ক্লাউডের 'notify' নোড চেক — নতুন খবর থাকলে অ্যাপের বাইরে নোটিফিকেশন
        try {
            androidx.work.PeriodicWorkRequest poll =
                    new androidx.work.PeriodicWorkRequest.Builder(
                            NoticePollerWorker.class, 15, java.util.concurrent.TimeUnit.MINUTES)
                            .setConstraints(new androidx.work.Constraints.Builder()
                                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                    .build())
                            .build();
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "srf_notice_poll", androidx.work.ExistingPeriodicWorkPolicy.KEEP, poll);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
