/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.util.catalyst;

import static android.provider.Settings.Global.ZEN_MODE_OFF;
import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
import android.app.ActivityThread;
import android.app.AlertDialog;
import android.app.IActivityManager;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.DialogInterface;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.input.InputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.graphics.Color;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorPrivacyManager;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.UserHandle;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.telephony.SubscriptionManager;
import android.provider.Settings;

import com.android.internal.statusbar.IStatusBarService;

import java.util.Locale;
import android.widget.Toast;

import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.util.ArrayUtils;
import com.android.internal.R;
import java.lang.ref.WeakReference;

import java.util.ArrayList;

public class CatalystUtils {

    public static boolean isAppInstalled(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAvailableApp(String packageName, Context context) {
       Context mContext = context;
       final PackageManager pm = mContext.getPackageManager();
       try {
           pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
           int enabled = pm.getApplicationEnabledSetting(packageName);
           return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
               enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
       } catch (NameNotFoundException e) {
           return false;
       }
    }

    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    public static boolean isPackageAvailable(Context context, String packageName) {
        final PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            int enabled = pm.getApplicationEnabledSetting(packageName);
            return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (NameNotFoundException e) {
            return false;
        }
    }
    
    // Restart UI prompt
    public static void restartSystemUi(Context context) {
        new RestartSystemUiTask(context).execute();
    }

    public static void showSystemUiRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.systemui_restart_title)
                .setMessage(R.string.systemui_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartSystemUi(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class RestartSystemUiTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public RestartSystemUiTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.systemui".equals(app.processName)) {
                        ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    //Launcher Restart
    public static void restartLauncher(Context context) {
        new RestartLauncherTask(context).execute();
    }

    private static class RestartLauncherTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public RestartLauncherTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.launcher3".equals(app.processName)) {
                        ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    // Check if device has a notch
    public static boolean hasNotch(Context context) {
        int result = 0;
        int resid;
        int resourceId = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        resid = context.getResources().getIdentifier("config_fillMainBuiltInDisplayCutout",
                "bool", "android");
        if (resid > 0) {
            return context.getResources().getBoolean(resid);
        }
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = 24 * (metrics.densityDpi / 160f);
        return result > Math.round(px);
    }

    /**
     * Returns whether the device is voice-capable (meaning, it is also a phone).
     */
    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }
    
    public static class SleepModeController {
        private final Resources mResources;
        private final Context mUiContext;

        private Context mContext;
        private AudioManager mAudioManager;
        private NotificationManager mNotificationManager;
        private WifiManager mWifiManager;
        private SensorPrivacyManager mSensorPrivacyManager;
        private LocationManager mLocationManager;
        private BluetoothAdapter mBluetoothAdapter;
        private int mSubscriptionId;
        private Toast mToast;

        private boolean mSleepModeEnabled;

        private static boolean mWifiState;
        private static boolean mLocationState;
        private static boolean mCellularState;
        private static boolean mBluetoothState;
        private static int mRingerState;
        private static int mZenState;

        private static final String TAG = "SleepModeController";
        private static final int SLEEP_NOTIFICATION_ID = 727;
        public static final String SLEEP_MODE_TURN_OFF = "android.intent.action.SLEEP_MODE_TURN_OFF";

        public SleepModeController(Context context) {
            mContext = context;
            mUiContext = ActivityThread.currentActivityThread().getSystemUiContext();
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            mResources = mContext.getResources();

            mSleepModeEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;

            SettingsObserver observer = new SettingsObserver(new Handler(Looper.getMainLooper()));
            observer.observe();
            observer.update();
        }

        private TelephonyManager getTelephonyManager() {
            int subscriptionId = mSubscriptionId;

            // If mSubscriptionId is invalid, get default data sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
            }

            // If data sub is also invalid, get any active sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                int[] activeSubIds = SubscriptionManager.from(mContext).getActiveSubscriptionIdList();
                if (!ArrayUtils.isEmpty(activeSubIds)) {
                    subscriptionId = activeSubIds[0];
                }
            }

            return mContext.getSystemService(
                    TelephonyManager.class).createForSubscriptionId(subscriptionId);
        }

