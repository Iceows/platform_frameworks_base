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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Rect;
import android.service.notification.StatusBarNotification;
import android.util.EventLog;
import android.util.Log;
import android.view.View;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.ExpandableNotificationRow;
import com.android.systemui.statusbar.HeadsUpStatusBarView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.OnHeadsUpChangedListener;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;

import java.util.stream.Stream;

/**
 * Controls the appearance of heads up notifications in the icon area and the header itself.
 */
class HeadsUpAppearanceController implements OnHeadsUpChangedListener,
        DarkIconDispatcher.DarkReceiver {
    private final NotificationIconAreaController mNotificationIconAreaController;
    private final HeadsUpManagerPhone mHeadsUpManager;
    private final NotificationStackScrollLayout mStackScroller;
    private final HeadsUpStatusBarView mHeadsUpStatusBarView;
    private final View mClockView;
    private final DarkIconDispatcher mDarkIconDispatcher;
    private float mExpandedHeight;
    private boolean mIsExpanded;
    private float mExpandFraction;
    private ExpandableNotificationRow mTrackedChild;
    private boolean mShown;

    public HeadsUpAppearanceController(
            NotificationIconAreaController notificationIconAreaController,
            HeadsUpManagerPhone headsUpManager,
            View statusbarView) {
        this(notificationIconAreaController, headsUpManager,
                statusbarView.findViewById(R.id.heads_up_status_bar_view),
                statusbarView.findViewById(R.id.notification_stack_scroller),
                statusbarView.findViewById(R.id.notification_panel),
                statusbarView.findViewById(R.id.clock));
    }

    @VisibleForTesting
    public HeadsUpAppearanceController(
            NotificationIconAreaController notificationIconAreaController,
            HeadsUpManagerPhone headsUpManager,
            HeadsUpStatusBarView headsUpStatusBarView,
            NotificationStackScrollLayout stackScroller,
            NotificationPanelView panelView,
            View clockView) {
        mNotificationIconAreaController = notificationIconAreaController;
        mHeadsUpManager = headsUpManager;
        mHeadsUpManager.addListener(this);
        mHeadsUpStatusBarView = headsUpStatusBarView;
        mStackScroller = stackScroller;
        panelView.addTrackingHeadsUpListener(this::setTrackingHeadsUp);
        mStackScroller.addOnExpandedHeightListener(this::setExpandedHeight);
        mClockView = clockView;
        mDarkIconDispatcher = Dependency.get(DarkIconDispatcher.class);
        mDarkIconDispatcher.addDarkReceiver(this);
    }

    @Override
    public void onHeadsUpPinned(ExpandableNotificationRow headsUp) {
        updateTopEntry();
        updateHeader(headsUp.getEntry());
    }

    private void updateTopEntry() {
        NotificationData.Entry newEntry = null;
        if (!mIsExpanded && mHeadsUpManager.hasPinnedHeadsUp()) {
            newEntry = mHeadsUpManager.getTopEntry();
        }
        NotificationData.Entry previousEntry = mHeadsUpStatusBarView.getShowingEntry();
        mHeadsUpStatusBarView.setEntry(newEntry);
        if (newEntry != previousEntry) {
            if (newEntry == null) {
                // no heads up anymore, lets start the disappear animation

                setShown(false);
            } else if (previousEntry == null) {
                // We now have a headsUp and didn't have one before. Let's start the disappear
                // animation
                setShown(true);
            }
            mNotificationIconAreaController.showIconIsolated(newEntry == null ? null
                    : newEntry.icon, mHeadsUpStatusBarView.getIconDrawingRect());
        }
    }

    private void setShown(boolean isShown) {
        mShown = isShown;
        mHeadsUpStatusBarView.setVisibility(isShown ? View.VISIBLE : View.GONE);
        mClockView.setVisibility(!isShown ? View.VISIBLE : View.INVISIBLE);
    }

    @VisibleForTesting
    public boolean isShown() {
        return mShown;
    }

    @Override
    public void onHeadsUpUnPinned(ExpandableNotificationRow headsUp) {
        updateTopEntry();
        updateHeader(headsUp.getEntry());
    }

    public void setExpandedHeight(float expandedHeight, float appearFraction) {
        boolean changedHeight = expandedHeight != mExpandedHeight;
        mExpandedHeight = expandedHeight;
        mExpandFraction = appearFraction;
        boolean isExpanded = expandedHeight > 0;
        if (changedHeight) {
            updateHeadsUpHeaders();
        }
        if (isExpanded != mIsExpanded) {
            mIsExpanded = isExpanded;
            updateTopEntry();
        }
    }

    /**
     * Set a headsUp to be tracked, meaning that it is currently being pulled down after being
     * in a pinned state on the top. The expand animation is different in that case and we need
     * to update the header constantly afterwards.
     *
     * @param trackedChild the tracked headsUp or null if it's not tracking anymore.
     */
    public void setTrackingHeadsUp(ExpandableNotificationRow trackedChild) {
        ExpandableNotificationRow previousTracked = mTrackedChild;
        mTrackedChild = trackedChild;
        if (previousTracked != null) {
            updateHeader(previousTracked.getEntry());
        }
    }

    private void updateHeadsUpHeaders() {
        mHeadsUpManager.getAllEntries().forEach(entry -> {
            updateHeader(entry);
        });
    }

    private void updateHeader(NotificationData.Entry entry) {
        ExpandableNotificationRow row = entry.row;
        float headerVisibleAmount = 1.0f;
        if (row.isPinned() || row == mTrackedChild) {
            headerVisibleAmount = mExpandFraction;
        }
        row.setHeaderVisibleAmount(headerVisibleAmount);
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mHeadsUpStatusBarView.onDarkChanged(area, darkIntensity, tint);
    }

    public void setPublicMode(boolean publicMode) {
        mHeadsUpStatusBarView.setPublicMode(publicMode);
        updateTopEntry();
    }
}
