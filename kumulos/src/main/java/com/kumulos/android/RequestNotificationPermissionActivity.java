package com.kumulos.android;

import android.Manifest;
import android.app.Activity;
import android.os.Build;

import androidx.annotation.NonNull;

public class RequestNotificationPermissionActivity extends Activity {
    private static final int REQ_CODE = 1;
    private static final String TAG = RequestNotificationPermissionActivity.class.getName();

    @Override
    protected void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            finish();
            return;
        }

        boolean shouldShow = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
        Kumulos.log(TAG, "Should show perms req rationale? " + (shouldShow ? "YES" : "NO"));

        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }

}
