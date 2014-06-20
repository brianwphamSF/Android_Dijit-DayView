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

package com.google.code.yadview.util;

import java.util.Calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.Time;

public class CalendarDateUtils {

    public static final String KEY_WEEK_START_DAY = "preferences_week_start_day";
    public static final String WEEK_START_DEFAULT = "-1";
    private PreferencesUtils mPrefUtils;

    public CalendarDateUtils(PreferencesUtils prefUtils){
        mPrefUtils = prefUtils;
    }
    
    public boolean isSaturday(int dayOfWeek, int firstDayOfWeek) {
        return (firstDayOfWeek == Time.SUNDAY && dayOfWeek == 6)
                || (firstDayOfWeek == Time.MONDAY && dayOfWeek == 5)
                || (firstDayOfWeek == Time.SATURDAY && dayOfWeek == 0);
    }

    public boolean isSunday(int dayOfWeek, int firstDayOfWeek) {
        return (firstDayOfWeek == Time.SUNDAY && dayOfWeek == 0)
                || (firstDayOfWeek == Time.MONDAY &&dayOfWeek == 6)
                || (firstDayOfWeek == Time.SATURDAY && dayOfWeek == 1);

    }

    public int getFirstDayOfWeek(Context context) {
        SharedPreferences prefs = mPrefUtils.getSharedPreferences(context);
        String pref = prefs.getString(KEY_WEEK_START_DAY, WEEK_START_DEFAULT);

        int startDay;
        if (WEEK_START_DEFAULT.equals(pref)) {
            startDay = Calendar.getInstance().getFirstDayOfWeek();
        } else {
            startDay = Integer.parseInt(pref);
        }

        if (startDay == Calendar.SATURDAY) {
            return Time.SATURDAY;
        } else if (startDay == Calendar.MONDAY) {
            return Time.MONDAY;
        } else {
            return Time.SUNDAY;
        }
    }

}
