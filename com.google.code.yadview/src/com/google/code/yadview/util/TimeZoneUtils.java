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

import java.util.Formatter;
import java.util.HashSet;
import java.util.Locale;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CalendarContract.CalendarCache;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

/**
 * This class contains methods specific to reading and writing time zone
 * values.
 */
public class TimeZoneUtils {
    static final boolean DEBUG = false;
    static final String TAG = "TimezoneUtils";
    
    
    private static final String[] TIMEZONE_TYPE_ARGS = { CalendarCache.KEY_TIMEZONE_TYPE };
    private static final String[] TIMEZONE_INSTANCES_ARGS =
            { CalendarCache.KEY_TIMEZONE_INSTANCES };
    public static final String[] CALENDAR_CACHE_POJECTION = {
            CalendarCache.KEY, CalendarCache.VALUE
    };

    private static StringBuilder mSB = new StringBuilder(50);
    private static Formatter mF = new Formatter(mSB, Locale.getDefault());
    private volatile static boolean mFirstTZRequest = true;
    private volatile static boolean mTZQueryInProgress = false;

    private volatile static boolean mUseHomeTZ = false;
    private volatile static String mHomeTZ = Time.getCurrentTimezone();

    private static HashSet<Runnable> mTZCallbacks = new HashSet<Runnable>();
    private static int mToken = 1;
    private static AsyncTZHandler mHandler;

    // The name of the shared preferences file. This name must be maintained for historical
    // reasons, as it's what PreferenceManager assigned the first time the file was created.
    private final String mPrefsName;
    private PreferencesUtils mPrefUtils;

    /**
     * This is the key used for writing whether or not a home time zone should
     * be used in the Calendar app to the Calendar Preferences.
     */
    public static final String KEY_HOME_TZ_ENABLED = "preferences_home_tz_enabled";
    /**
     * This is the key used for writing the time zone that should be used if
     * home time zones are enabled for the Calendar app.
     */
    public static final String KEY_HOME_TZ = "preferences_home_tz";

