/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.shared.system;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_2BUTTON;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL;

import android.annotation.IntDef;
import android.content.Context;
import android.content.res.Resources;
import android.view.ViewConfiguration;
import android.view.WindowManagerPolicyConstants;

import com.android.internal.policy.ScreenDecorationsUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.StringJoiner;

/**
 * Various shared constants between Launcher and SysUI as part of quickstep
 */
public class QuickStepContract {
    // Fully qualified name of the Launcher activity.
    public static final String LAUNCHER_ACTIVITY_CLASS_NAME =
            "com.android.Launcher3";

    public static final String KEY_EXTRA_SYSUI_PROXY = "extra_sysui_proxy";
    public static final String KEY_EXTRA_WINDOW_CORNER_RADIUS = "extra_window_corner_radius";
    public static final String KEY_EXTRA_SUPPORTS_WINDOW_CORNERS = "extra_supports_window_corners";
    // See IPip.aidl
    public static final String KEY_EXTRA_SHELL_PIP = "extra_shell_pip";
    // See ISplitScreen.aidl
    public static final String KEY_EXTRA_SHELL_SPLIT_SCREEN = "extra_shell_split_screen";
    // See IOneHanded.aidl
    public static final String KEY_EXTRA_SHELL_ONE_HANDED = "extra_shell_one_handed";
    // See IShellTransitions.aidl
    public static final String KEY_EXTRA_SHELL_SHELL_TRANSITIONS =
            "extra_shell_shell_transitions";
    // See IStartingWindow.aidl
    public static final String KEY_EXTRA_SHELL_STARTING_WINDOW =
            "extra_shell_starting_window";
    // See ISysuiUnlockAnimationController.aidl
    public static final String KEY_EXTRA_UNLOCK_ANIMATION_CONTROLLER = "unlock_animation";
    // See IRecentTasks.aidl
    public static final String KEY_EXTRA_RECENT_TASKS = "recent_tasks";
    public static final String KEY_EXTRA_SHELL_BACK_ANIMATION = "extra_shell_back_animation";

    public static final String NAV_BAR_MODE_3BUTTON_OVERLAY =
            WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY;
    public static final String NAV_BAR_MODE_GESTURAL_OVERLAY =
            WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY;

