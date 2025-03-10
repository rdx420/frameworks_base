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

package com.android.server.wm;

import static com.android.server.wm.WindowContainer.AnimationFlags.PARENTS;
import static com.android.server.wm.WindowContainer.AnimationFlags.TRANSITION;

import android.hardware.display.DisplayManagerInternal.RefreshRateRange;
import android.content.res.Configuration;
import android.provider.Settings;
import android.util.Slog;
import android.view.Display;
import android.view.Display.Mode;
import android.view.DisplayInfo;
import java.util.HashMap;

/**
 * Policy to select a lower/higher refresh rate for the display if applicable.
 */
class RefreshRatePolicy {

    class PackageRefreshRate {
        private final HashMap<String, RefreshRateRange> mPackages = new HashMap<>();

        public void add(String s, float minRefreshRate, float maxRefreshRate) {
            float minSupportedRefreshRate =
                    Math.max(RefreshRatePolicy.this.mMinSupportedRefreshRate, minRefreshRate);
            float maxSupportedRefreshRate =
                    Math.min(RefreshRatePolicy.this.mMaxSupportedRefreshRate, maxRefreshRate);

            mPackages.put(s,
                    new RefreshRateRange(minSupportedRefreshRate, maxSupportedRefreshRate));
        }

        public RefreshRateRange get(String s) {
            return mPackages.get(s);
        }

        public void remove(String s) {
            mPackages.remove(s);
        }
    }

    private final Mode mLowRefreshRateMode;
    private final Mode mHighRefreshRateMode;
    private final PackageRefreshRate mNonHighRefreshRatePackages = new PackageRefreshRate();
    private final HighRefreshRateDenylist mHighRefreshRateDenylist;
    private final WindowManagerService mWmService;
    private float mMinSupportedRefreshRate;
    private float mMaxSupportedRefreshRate;

    /**
     * The following constants represent priority of the window. SF uses this information when
     * deciding which window has a priority when deciding about the refresh rate of the screen.
     * Priority 0 is considered the highest priority. -1 means that the priority is unset.
     */
    static final int LAYER_PRIORITY_UNSET = -1;
    /** Windows that are in focus and voted for the preferred mode ID have the highest priority. */
    static final int LAYER_PRIORITY_FOCUSED_WITH_MODE = 0;
    /**
     * This is a default priority for all windows that are in focus, but have not requested a
     * specific mode ID.
     */
    static final int LAYER_PRIORITY_FOCUSED_WITHOUT_MODE = 1;
    /**
     * Windows that are not in focus, but voted for a specific mode ID should be
     * acknowledged by SF. For example, there are two applications in a split screen.
     * One voted for a given mode ID, and the second one doesn't care. Even though the
     * second one might be in focus, we can honor the mode ID of the first one.
     */
    static final int LAYER_PRIORITY_NOT_FOCUSED_WITH_MODE = 2;

    RefreshRatePolicy(WindowManagerService wmService, DisplayInfo displayInfo,
            HighRefreshRateDenylist denylist) {
        mLowRefreshRateMode = findLowRefreshRateMode(displayInfo);
        mHighRefreshRateMode = findHighRefreshRateMode(displayInfo);
        mHighRefreshRateDenylist = denylist;
        mWmService = wmService;
    }

    /**
     * Finds the mode id with the lowest refresh rate which is >= 60hz and same resolution as the
     * default mode.
     */
    private Mode findLowRefreshRateMode(DisplayInfo displayInfo) {
        Mode mode = displayInfo.getDefaultMode();
        float[] refreshRates = displayInfo.getDefaultRefreshRates();
        float bestRefreshRate = mode.getRefreshRate();
        mMinSupportedRefreshRate = bestRefreshRate;
        mMaxSupportedRefreshRate = bestRefreshRate;
        for (int i = refreshRates.length - 1; i >= 0; i--) {
            mMinSupportedRefreshRate = Math.min(mMinSupportedRefreshRate, refreshRates[i]);
            mMaxSupportedRefreshRate = Math.max(mMaxSupportedRefreshRate, refreshRates[i]);

            if (refreshRates[i] >= 60f && refreshRates[i] < bestRefreshRate) {
                bestRefreshRate = refreshRates[i];
            }
        }
        return displayInfo.findDefaultModeByRefreshRate(bestRefreshRate);
    }

    /**
     * Finds the mode id with the highest refresh rate which is same resolution as the
     * default mode.
     */
    private Mode findHighRefreshRateMode(DisplayInfo displayInfo) {
        Mode mode = displayInfo.getDefaultMode();
        float[] refreshRates = displayInfo.getDefaultRefreshRates();
        float bestRefreshRate = mode.getRefreshRate();
        for (int i = refreshRates.length - 1; i >= 0; i--) {
            if (refreshRates[i] > bestRefreshRate) {
                bestRefreshRate = refreshRates[i];
            }
        }
        return displayInfo.findDefaultModeByRefreshRate(bestRefreshRate);
    }

    void addRefreshRateRangeForPackage(String packageName,
            float minRefreshRate, float maxRefreshRate) {
        mNonHighRefreshRatePackages.add(packageName, minRefreshRate, maxRefreshRate);
        mWmService.requestTraversal();
    }

    void removeRefreshRateRangeForPackage(String packageName) {
        mNonHighRefreshRatePackages.remove(packageName);
        mWmService.requestTraversal();
    }

