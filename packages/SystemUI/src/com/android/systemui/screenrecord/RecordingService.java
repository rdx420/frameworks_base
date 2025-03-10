/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screenrecord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.LongRunning;
import com.android.systemui.settings.UserContextProvider;
import com.android.systemui.statusbar.phone.KeyguardDismissUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * A service which records the device screen and optionally microphone input.
 */
public class RecordingService extends Service implements MediaRecorder.OnInfoListener {
    public static final int REQUEST_CODE = 2;

    private static final int NOTIFICATION_RECORDING_ID = 4274;
    private static final int NOTIFICATION_PROCESSING_ID = 4275;
    private static final int NOTIFICATION_VIEW_ID = 4273;
    private static final String TAG = "RecordingService";
    private static final String CHANNEL_ID = "screen_record";
    private static final String EXTRA_RESULT_CODE = "extra_resultCode";
    private static final String EXTRA_PATH = "extra_path";
    private static final String EXTRA_AUDIO_SOURCE = "extra_useAudio";
    private static final String EXTRA_SHOW_TAPS = "extra_showTaps";
    private static final String EXTRA_SHOW_STOP_DOT = "extra_showStopDot";
    private static final String EXTRA_LOW_QUALITY = "extra_lowQuality";
    private static final String EXTRA_LONGER_DURATION = "extra_longerDuration";

    private static final String ACTION_START = "com.android.systemui.screenrecord.START";
    private static final String ACTION_STOP = "com.android.systemui.screenrecord.STOP";
    private static final String ACTION_STOP_NOTIF =
            "com.android.systemui.screenrecord.STOP_FROM_NOTIF";
    private static final String ACTION_SHOW_DIALOG = "com.android.systemui.screenrecord.SHOW_DIALOG";
    private static final String ACTION_SHARE = "com.android.systemui.screenrecord.SHARE";
    private static final String ACTION_DELETE = "com.android.systemui.screenrecord.DELETE";
    private static final String PERMISSION_SELF = "com.android.systemui.permission.SELF";

    private final RecordingServiceBinder mBinder;
    private final RecordingController mController;
    private final KeyguardDismissUtil mKeyguardDismissUtil;
    private ScreenRecordingAudioSource mAudioSource;
    private boolean mShowTaps;
    private boolean mOriginalShowTaps;
    private ScreenMediaRecorder mRecorder;
    private final Executor mLongExecutor;
    private final UiEventLogger mUiEventLogger;
    private final NotificationManager mNotificationManager;
    private final UserContextProvider mUserContextTracker;

    private boolean mLowQuality;
    private boolean mLongerDuration;
    private boolean mShowStopDot;
    private boolean mIsDotAtRight;
    private boolean mDotShowing;
    private FrameLayout mFrameLayout;
    private WindowManager mWindowManager;

    @Inject
    public RecordingService(RecordingController controller, @LongRunning Executor executor,
            UiEventLogger uiEventLogger, NotificationManager notificationManager,
            UserContextProvider userContextTracker, KeyguardDismissUtil keyguardDismissUtil) {
        mController = controller;
        mLongExecutor = executor;
        mUiEventLogger = uiEventLogger;
        mNotificationManager = notificationManager;
        mUserContextTracker = userContextTracker;
        mWindowManager = (WindowManager) userContextTracker.getUserContext()
                .getSystemService(Context.WINDOW_SERVICE);
        mKeyguardDismissUtil = keyguardDismissUtil;
        mBinder = new RecordingServiceBinder();
    }

