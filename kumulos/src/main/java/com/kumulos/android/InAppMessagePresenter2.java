package com.kumulos.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class InAppMessagePresenter2 implements KumulosInitProvider.AppStateChangedListener {

    private static final String TAG = InAppMessagePresenter2.class.getName();

    private static final String HOST_MESSAGE_TYPE_CLOSE_MESSAGE = "CLOSE_MESSAGE";

    private final List<InAppMessage> messageQueue = new ArrayList<>();
    private final Context context;
    private final InAppMessageView view;
    private final Handler handler = new Handler(Looper.getMainLooper());

    InAppMessagePresenter2(Context context) {
        this.context = context.getApplicationContext();
        KumulosInitProvider.getAppStateWatcher().registerListener(this);
        view = new InAppMessageView(this);
    }

    @Override
    public void appEnteredForeground() {
        if (!KumulosInApp.isInAppEnabled()) {
            return;
        }
        // TODO handle tickle id? (handled in activityAvailable?)
        InAppMessageService.readAndPresentMessages(context, true, null);
    }

    @Override
    public void activityAvailable(Activity currentActivity) {
        if (!KumulosInApp.isInAppEnabled()) {
            return;
        }

        view.attach(currentActivity);

        Intent i = currentActivity.getIntent();
        int tickleId = i.getIntExtra(PushBroadcastReceiver.EXTRAS_KEY_TICKLE_ID, -1);

        if (-1 != tickleId) {
            InAppMessageService.readAndPresentMessages(context, false, tickleId);
        }

        presentMessageToClient();
    }

    @Override
    public void activityUnavailable(Activity activity) {
        view.detach(activity);
    }

    // TODO, need some detach to clean up when app is terminated / GC in bg?

    @Override
    public void appEnteredBackground() {
        // ?
    }

    @AnyThread
    synchronized void presentMessages(List<InAppMessage> itemsToPresent, List<Integer> tickleIds) {
        handler.post(() -> presentMessagesOnUiThread(itemsToPresent, tickleIds));
    }

    @UiThread
    private void presentMessageToClient() {
        if (messageQueue.isEmpty()) {
            view.close();
            return;
        }

        view.setSpinnerVisibility(View.VISIBLE);
        view.display(Objects.requireNonNull(getCurrentMessage()));
    }

    @UiThread
    void cancelCurrentPresentationQueue() {
        messageQueue.clear();
        view.close();
    }

    @UiThread
    void clientReady() {
        presentMessageToClient();
    }

    @UiThread
    void messageOpened() {
        view.setSpinnerVisibility(View.GONE);

        InAppMessage message = getCurrentMessage();
        if (null == message) {
            return;
        }

        InAppMessageService.handleMessageOpened(context, message);
    }

    @UiThread
    void messageClosed() {
        if (messageQueue.isEmpty()) {
            return;
        }

        messageQueue.remove(0);

        presentMessageToClient();
    }

    @UiThread
    void closeCurrentMessage() {
        if (messageQueue.isEmpty()) {
            return;
        }

        InAppMessage message = getCurrentMessage();
        if (null == message) {
            return;
        }

        view.sendToClient(HOST_MESSAGE_TYPE_CLOSE_MESSAGE, null);
        InAppMessageService.handleMessageClosed(context, message);
    }

    @UiThread
    private void presentMessagesOnUiThread(List<InAppMessage> itemsToPresent, List<Integer> tickleIds) {
        if (itemsToPresent.isEmpty()) {
            return;
        }

        List<InAppMessage> oldQueue = new ArrayList<>(messageQueue);

        addMessagesToQueue(itemsToPresent);
        moveTicklesToFront(tickleIds);
        maybeRefreshFirstMessageInQueue(oldQueue);

        presentMessageToClient();
    }

    @UiThread
    private void maybeRefreshFirstMessageInQueue(List<InAppMessage> oldQueue) {
        if (oldQueue.isEmpty()) {
            return;
        }

        InAppMessage oldFront = oldQueue.get(0);

        if (oldFront.getInAppId() != messageQueue.get(0).getInAppId()) {
            presentMessageToClient();
        }
    }

    @UiThread
    private void moveTicklesToFront(List<Integer> tickleIds) {
        if (tickleIds == null || tickleIds.isEmpty()) {
            return;
        }

        for (Integer tickleId : tickleIds) {
            for (int i = 0; i < messageQueue.size(); i++) {
                InAppMessage next = messageQueue.get(i);
                if (tickleId == next.getInAppId()) {
                    messageQueue.remove(i);
                    messageQueue.add(0, next);

                    break;
                }
            }
        }
    }

    @UiThread
    private void addMessagesToQueue(List<InAppMessage> itemsToPresent) {
        for (InAppMessage messageToAppend : itemsToPresent) {
            boolean exists = false;
            for (InAppMessage messageFromQueue : messageQueue) {
                if (messageToAppend.getInAppId() == messageFromQueue.getInAppId()) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                messageQueue.add(messageToAppend);
            }
        }
    }

    @Nullable
    @UiThread
    InAppMessage getCurrentMessage() {
        if (messageQueue.isEmpty()) {
            return null;
        }

        return messageQueue.get(0);
    }
}