        private boolean isWifiEnabled() {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                return mWifiManager.isWifiEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setWifiEnabled(boolean enable) {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                mWifiManager.setWifiEnabled(enable);
            } catch (Exception e) {
            }
        }

        private boolean isLocationEnabled() {
            if (mLocationManager == null) {
                mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            }
            try {
                return mLocationManager.isLocationEnabledForUser(UserHandle.of(ActivityManager.getCurrentUser()));
            } catch (Exception e) {
                return false;
            }
        }

        private void setLocationEnabled(boolean enable) {
            if (mLocationManager == null) {
                mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            }
            try {
                mLocationManager.setLocationEnabledForUser(enable, UserHandle.of(ActivityManager.getCurrentUser()));
            } catch (Exception e) {
            }
        }

        private boolean isBluetoothEnabled() {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                return mBluetoothAdapter.isEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setBluetoothEnabled(boolean enable) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                if (enable) mBluetoothAdapter.enable();
                else mBluetoothAdapter.disable();
            } catch (Exception e) {
            }
        }

        private boolean isSensorEnabled() {
            if (mSensorPrivacyManager == null) {
                mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            }
            try {
                return !mSensorPrivacyManager.isAllSensorPrivacyEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setSensorEnabled(boolean enable) {
            if (mSensorPrivacyManager == null) {
                mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            }
            try {
                mSensorPrivacyManager.setAllSensorPrivacy(!enable);
            } catch (Exception e) {
            }
        }

        private int getZenMode() {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                return mNotificationManager.getZenMode();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setZenMode(int mode) {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                mNotificationManager.setZenMode(mode, null, TAG);
            } catch (Exception e) {
            }
        }

        private int getRingerModeInternal() {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                return mAudioManager.getRingerModeInternal();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setRingerModeInternal(int mode) {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                mAudioManager.setRingerModeInternal(mode);
            } catch (Exception e) {
            }
        }

        private void enable() {
            if (!ActivityManager.isSystemReady()) return;

            // Disable Wi-Fi
            final boolean disableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_WIFI_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableWifi) {
                mWifiState = isWifiEnabled();
                setWifiEnabled(false);
            }

            // Disable Bluetooth
            final boolean disableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_BLUETOOTH_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableBluetooth) {
                mBluetoothState = isBluetoothEnabled();
                setBluetoothEnabled(false);
            }

            // Disable Mobile Data
            final boolean disableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_CELLULAR_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableData) {
                mCellularState = getTelephonyManager().isDataEnabled();
                getTelephonyManager().setDataEnabled(false);
            }

            // Disable Location
            final boolean disableLocation = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_LOCATION_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableLocation) {
                mLocationState = isLocationEnabled();
                setLocationEnabled(false);
            }

            // Disable Sensors
            final boolean disableSensors = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_SENSORS_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableSensors) {
                setSensorEnabled(false);
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0) {
                mRingerState = getRingerModeInternal();
                mZenState = getZenMode();
                if (ringerMode == 1) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                    setZenMode(ZEN_MODE_OFF);
                } else if (ringerMode == 2) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                    setZenMode(ZEN_MODE_IMPORTANT_INTERRUPTIONS);
                } else if (ringerMode == 3) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                    setZenMode(ZEN_MODE_OFF);
                }
            }

            showToast(mResources.getString(R.string.sleep_mode_enabled_toast), Toast.LENGTH_LONG);
            addNotification();
        }

        private void disable() {
            if (!ActivityManager.isSystemReady()) return;

            // Enable Wi-Fi
            final boolean disableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_WIFI_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableWifi && mWifiState != isWifiEnabled()) {
                setWifiEnabled(mWifiState);
            }

            // Enable Bluetooth
            final boolean disableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_BLUETOOTH_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableBluetooth && mBluetoothState != isBluetoothEnabled()) {
                setBluetoothEnabled(mBluetoothState);
            }

            // Enable Mobile Data
            final boolean disableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_CELLULAR_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableData && mCellularState != getTelephonyManager().isDataEnabled()) {
                getTelephonyManager().setDataEnabled(mCellularState);
            }

            // Enable Location
            final boolean disableLocation = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_LOCATION_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableLocation && mLocationState != isLocationEnabled()) {
                setLocationEnabled(mLocationState);
            }

            // Enable Sensors
            final boolean disableSensors = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_SENSORS_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableSensors) {
                setSensorEnabled(true);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                if (!isSensorEnabled()) {
                    setSensorEnabled(true);
                }
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0 && (mRingerState != getRingerModeInternal() ||
                    mZenState != getZenMode())) {
                setRingerModeInternal(mRingerState);
                setZenMode(mZenState);
            }

            showToast(mResources.getString(R.string.sleep_mode_disabled_toast), Toast.LENGTH_LONG);
            mNotificationManager.cancel(SLEEP_NOTIFICATION_ID);
        }

        private void addNotification() {
            final Intent intent = new Intent(SLEEP_MODE_TURN_OFF);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Display a notification
            Notification.Builder builder = new Notification.Builder(mContext, SystemNotificationChannels.SLEEP)
                .setTicker(mResources.getString(R.string.sleep_mode_notification_title))
                .setContentTitle(mResources.getString(R.string.sleep_mode_notification_title))
                .setContentText(mResources.getString(R.string.sleep_mode_notification_content))
                .setSmallIcon(R.drawable.ic_sleep)
                .setWhen(java.lang.System.currentTimeMillis())
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

            final Notification notification = builder.build();
            mNotificationManager.notify(SLEEP_NOTIFICATION_ID, notification);
        }

        private void showToast(String msg, int duration) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mToast != null) mToast.cancel();
                        mToast = Toast.makeText(mUiContext, msg, duration);
                        mToast.show();
                    } catch (Exception e) {
                    }
                }
            });
        }

        private void setSleepMode(boolean enabled) {
            if (mSleepModeEnabled == enabled) {
                return;
            }

            mSleepModeEnabled = enabled;

            if (mSleepModeEnabled) {
                enable();
            } else {
                disable();
            }
        }

        class SettingsObserver extends ContentObserver {
            SettingsObserver(Handler handler) {
                super(handler);
            }

        void observe() {
                mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                        Settings.Secure.SLEEP_MODE_ENABLED), false, this,
                        UserHandle.USER_ALL);
                update();
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                update();
            }

            void update() {
                final boolean enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                        Settings.Secure.SLEEP_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
                setSleepMode(enabled);
            }
        }
     }
        
     public static class QSLayoutUtils {

        public static boolean getQSTileLabelHide(Context context) {
            return Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QS_TILE_LABEL_HIDE,
                    0, UserHandle.USER_CURRENT) == 1;
        }

        public static boolean getQSTileVerticalLayout(Context context) {
            return Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QS_TILE_VERTICAL_LAYOUT,
                    0, UserHandle.USER_CURRENT) == 1;
          }
       
       public static boolean updateLayout(Context context) {
            final IOverlayManager overlayManager = IOverlayManager.Stub.asInterface(ServiceManager.getService(
                    Context.OVERLAY_SERVICE));
            final int layout_qs = Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QS_LAYOUT,
                    42, UserHandle.USER_CURRENT);
            final int layout_qqs = Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.QQS_LAYOUT,
                    22, UserHandle.USER_CURRENT);
            final int row_qs = layout_qs / 10;
            final int col_qs = layout_qs % 10;
            final int row_qqs = layout_qqs / 10;
            for (int i = 0; i < 2; ++i) {
                String pkgName;
                if (i == 0) {
                    pkgName = String.format("com.arrow.qs.portrait.layout_%sx%s", Integer.toString(row_qs), Integer.toString(col_qs));
                } else {
                    pkgName = String.format("com.arrow.qqs.portrait.layout_%sx%s", Integer.toString(row_qqs), Integer.toString(col_qs));
                }
                try {
                    overlayManager.setEnabledExclusiveInCategory(pkgName, UserHandle.USER_CURRENT);
                } catch (RemoteException re) {
                    return false;
                }
            }
            return true;
        }
    }
}