    /**
     * Get an intent to start the recording service.
     *
     * @param context    Context from the requesting activity
     * @param resultCode The result code from {@link android.app.Activity#onActivityResult(int, int,
     *                   android.content.Intent)}
     * @param audioSource   The ordinal value of the audio source
     *                      {@link com.android.systemui.screenrecord.ScreenRecordingAudioSource}
     * @param showTaps   True to make touches visible while recording
     */
    public static Intent getStartIntent(Context context, int resultCode,
            int audioSource, boolean showTaps, boolean showStopDot,
            boolean lowQuality, boolean longerDuration) {
        return new Intent(context, RecordingService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_AUDIO_SOURCE, audioSource)
                .putExtra(EXTRA_SHOW_TAPS, showTaps)
                .putExtra(EXTRA_SHOW_STOP_DOT, showStopDot)
                .putExtra(EXTRA_LOW_QUALITY, lowQuality)
                .putExtra(EXTRA_LONGER_DURATION, longerDuration);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return Service.START_NOT_STICKY;
        }
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand " + action);

        int currentUserId = mUserContextTracker.getUserContext().getUserId();
        UserHandle currentUser = new UserHandle(currentUserId);
        switch (action) {
            case ACTION_START:
                mAudioSource = ScreenRecordingAudioSource
                        .values()[intent.getIntExtra(EXTRA_AUDIO_SOURCE, 0)];
                Log.d(TAG, "recording with audio source" + mAudioSource);
                mShowTaps = intent.getBooleanExtra(EXTRA_SHOW_TAPS, false);
                mShowStopDot = intent.getBooleanExtra(EXTRA_SHOW_STOP_DOT, false);
                mLowQuality = intent.getBooleanExtra(EXTRA_LOW_QUALITY, false);
                mLongerDuration = intent.getBooleanExtra(EXTRA_LONGER_DURATION, false);

                mRecorder = new ScreenMediaRecorder(
                        mUserContextTracker.getUserContext(),
                        currentUserId,
                        mAudioSource,
                        this
                );
                setTapsVisible(mShowTaps);
                setStopDotVisible(mShowStopDot);
                setLowQuality(mLowQuality);
                setLongerDuration(mLongerDuration);

                if (startRecording()) {
                    updateState(true);
                    createRecordingNotification();
                    mUiEventLogger.log(Events.ScreenRecordEvent.SCREEN_RECORD_START);
                } else {
                    updateState(false);
                    createErrorNotification();
                    stopForeground(true);
                    stopSelf();
                    return Service.START_NOT_STICKY;
                }
                break;

            case ACTION_STOP_NOTIF:
            case ACTION_STOP:
                // only difference for actions is the log event
                if (ACTION_STOP_NOTIF.equals(action)) {
                    mUiEventLogger.log(Events.ScreenRecordEvent.SCREEN_RECORD_END_NOTIFICATION);
                } else {
                    mUiEventLogger.log(Events.ScreenRecordEvent.SCREEN_RECORD_END_QS_TILE);
                }
                // Check user ID - we may be getting a stop intent after user switch, in which case
                // we want to post the notifications for that user, which is NOT current user
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                if (userId == -1) {
                    userId = mUserContextTracker.getUserContext().getUserId();
                }
                Log.d(TAG, "notifying for user " + userId);
                stopRecording(userId);
                stopForeground(true);
                break;

            case ACTION_SHARE:
                Uri shareUri = Uri.parse(intent.getStringExtra(EXTRA_PATH));

                Intent shareIntent = new Intent(Intent.ACTION_SEND)
                        .setType("video/mp4")
                        .putExtra(Intent.EXTRA_STREAM, shareUri);
                mKeyguardDismissUtil.executeWhenUnlocked(() -> {
                    String shareLabel = getResources().getString(R.string.screenrecord_share_label);
                    startActivity(Intent.createChooser(shareIntent, shareLabel)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    // Remove notification
                    mNotificationManager.cancelAsUser(null, NOTIFICATION_VIEW_ID, currentUser);
                    return false;
                }, false, false);

                // Close quick shade
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                break;
            case ACTION_DELETE:
                // Close quick shade
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

                ContentResolver resolver = getContentResolver();
                Uri uri = Uri.parse(intent.getStringExtra(EXTRA_PATH));
                resolver.delete(uri, null, null);

                Toast.makeText(
                        this,
                        R.string.screenrecord_delete_description,
                        Toast.LENGTH_LONG).show();

                // Remove notification
                mNotificationManager.cancelAsUser(null, NOTIFICATION_VIEW_ID, currentUser);
                Log.d(TAG, "Deleted recording " + uri);
                break;
            case ACTION_SHOW_DIALOG:
                if (mController != null) {
                    mController.createScreenRecordDialog(this, null).show();
                }
                break;
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mController.addCallback((RecordingController.RecordingStateChangeCallback) mBinder);
    }

    @Override
    public void onDestroy() {
        mController.removeCallback((RecordingController.RecordingStateChangeCallback) mBinder);
        super.onDestroy();
    }

    @VisibleForTesting
    protected ScreenMediaRecorder getRecorder() {
        return mRecorder;
    }

    private void updateState(boolean state) {
        int userId = mUserContextTracker.getUserContext().getUserId();
        if (userId == UserHandle.USER_SYSTEM) {
            // Main user has a reference to the correct controller, so no need to use a broadcast
            mController.updateState(state);
        } else {
            Intent intent = new Intent(RecordingController.INTENT_UPDATE_STATE);
            intent.putExtra(RecordingController.EXTRA_STATE, state);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            sendBroadcast(intent, PERMISSION_SELF);
        }
    }

    /**
     * Begin the recording session
     * @return true if successful, false if something went wrong
     */
    private boolean startRecording() {
        try {
            getRecorder().start();
            return true;
        } catch (IOException | RemoteException | RuntimeException e) {
            showErrorToast(R.string.screenrecord_start_error);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Simple error notification, needed since startForeground must be called to avoid errors
     */
    @VisibleForTesting
    protected void createErrorNotification() {
        Resources res = getResources();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screenrecord_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.screenrecord_channel_description));
        channel.enableVibration(true);
        mNotificationManager.createNotificationChannel(channel);

        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                res.getString(R.string.screenrecord_name));
        String notificationTitle = res.getString(R.string.screenrecord_start_error);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(notificationTitle)
                .addExtras(extras);
        startForeground(NOTIFICATION_RECORDING_ID, builder.build());
    }

