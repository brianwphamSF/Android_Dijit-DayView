/*
Copyright 2013 Chris Pope

Copyright (C) 2007 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 
 */


package com.google.code.yadview;

import com.google.code.yadview.events.CreateEventEvent;
import com.google.code.yadview.impl.DefaultDayViewFactory;
import com.google.code.yadview.impl.DefaultDayViewResources;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnLongClickListener;

public class DayViewOnLongClickListener implements OnLongClickListener {

    private Context mContext;
    private DayView mDayView;
    private DayViewResources mDayViewResources;
    private DefaultDayViewFactory mDependencyFactory;

    public DayViewOnLongClickListener(Context context, DayView dv, DayViewResources resources, DefaultDayViewFactory defaultDayViewFactory) {
        mContext = context;
        mDayView = dv;
        mDependencyFactory = defaultDayViewFactory;
        mDayViewResources = resources;
    }

    @Override
    public boolean onLongClick(View v) {
        int flags = DateUtils.FORMAT_SHOW_WEEKDAY;
        long time = mDayView.getSelectedTimeInMillis();
        if (!mDayView.isSelectionAllday()) {
            flags |= DateUtils.FORMAT_SHOW_TIME;
        }
        if (DateFormat.is24HourFormat(mContext)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }

        new AlertDialog.Builder(mContext).setTitle(mDependencyFactory.buildTimezoneUtils().formatDateRange(mContext, time, time,
                flags))
                .setItems(mDayViewResources.getLongPressItems(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            mDayView.getEventBus().post(new CreateEventEvent(mDayView.getSelectedTimeInMillis(), mDayView.isSelectionAllday()));
                        }
                    }
                }).show().setCanceledOnTouchOutside(true);
        return true;
    }

}