    int getPreferredModeId(WindowState w) {
        // If app is animating, it's not able to control refresh rate because we want the animation
        // to run in default refresh rate.
        if (w.isAnimating(TRANSITION | PARENTS)) {
            return 0;
        }

        return w.mAttrs.preferredDisplayModeId;
    }

    /**
     * Calculate the priority based on whether the window is in focus and whether the application
     * voted for a specific refresh rate.
     *
     * TODO(b/144307188): This is a very basic algorithm version. Explore other signals that might
     * be useful in edge cases when we are deciding which layer should get priority when deciding
     * about the refresh rate.
     */
    int calculatePriority(WindowState w) {
        boolean isFocused = w.isFocused();
        int preferredModeId = getPreferredModeId(w);

        if (!isFocused && preferredModeId > 0) {
            return LAYER_PRIORITY_NOT_FOCUSED_WITH_MODE;
        }
        if (isFocused && preferredModeId == 0) {
            return LAYER_PRIORITY_FOCUSED_WITHOUT_MODE;
        }
        if (isFocused && preferredModeId > 0) {
            return LAYER_PRIORITY_FOCUSED_WITH_MODE;
        }
        return LAYER_PRIORITY_UNSET;
    }

    float getPreferredRefreshRate(WindowState w) {
        // If app is animating, it's not able to control refresh rate because we want the animation
        // to run in default refresh rate.
        if (w.isAnimating(TRANSITION | PARENTS)) {
            return 0;
        }

        // If the app set a preferredDisplayModeId, the preferred refresh rate is the refresh rate
        // of that mode id.
        final int preferredModeId = w.mAttrs.preferredDisplayModeId;
        if (preferredModeId > 0) {
            DisplayInfo info = w.getDisplayInfo();
            if (info != null) {
                for (Display.Mode mode : info.supportedModes) {
                    if (preferredModeId == mode.getModeId()) {
                        return mode.getRefreshRate();
                    }
                }
            }
        }

        if(w.getOwningPackage() != null && !w.taskInPipMode()) {
            final int refreshRateToForce = mWmService.getRefreshRateModeForPkg(w.getOwningPackage());
            if(refreshRateToForce == 1) {
                if(w.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    return mLowRefreshRateMode.getRefreshRate();
                }
            } else if(refreshRateToForce == 2) {
                return mLowRefreshRateMode.getRefreshRate();
            } else if(refreshRateToForce == 3) {
                return mHighRefreshRateMode.getRefreshRate();
            }
        }

        if (w.mAttrs.preferredRefreshRate > 0) {
            return w.mAttrs.preferredRefreshRate;
        }

        // If the app didn't set a preferred mode id or refresh rate, but it is part of the deny
        // list, we return the low refresh rate as the preferred one.
        final String packageName = w.getOwningPackage();
        if (mHighRefreshRateDenylist.isDenylisted(packageName)) {
            return mLowRefreshRateMode.getRefreshRate();
        }

        return 0;
    }

    float getPreferredMinRefreshRate(WindowState w) {
        // If app is animating, it's not able to control refresh rate because we want the animation
        // to run in default refresh rate.
        if (w.isAnimating(TRANSITION | PARENTS)) {
            return 0;
        }

        if (w.mAttrs.preferredMinDisplayRefreshRate > 0) {
            return w.mAttrs.preferredMinDisplayRefreshRate;
        }

        String packageName = w.getOwningPackage();
        // If app is using Camera, we set both the min and max refresh rate to the camera's
        // preferred refresh rate to make sure we don't end up with a refresh rate lower
        // than the camera capture rate, which will lead to dropping camera frames.
        RefreshRateRange range = mNonHighRefreshRatePackages.get(packageName);
        if (range != null) {
            return range.min;
        }
            
        if(packageName != null && !w.taskInPipMode()) {
            final int refreshRateToForce = mWmService.getRefreshRateModeForPkg(packageName);
            if(refreshRateToForce == 2) {
                return mLowRefreshRateMode.getRefreshRate();
            } else if(refreshRateToForce == 3) {
                return mHighRefreshRateMode.getRefreshRate();
            }
        }

        return 0;
    }

    float getPreferredMaxRefreshRate(WindowState w) {
        // If app is animating, it's not able to control refresh rate because we want the animation
        // to run in default refresh rate.
        if (w.isAnimating(TRANSITION | PARENTS)) {
            return 0;
        }

        if (w.mAttrs.preferredMaxDisplayRefreshRate > 0) {
            return w.mAttrs.preferredMaxDisplayRefreshRate;
        }

        final String packageName = w.getOwningPackage();
        // If app is using Camera, force it to default (lower) refresh rate.
        RefreshRateRange range = mNonHighRefreshRatePackages.get(packageName);
        if (range != null) {
            return range.max;
        }

        if(packageName != null && !w.taskInPipMode()) {
            final int refreshRateToForce = mWmService.getRefreshRateModeForPkg(packageName);
            if(refreshRateToForce == 1) {
                if(w.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    return mLowRefreshRateMode.getRefreshRate();
                }
            } else if(refreshRateToForce == 2) {
                return mLowRefreshRateMode.getRefreshRate();
            } else if(refreshRateToForce == 3) {
                return mHighRefreshRateMode.getRefreshRate();
            }
        }

        return 0;
    }
}