    @VisibleForTesting
    protected void showErrorToast(int stringId) {
        Toast.makeText(this, stringId, Toast.LENGTH_LONG).show();
    }

    @VisibleForTesting
    protected void createRecordingNotification() {
        Resources res = getResources();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screenrecord_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.screenrecord_channel_description));
        channel.enableVibration(true);
        channel.setSound(null, // silent
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        mNotificationManager.createNotificationChannel(channel);

        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                res.getString(R.string.screenrecord_name));

        String notificationTitle = mAudioSource == ScreenRecordingAudioSource.NONE
                ? res.getString(R.string.screenrecord_ongoing_screen_only)
                : res.getString(R.string.screenrecord_ongoing_screen_and_audio);

        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE,
                getNotificationIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_android),
                getResources().getString(R.string.screenrecord_stop_label),
                pendingIntent).build();
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(notificationTitle)
                .setUsesChronometer(true)
                .setColorized(true)
                .setColor(getResources().getColor(R.color.GM2_red_700))
                .setOngoing(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(stopAction)
                .addExtras(extras);
        startForeground(NOTIFICATION_RECORDING_ID, builder.build());
    }

    @VisibleForTesting
    protected Notification createProcessingNotification() {
        Resources res = getApplicationContext().getResources();
        String notificationTitle = mAudioSource == ScreenRecordingAudioSource.NONE
                ? res.getString(R.string.screenrecord_ongoing_screen_only)
                : res.getString(R.string.screenrecord_ongoing_screen_and_audio);

        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                res.getString(R.string.screenrecord_name));

        Notification.Builder builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setContentText(
                        getResources().getString(R.string.screenrecord_background_processing_label))
                .setSmallIcon(R.drawable.ic_screenrecord)
                .addExtras(extras);
        return builder.build();
    }

    @VisibleForTesting
    protected Notification createSaveNotification(ScreenMediaRecorder.SavedRecording recording) {
        Uri uri = recording.getUri();
        Intent viewIntent = new Intent(Intent.ACTION_VIEW)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setDataAndType(uri, "video/mp4");

        Notification.Action shareAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_screenrecord),
                getResources().getString(R.string.screenrecord_share_label),
                PendingIntent.getService(
                        this,
                        REQUEST_CODE,
                        getShareIntent(this, uri.toString()),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();

        Notification.Action deleteAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_screenrecord),
                getResources().getString(R.string.screenrecord_delete_label),
                PendingIntent.getService(
                        this,
                        REQUEST_CODE,
                        getDeleteIntent(this, uri.toString()),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();

        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                getResources().getString(R.string.screenrecord_name));

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(getResources().getString(R.string.screenrecord_save_title))
                .setContentText(getResources().getString(R.string.screenrecord_save_text))
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        REQUEST_CODE,
                        viewIntent,
                        PendingIntent.FLAG_IMMUTABLE))
                .addAction(shareAction)
                .addAction(deleteAction)
                .setAutoCancel(true)
                .addExtras(extras);

        // Add thumbnail if available
        Bitmap thumbnailBitmap = recording.getThumbnail();
        if (thumbnailBitmap != null) {
            Notification.BigPictureStyle pictureStyle = new Notification.BigPictureStyle()
                    .bigPicture(thumbnailBitmap)
                    .showBigPictureWhenCollapsed(true);
            builder.setStyle(pictureStyle);
        }
        return builder.build();
    }

    private void stopRecording(int userId) {
        setTapsVisible(false);
        setStopDotVisible(false);
        if (getRecorder() != null) {
            getRecorder().end();
            saveRecording(userId);
        } else {
            Log.e(TAG, "stopRecording called, but recorder was null");
        }
        updateState(false);
    }

    private void saveRecording(int userId) {
        UserHandle currentUser = new UserHandle(userId);
        mNotificationManager.notifyAsUser(null, NOTIFICATION_PROCESSING_ID,
                createProcessingNotification(), currentUser);

        mLongExecutor.execute(() -> {
            try {
                Log.d(TAG, "saving recording");
                Notification notification = createSaveNotification(getRecorder().save());
                if (!mController.isRecording()) {
                    mNotificationManager.notifyAsUser(null, NOTIFICATION_VIEW_ID, notification,
                            currentUser);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving screen recording: " + e.getMessage());
                showErrorToast(R.string.screenrecord_delete_error);
            } finally {
                mNotificationManager.cancelAsUser(null, NOTIFICATION_PROCESSING_ID, currentUser);
            }
        });
    }

    private void setTapsVisible(boolean turnOn) {
        int value = turnOn ? 1 : 0;
        Settings.System.putInt(getContentResolver(), Settings.System.SHOW_TOUCHES, value);
    }

    private void setLowQuality(boolean turnOn) {
        if (getRecorder() != null) {
            getRecorder().setLowQuality(turnOn);
        }
    }

    private void setLongerDuration(boolean longer) {
        if (getRecorder() != null) {
            getRecorder().setLongerDuration(longer);
        }
    }

    private void setStopDotVisible(boolean turnOn) {
        if (turnOn) {
            showDot();
        } else if (mDotShowing) {
            stopDot();
        }
    }

    private void showDot() {
        mDotShowing = true;
        mIsDotAtRight = true;
        final int size = (int) (this.getResources()
                .getDimensionPixelSize(R.dimen.screenrecord_dot_size));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // don't get softkey inputs
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // allow outside inputs
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.width = size;
        params.height = size;

        mFrameLayout = new FrameLayout(this);

        mWindowManager.addView(mFrameLayout, params);
        final LayoutInflater inflater =
                (LayoutInflater) mUserContextTracker.getUserContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.screenrecord_dot, mFrameLayout);

        final ImageView dot = (ImageView) mFrameLayout.findViewById(R.id.dot);
        dot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    getStopPendingIntent().send();
                } catch (PendingIntent.CanceledException e) {}
            }
        });

        dot.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                dot.setAnimation(null);
                WindowManager.LayoutParams params =
                        (WindowManager.LayoutParams) mFrameLayout.getLayoutParams();
                params.gravity = Gravity.TOP | (mIsDotAtRight? Gravity.LEFT : Gravity.RIGHT);
                mIsDotAtRight = !mIsDotAtRight;
                mWindowManager.updateViewLayout(mFrameLayout, params);
                dot.startAnimation(getDotAnimation());
                return true;
            }
        });

        dot.startAnimation(getDotAnimation());
    }

    private PendingIntent getStopPendingIntent() {
        return PendingIntent.getService(this, REQUEST_CODE, getStopIntent(this),
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void stopDot() {
        mDotShowing = false;
        final ImageView dot = (ImageView) mFrameLayout.findViewById(R.id.dot);
        if (dot != null) {
            dot.setAnimation(null);
            mWindowManager.removeView(mFrameLayout);
        }
    }

    private Animation getDotAnimation() {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(500);
        anim.setStartOffset(100);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        return anim;
    }

    /**
     * Get an intent to stop the recording service.
     * @param context Context from the requesting activity
     * @return
     */
    public static Intent getStopIntent(Context context) {
        return new Intent(context, RecordingService.class)
                .setAction(ACTION_STOP)
                .putExtra(Intent.EXTRA_USER_HANDLE, context.getUserId());
    }

    /**
     * Get the recording notification content intent
     * @param context
     * @return
     */
    protected static Intent getNotificationIntent(Context context) {
        return new Intent(context, RecordingService.class).setAction(ACTION_STOP_NOTIF);
    }

    private static Intent getShareIntent(Context context, String path) {
        return new Intent(context, RecordingService.class).setAction(ACTION_SHARE)
                .putExtra(EXTRA_PATH, path);
    }

    private static Intent getDeleteIntent(Context context, String path) {
        return new Intent(context, RecordingService.class).setAction(ACTION_DELETE)
                .putExtra(EXTRA_PATH, path);
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.d(TAG, "Media recorder info: " + what);
        onStartCommand(getStopIntent(this), 0, 0);
    }

    private class RecordingServiceBinder extends IRemoteRecording.Stub
            implements RecordingController.RecordingStateChangeCallback {

        private ArrayList<IRecordingCallback> mCallbackList = new ArrayList<>();

        @Override
        public void startRecording() throws RemoteException {
            Intent intent = new Intent(RecordingService.this, RecordingService.class);
            intent.setAction(ACTION_SHOW_DIALOG);
            RecordingService.this.startService(intent);
        }

        @Override
        public void stopRecording() throws RemoteException {
            Intent intent = new Intent(RecordingService.this, RecordingService.class);
            intent.setAction(ACTION_STOP_NOTIF);
            RecordingService.this.startService(intent);
        }

        @Override
        public boolean isRecording() throws RemoteException {
            return mController.isRecording();
        }

        @Override
        public boolean isStarting() throws RemoteException {
            return mController.isStarting();
        }

        public void addRecordingCallback(IRecordingCallback callback) throws RemoteException {
            if (!mCallbackList.contains(callback)) {
                mCallbackList.add(callback);
            }
        }

        public void removeRecordingCallback(IRecordingCallback callback) throws RemoteException {
            mCallbackList.remove(callback);
        }

        @Override
        public void onRecordingStart() {
            for (IRecordingCallback callback : mCallbackList) {
                try {
                    callback.onRecordingStart();
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

        @Override
        public void onRecordingEnd() {
            for (IRecordingCallback callback : mCallbackList) {
                try {
                    callback.onRecordingEnd();
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }
    }
}