    /**
     * This is a helper class for handling the async queries and updates for the
     * time zone settings in Calendar.
     */
    private class AsyncTZHandler extends AsyncQueryHandler {
        public AsyncTZHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            synchronized (mTZCallbacks) {
                if (cursor == null) {
                    mTZQueryInProgress = false;
                    mFirstTZRequest = true;
                    return;
                }

                boolean writePrefs = false;
                // Check the values in the db
                int keyColumn = cursor.getColumnIndexOrThrow(CalendarCache.KEY);
                int valueColumn = cursor.getColumnIndexOrThrow(CalendarCache.VALUE);
                while(cursor.moveToNext()) {
                    String key = cursor.getString(keyColumn);
                    String value = cursor.getString(valueColumn);
                    if (TextUtils.equals(key, CalendarCache.KEY_TIMEZONE_TYPE)) {
                        boolean useHomeTZ = !TextUtils.equals(
                                value, CalendarCache.TIMEZONE_TYPE_AUTO);
                        if (useHomeTZ != mUseHomeTZ) {
                            writePrefs = true;
                            mUseHomeTZ = useHomeTZ;
                        }
                    } else if (TextUtils.equals(
                            key, CalendarCache.KEY_TIMEZONE_INSTANCES_PREVIOUS)) {
                        if (!TextUtils.isEmpty(value) && !TextUtils.equals(mHomeTZ, value)) {
                            writePrefs = true;
                            mHomeTZ = value;
                        }
                    }
                }
                cursor.close();
                if (writePrefs) {
                    mPrefUtils.setSharedPreference((Context)cookie, KEY_HOME_TZ_ENABLED, mUseHomeTZ);
                    mPrefUtils.setSharedPreference((Context)cookie, KEY_HOME_TZ, mHomeTZ);
                }

                mTZQueryInProgress = false;
                for (Runnable callback : mTZCallbacks) {
                    if (callback != null) {
                        callback.run();
                    }
                }
                mTZCallbacks.clear();
            }
        }
    }

    /**
     * The name of the file where the shared prefs for Calendar are stored
     * must be provided. All activities within an app should provide the
     * same preferences name or behavior may become erratic.
     *
     * @param prefsName
     */
    public TimeZoneUtils(String prefsName) {
        mPrefsName = prefsName;
        mPrefUtils = new PreferencesUtils(prefsName);
    }

    /**
     * Formats a date or a time range according to the local conventions.
     *
     * This formats a date/time range using Calendar's time zone and the
     * local conventions for the region of the device.
     *
     * If the {@link DateUtils#FORMAT_UTC} flag is used it will pass in
     * the UTC time zone instead.
     *
     * @param context the context is required only if the time is shown
     * @param startMillis the start time in UTC milliseconds
     * @param endMillis the end time in UTC milliseconds
     * @param flags a bit mask of options See
     * {@link DateUtils#formatDateRange(Context, Formatter, long, long, int, String) formatDateRange}
     * @return a string containing the formatted date/time range.
     */
    public String formatDateRange(Context context, long startMillis,
            long endMillis, int flags) {
        String date;
        String tz;
        if ((flags & DateUtils.FORMAT_UTC) != 0) {
            tz = Time.TIMEZONE_UTC;
        } else {
            tz = getTimeZone(context, null);
        }
        synchronized (mSB) {
            mSB.setLength(0);
            date = DateUtils.formatDateRange(context, mF, startMillis, endMillis, flags,
                    tz).toString();
        }
        return date;
    }

    /**
     * Writes a new home time zone to the db.
     *
     * Updates the home time zone in the db asynchronously and updates
     * the local cache. Sending a time zone of
     * {@link CalendarCache#TIMEZONE_TYPE_AUTO} will cause it to be set
     * to the device's time zone. null or empty tz will be ignored.
     *
     * @param context The calling activity
     * @param timeZone The time zone to set Calendar to, or
     * {@link CalendarCache#TIMEZONE_TYPE_AUTO}
     */
    public void setTimeZone(Context context, String timeZone) {
        if (TextUtils.isEmpty(timeZone)) {
            if (DEBUG) {
                Log.d(TAG, "Empty time zone, nothing to be done.");
            }
            return;
        }
        boolean updatePrefs = false;
        synchronized (mTZCallbacks) {
            if (CalendarCache.TIMEZONE_TYPE_AUTO.equals(timeZone)) {
                if (mUseHomeTZ) {
                    updatePrefs = true;
                }
                mUseHomeTZ = false;
            } else {
                if (!mUseHomeTZ || !TextUtils.equals(mHomeTZ, timeZone)) {
                    updatePrefs = true;
                }
                mUseHomeTZ = true;
                mHomeTZ = timeZone;
            }
        }
        if (updatePrefs) {
            // Write the prefs
            mPrefUtils.setSharedPreference(context, KEY_HOME_TZ_ENABLED, mUseHomeTZ);
            mPrefUtils.setSharedPreference(context, KEY_HOME_TZ, mHomeTZ);

            // Update the db
            ContentValues values = new ContentValues();
            if (mHandler != null) {
                mHandler.cancelOperation(mToken);
            }

            mHandler = new AsyncTZHandler(context.getContentResolver());

            // skip 0 so query can use it
            if (++mToken == 0) {
                mToken = 1;
            }

            // Write the use home tz setting
            values.put(CalendarCache.VALUE, mUseHomeTZ ? CalendarCache.TIMEZONE_TYPE_HOME
                    : CalendarCache.TIMEZONE_TYPE_AUTO);
            mHandler.startUpdate(mToken, null, CalendarCache.URI, values, "key=?",
                    TIMEZONE_TYPE_ARGS);

            // If using a home tz write it to the db
            if (mUseHomeTZ) {
                ContentValues values2 = new ContentValues();
                values2.put(CalendarCache.VALUE, mHomeTZ);
                mHandler.startUpdate(mToken, null, CalendarCache.URI, values2,
                        "key=?", TIMEZONE_INSTANCES_ARGS);
            }
        }
    }

    /**
     * Gets the time zone that Calendar should be displayed in
     *
     * This is a helper method to get the appropriate time zone for Calendar. If this
     * is the first time this method has been called it will initiate an asynchronous
     * query to verify that the data in preferences is correct. The callback supplied
     * will only be called if this query returns a value other than what is stored in
     * preferences and should cause the calling activity to refresh anything that
     * depends on calling this method.
     *
     * @param context The calling activity
     * @param callback The runnable that should execute if a query returns new values
     * @return The string value representing the time zone Calendar should display
     */
    public String getTimeZone(Context context, Runnable callback) {
        synchronized (mTZCallbacks){
            if (mFirstTZRequest) {
                mTZQueryInProgress = true;
                mFirstTZRequest = false;

                SharedPreferences prefs = mPrefUtils.getSharedPreferences(context);
                mUseHomeTZ = prefs.getBoolean(KEY_HOME_TZ_ENABLED, false);
                mHomeTZ = prefs.getString(KEY_HOME_TZ, Time.getCurrentTimezone());

                // When the async query returns it should synchronize on
                // mTZCallbacks, update mUseHomeTZ, mHomeTZ, and the
                // preferences, set mTZQueryInProgress to false, and call all
                // the runnables in mTZCallbacks.
                if (mHandler == null) {
                    mHandler = new AsyncTZHandler(context.getContentResolver());
                }
                mHandler.startQuery(0, context, CalendarCache.URI, CALENDAR_CACHE_POJECTION,
                        null, null, null);
            }
            if (mTZQueryInProgress) {
                mTZCallbacks.add(callback);
            }
        }
        return mUseHomeTZ ? mHomeTZ : Time.getCurrentTimezone();
    }

    /**
     * Forces a query of the database to check for changes to the time zone.
     * This should be called if another app may have modified the db. If a
     * query is already in progress the callback will be added to the list
     * of callbacks to be called when it returns.
     *
     * @param context The calling activity
     * @param callback The runnable that should execute if a query returns
     *            new values
     */
    public void forceDBRequery(Context context, Runnable callback) {
        synchronized (mTZCallbacks){
            if (mTZQueryInProgress) {
                mTZCallbacks.add(callback);
                return;
            }
            mFirstTZRequest = true;
            getTimeZone(context, callback);
        }
    }
}