    // Overview is disabled, either because the device is in lock task mode, or because the device
    // policy has disabled the feature
    public static final int SYSUI_STATE_SCREEN_PINNING = 1 << 0;
    // The navigation bar is hidden due to immersive mode
    public static final int SYSUI_STATE_NAV_BAR_HIDDEN = 1 << 1;
    // The notification panel is expanded and interactive (either locked or unlocked), and the
    // quick settings is not expanded
    public static final int SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED = 1 << 2;
    // The keyguard bouncer is showing
    public static final int SYSUI_STATE_BOUNCER_SHOWING = 1 << 3;
    // The navigation bar a11y button should be shown
    public static final int SYSUI_STATE_A11Y_BUTTON_CLICKABLE = 1 << 4;
    // The navigation bar a11y button shortcut is available
    public static final int SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE = 1 << 5;
    // The keyguard is showing and not occluded
    public static final int SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING = 1 << 6;
    // The recents feature is disabled (either by SUW/SysUI/device policy)
    public static final int SYSUI_STATE_OVERVIEW_DISABLED = 1 << 7;
    // The home feature is disabled (either by SUW/SysUI/device policy)
    public static final int SYSUI_STATE_HOME_DISABLED = 1 << 8;
    // The keyguard is showing, but occluded
    public static final int SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED = 1 << 9;
    // The search feature is disabled (either by SUW/SysUI/device policy)
    public static final int SYSUI_STATE_SEARCH_DISABLED = 1 << 10;
    // The notification panel is expanded and interactive (either locked or unlocked), and quick
    // settings is expanded.
    public static final int SYSUI_STATE_QUICK_SETTINGS_EXPANDED = 1 << 11;
    // Winscope tracing is enabled
    public static final int SYSUI_STATE_TRACING_ENABLED = 1 << 12;
    // The Assistant gesture should be constrained. It is up to the launcher implementation to
    // decide how to constrain it
    public static final int SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED = 1 << 13;
    // The bubble stack is expanded. This means that the home gesture should be ignored, since a
    // swipe up is an attempt to close the bubble stack, but that the back gesture should remain
    // enabled (since it's used to navigate back within the bubbled app, or to collapse the bubble
    // stack.
    public static final int SYSUI_STATE_BUBBLES_EXPANDED = 1 << 14;
    // A SysUI dialog is showing.
    public static final int SYSUI_STATE_DIALOG_SHOWING = 1 << 15;
    // The one-handed mode is active
    public static final int SYSUI_STATE_ONE_HANDED_ACTIVE = 1 << 16;
    // Allow system gesture no matter the system bar(s) is visible or not
    public static final int SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY = 1 << 17;
    // The IME is showing
    public static final int SYSUI_STATE_IME_SHOWING = 1 << 18;
    // The window magnification is overlapped with system gesture insets at the bottom.
    public static final int SYSUI_STATE_MAGNIFICATION_OVERLAP = 1 << 19;
    // ImeSwitcher is showing
    public static final int SYSUI_STATE_IME_SWITCHER_SHOWING = 1 << 20;
    // Device dozing/AOD state
    public static final int SYSUI_STATE_DEVICE_DOZING = 1 << 21;
    // The home feature is disabled (either by SUW/SysUI/device policy)
    public static final int SYSUI_STATE_BACK_DISABLED = 1 << 22;
    // The bubble stack is expanded AND the mange menu for bubbles is expanded on top of it.
    public static final int SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED = 1 << 23;
    // The current app is in immersive mode
    public static final int SYSUI_STATE_IMMERSIVE_MODE = 1 << 24;
    // The voice interaction session window is showing
    public static final int SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING = 1 << 25;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SYSUI_STATE_SCREEN_PINNING,
            SYSUI_STATE_NAV_BAR_HIDDEN,
            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
            SYSUI_STATE_BOUNCER_SHOWING,
            SYSUI_STATE_A11Y_BUTTON_CLICKABLE,
            SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
            SYSUI_STATE_OVERVIEW_DISABLED,
            SYSUI_STATE_HOME_DISABLED,
            SYSUI_STATE_SEARCH_DISABLED,
            SYSUI_STATE_TRACING_ENABLED,
            SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED,
            SYSUI_STATE_BUBBLES_EXPANDED,
            SYSUI_STATE_DIALOG_SHOWING,
            SYSUI_STATE_ONE_HANDED_ACTIVE,
            SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY,
            SYSUI_STATE_IME_SHOWING,
            SYSUI_STATE_MAGNIFICATION_OVERLAP,
            SYSUI_STATE_IME_SWITCHER_SHOWING,
            SYSUI_STATE_DEVICE_DOZING,
            SYSUI_STATE_BACK_DISABLED,
            SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED,
            SYSUI_STATE_IMMERSIVE_MODE,
            SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING
    })
    public @interface SystemUiStateFlags {}

    public static String getSystemUiStateString(int flags) {
        StringJoiner str = new StringJoiner("|");
        str.add((flags & SYSUI_STATE_SCREEN_PINNING) != 0 ? "screen_pinned" : "");
        str.add((flags & SYSUI_STATE_OVERVIEW_DISABLED) != 0 ? "overview_disabled" : "");
        str.add((flags & SYSUI_STATE_HOME_DISABLED) != 0 ? "home_disabled" : "");
        str.add((flags & SYSUI_STATE_SEARCH_DISABLED) != 0 ? "search_disabled" : "");
        str.add((flags & SYSUI_STATE_NAV_BAR_HIDDEN) != 0 ? "navbar_hidden" : "");
        str.add((flags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) != 0 ? "notif_visible" : "");
        str.add((flags & SYSUI_STATE_QUICK_SETTINGS_EXPANDED) != 0 ? "qs_visible" : "");
        str.add((flags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING) != 0 ? "keygrd_visible" : "");
        str.add((flags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED) != 0
                ? "keygrd_occluded" : "");
        str.add((flags & SYSUI_STATE_BOUNCER_SHOWING) != 0 ? "bouncer_visible" : "");
        str.add((flags & SYSUI_STATE_DIALOG_SHOWING) != 0 ? "dialog_showing" : "");
        str.add((flags & SYSUI_STATE_A11Y_BUTTON_CLICKABLE) != 0 ? "a11y_click" : "");
        str.add((flags & SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE) != 0 ? "a11y_long_click" : "");
        str.add((flags & SYSUI_STATE_TRACING_ENABLED) != 0 ? "tracing" : "");
        str.add((flags & SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED) != 0
                ? "asst_gesture_constrain" : "");
        str.add((flags & SYSUI_STATE_BUBBLES_EXPANDED) != 0 ? "bubbles_expanded" : "");
        str.add((flags & SYSUI_STATE_ONE_HANDED_ACTIVE) != 0 ? "one_handed_active" : "");
        str.add((flags & SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY) != 0
                ? "allow_gesture" : "");
        str.add((flags & SYSUI_STATE_IME_SHOWING) != 0 ? "ime_visible" : "");
        str.add((flags & SYSUI_STATE_MAGNIFICATION_OVERLAP) != 0 ? "magnification_overlap" : "");
        str.add((flags & SYSUI_STATE_IME_SWITCHER_SHOWING) != 0 ? "ime_switcher_showing" : "");
        str.add((flags & SYSUI_STATE_DEVICE_DOZING) != 0 ? "device_dozing" : "");
        str.add((flags & SYSUI_STATE_BACK_DISABLED) != 0 ? "back_disabled" : "");
        str.add((flags & SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED) != 0
                ? "bubbles_mange_menu_expanded" : "");
        str.add((flags & SYSUI_STATE_IMMERSIVE_MODE) != 0 ? "immersive_mode" : "");
        str.add((flags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0 ? "vis_win_showing" : "");
        return str.toString();
    }

    /**
     * Ratio of quickstep touch slop (when system takes over the touch) to view touch slop
     */
    public static final float QUICKSTEP_TOUCH_SLOP_RATIO = 3;

    /**
     * Touch slop for quickstep gesture
     */
    public static final float getQuickStepTouchSlopPx(Context context) {
        return QUICKSTEP_TOUCH_SLOP_RATIO * ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /**
     * Touch slopes and thresholds for quick step operations. Drag slop is the point where the
     * home button press/long press over are ignored and will start to drag when exceeded and the
     * touch slop is when the respected operation will occur when exceeded. Touch slop must be
     * larger than the drag slop.
     */
    public static int getQuickStepDragSlopPx() {
        return convertDpToPixel(10);
    }

    public static int getQuickStepTouchSlopPx() {
        return convertDpToPixel(24);
    }

    public static int getQuickScrubTouchSlopPx() {
        return convertDpToPixel(24);
    }

    private static int convertDpToPixel(float dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    /**
     * Returns whether the specified sysui state is such that the assistant gesture should be
     * disabled.
     */
    public static boolean isAssistantGestureDisabled(int sysuiStateFlags) {
        if ((sysuiStateFlags & SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY) != 0) {
            sysuiStateFlags &= ~SYSUI_STATE_NAV_BAR_HIDDEN;
        }
        // Disable when in quick settings, screen pinning, immersive, the bouncer is showing, 
        // or search is disabled
        int disableFlags = SYSUI_STATE_SCREEN_PINNING
                | SYSUI_STATE_NAV_BAR_HIDDEN
                | SYSUI_STATE_BOUNCER_SHOWING
                | SYSUI_STATE_SEARCH_DISABLED
                | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
        if ((sysuiStateFlags & disableFlags) != 0) {
            return true;
        }

        // Disable when notifications are showing (only if unlocked)
        if ((sysuiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) != 0
                && (sysuiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING) == 0) {
            return true;
        }

        return false;
    }

    /**
     * Returns whether the specified sysui state is such that the back gesture should be
     * disabled.
     */
    public static boolean isBackGestureDisabled(int sysuiStateFlags) {
        // Always allow when the bouncer/global actions/voice session is showing (even on top of
        // the keyguard)
        if ((sysuiStateFlags & SYSUI_STATE_BOUNCER_SHOWING) != 0
                || (sysuiStateFlags & SYSUI_STATE_DIALOG_SHOWING) != 0
                || (sysuiStateFlags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0) {
            return false;
        }
        if ((sysuiStateFlags & SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY) != 0) {
            sysuiStateFlags &= ~SYSUI_STATE_NAV_BAR_HIDDEN;
        }
        // Disable when in immersive, or the notifications are interactive
        int disableFlags = SYSUI_STATE_NAV_BAR_HIDDEN
                | SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
                | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
        return (sysuiStateFlags & disableFlags) != 0;
    }

    /**
     * @return whether this nav bar mode is edge to edge
     */
    public static boolean isGesturalMode(int mode) {
        return mode == NAV_BAR_MODE_GESTURAL;
    }

    /**
     * @return whether this nav bar mode is swipe up
     */
    public static boolean isSwipeUpMode(int mode) {
        return mode == NAV_BAR_MODE_2BUTTON;
    }

    /**
     * @return whether this nav bar mode is 3 button
     */
    public static boolean isLegacyMode(int mode) {
        return mode == NAV_BAR_MODE_3BUTTON;
    }

    /**
     * Corner radius that should be used on windows in order to cover the display.
     * These values are expressed in pixels because they should not respect display or font
     * scaling, this means that we don't have to reload them on config changes.
     */
    public static float getWindowCornerRadius(Context context) {
        return ScreenDecorationsUtils.getWindowCornerRadius(context);
    }

    /**
     * If live rounded corners are supported on windows.
     */
    public static boolean supportsRoundedCornersOnWindows(Resources resources) {
        return ScreenDecorationsUtils.supportsRoundedCornersOnWindows(resources);
    }
}
