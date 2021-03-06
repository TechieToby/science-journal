/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.review;

import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ZoomRecorder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Controls the zoom level at which to load data points into a line graph, based on the zoom levels
 * available and the ideal number of data points to display
 */
public class ZoomPresenter {
    // Experimentally, this seems to produce decent results on Nexus 5x.  We could adjust.
    private static final int IDEAL_NUMBER_OF_DISPLAYED_DATAPOINTS = 500;

    /**
     * How far does our ideal zoom level need to be from the current zoom level before we change?
     * Current zoom level is always an int, so a value here of 0.5 means that we always switch to
     * the ideal zoom level, and ignore the current level.  The downside is that if one is close
     * to the midpoint, and keeps zooming back and forth, every small change triggers a reload.
     *
     * Too big a value here, and data can get noticeably sparse when zooming in before a reload.
     *
     * Experimentally, 0.6 seems a decent compromise at the moment.
     */
    private static final double THRESHOLD_TO_CHANGE_ZOOM_LEVEL = 0.6;

    private static final String TAG = "ZoomPresenter";

    private final int mIdealNumberOfDisplayedDatapoints;
    private RunStats mRunStats;
    private int mCurrentTier;

    public ZoomPresenter() {
        this(IDEAL_NUMBER_OF_DISPLAYED_DATAPOINTS);
    }

    @VisibleForTesting
    public ZoomPresenter(int idealNumberOfDisplayedDatapoints) {
        mIdealNumberOfDisplayedDatapoints = idealNumberOfDisplayedDatapoints;
    }

    public void setRunStats(RunStats stats) {
        mRunStats = stats;
    }

    public int updateTier(long loadedRange) {
        mCurrentTier = computeTier(mCurrentTier, mIdealNumberOfDisplayedDatapoints, mRunStats,
                loadedRange);
        return mCurrentTier;
    }

    public int getCurrentTier() {
        return mCurrentTier;
    }

    @VisibleForTesting
    public static int computeTier(int currentTier, int idealNumberOfDisplayedDatapoints,
            RunStats runStats, long loadedRange) {
        if (!hasRequiredStats(runStats)) {
            // Must be an old run from before we started saving zoom info, so only base tier exists.
            return 0;
        }

        double idealTier = computeIdealTier(idealNumberOfDisplayedDatapoints, runStats,
                loadedRange);

        if (Math.abs(idealTier - currentTier) < THRESHOLD_TO_CHANGE_ZOOM_LEVEL) {
            return currentTier;
        }

        int actualTier = (int) Math.round(idealTier);
        if (actualTier < 0) {
            actualTier = 0;
        }
        int maxTier = runStats.getIntStat(ZoomRecorder.STATS_KEY_TIER_COUNT) - 1;
        if (actualTier > maxTier) {
            actualTier = maxTier;
        }

        return actualTier;
    }

    @VisibleForTesting
    public static double computeIdealTier(int idealNumberOfDisplayedDatapoints,
            RunStats runStats, long loadedRange) {
        double meanMillisPerDataPoint = runStats.getStat(StatsAccumulator.KEY_TOTAL_DURATION)
                / runStats.getStat(StatsAccumulator.KEY_NUM_DATA_POINTS);
        double expectedTierZeroDatapointsInRange = loadedRange / meanMillisPerDataPoint;
        double idealTierZeroDatapointsPerDisplayedPoint =
                expectedTierZeroDatapointsInRange / idealNumberOfDisplayedDatapoints;

        int zoomLevelBetweenTiers = runStats.getIntStat(
                ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS);
        return Math.log(idealTierZeroDatapointsPerDisplayedPoint) / Math.log(
                zoomLevelBetweenTiers);
    }

    private static boolean hasRequiredStats(RunStats stats) {
        return stats.hasStat(StatsAccumulator.KEY_TOTAL_DURATION) && stats.hasStat(
                StatsAccumulator.KEY_NUM_DATA_POINTS) && stats.hasStat(
                ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS) && stats.hasStat(
                ZoomRecorder.STATS_KEY_TIER_COUNT);
    }
}