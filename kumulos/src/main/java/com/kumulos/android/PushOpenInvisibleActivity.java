package com.kumulos.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/*
    Invisible activity used to forward ACTION_PUSH_OPENED and ACTION_BUTTON_CLICKED to PushBroadcastReceiver.
    This resolves https://developer.android.com/about/versions/12/behavior-changes-12#notification-trampolines
 */
public class PushOpenInvisibleActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.forwardPushOpen(getIntent());

        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        this.forwardPushOpen(getIntent());
        finish();
    }

    // Forwards push opens to broadcast receiver, behaviour of which users can override.
    // ACTION_PUSH_OPENED is not immediately broadcast to the receiver as
    // a) 1 PendingIntent cannot have mixture of activities and broadcast
    // b) notification cannot have multiple content intents
    // Also forwards action button intents as in PushActionHandler users may wish to start an activity.
    private void forwardPushOpen(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        String action = extras.containsKey(PushBroadcastReceiver.EXTRAS_KEY_BUTTON_ID) ?
                PushBroadcastReceiver.ACTION_BUTTON_CLICKED : PushBroadcastReceiver.ACTION_PUSH_OPENED;

        Intent derivedIntent = new Intent(action);
        PushMessage m = intent.getParcelableExtra(PushMessage.EXTRAS_KEY);
        derivedIntent.putExtra(PushMessage.EXTRAS_KEY, m);
        if (action.equals(PushBroadcastReceiver.ACTION_BUTTON_CLICKED)) {
            derivedIntent.putExtra(PushBroadcastReceiver.EXTRAS_KEY_BUTTON_ID, intent.getStringExtra(PushBroadcastReceiver.EXTRAS_KEY_BUTTON_ID));
        }

        derivedIntent.setPackage(this.getPackageName());

        this.sendBroadcast(derivedIntent);
    }
}