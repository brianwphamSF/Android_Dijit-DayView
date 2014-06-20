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

package com.google.code.yadview.impl;

import java.util.Arrays;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.code.yadview.DayViewDependencyFactory;
import com.google.code.yadview.Event;
import com.google.code.yadview.EventResource;
import com.google.code.yadview.Predicate;
import com.google.code.yadview.R;
import com.google.common.collect.Lists;

public class DefaultEventResource implements EventResource {

    private static final boolean PROFILE = false;

    /**
     * The sort order is: 1) events with an earlier start (begin for normal
     * events, startday for allday) 2) events with a later end (end for normal
     * events, endday for allday) 3) the title (unnecessary, but nice) The start
     * and end day is sorted first so that all day events are sorted correctly
     * with respect to events that are >24 hours (and therefore show up in the
     * allday area).
     */
    private static final String SORT_EVENTS_BY =
            "begin ASC, end DESC, title ASC";
    private static final String SORT_ALLDAY_BY =
            "startDay ASC, endDay DESC, title ASC";
    private static final String DISPLAY_AS_ALLDAY = "dispAllday";

    private static final String ALLDAY_WHERE = DISPLAY_AS_ALLDAY + "=1";

    // The projection to use when querying instances to build a list of events
    public static final String[] EVENT_PROJECTION = new String[] {
            Instances.TITLE, // 0
            Instances.EVENT_LOCATION, // 1
            Instances.ALL_DAY, // 2
            Instances.DISPLAY_COLOR, // 3 If SDK < 16, set to
                                     // Instances.CALENDAR_COLOR.
            Instances.EVENT_TIMEZONE, // 4
            Instances.EVENT_ID, // 5
            Instances.BEGIN, // 6
            Instances.END, // 7
            Instances._ID, // 8
            Instances.START_DAY, // 9
            Instances.END_DAY, // 10
            Instances.START_MINUTE, // 11
            Instances.END_MINUTE, // 12
            Instances.HAS_ALARM, // 13
            Instances.RRULE, // 14
            Instances.RDATE, // 15
            Instances.SELF_ATTENDEE_STATUS, // 16
            Events.ORGANIZER, // 17
            Events.GUESTS_CAN_MODIFY, // 18
            Instances.ALL_DAY + "=1 OR (" + Instances.END + "-" + Instances.BEGIN + ")>="
                    + DateUtils.DAY_IN_MILLIS + " AS " + DISPLAY_AS_ALLDAY, // 19
    };

    // The indices for the projection array above.
    private static final int PROJECTION_TITLE_INDEX = 0;
    private static final int PROJECTION_LOCATION_INDEX = 1;
    private static final int PROJECTION_ALL_DAY_INDEX = 2;
    private static final int PROJECTION_COLOR_INDEX = 3;
    private static final int PROJECTION_TIMEZONE_INDEX = 4;
    private static final int PROJECTION_EVENT_ID_INDEX = 5;
    private static final int PROJECTION_BEGIN_INDEX = 6;
    private static final int PROJECTION_END_INDEX = 7;
    private static final int PROJECTION_START_DAY_INDEX = 9;
    private static final int PROJECTION_END_DAY_INDEX = 10;
    private static final int PROJECTION_START_MINUTE_INDEX = 11;
    private static final int PROJECTION_END_MINUTE_INDEX = 12;
    private static final int PROJECTION_HAS_ALARM_INDEX = 13;
    private static final int PROJECTION_RRULE_INDEX = 14;
    private static final int PROJECTION_RDATE_INDEX = 15;
    private static final int PROJECTION_SELF_ATTENDEE_STATUS_INDEX = 16;
    private static final int PROJECTION_ORGANIZER_INDEX = 17;
    private static final int PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX = 18;
    private static final int PROJECTION_DISPLAY_AS_ALLDAY = 19;

    private static final String EVENTS_WHERE = DISPLAY_AS_ALLDAY + "=0";

    private static final String TAG = "CalEvent";

    private static String mNoTitleString;
    private static int mNoColorColor;

    private static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID, // 0
            Calendars.CALENDAR_ACCESS_LEVEL, // 1
            Calendars.OWNER_ACCOUNT, // 2
    };

    private static final int CALENDARS_INDEX_ACCESS_LEVEL = 1;
    private static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    private static final String CALENDARS_WHERE = Calendars._ID + "=%d";

    
    public static final String KEY_HIDE_DECLINED = "preferences_hide_declined";

    
    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            EVENT_PROJECTION[PROJECTION_COLOR_INDEX] = Instances.CALENDAR_COLOR;
        }
    }

    private Context mContext;

    private DayViewDependencyFactory mDependencyFactory;

    public DefaultEventResource(Context ctx, DayViewDependencyFactory dependencyFactory) {
        mContext = ctx;
        mDependencyFactory = dependencyFactory;
    }

    @Override
    public List<Event> get(int startJulianDay, int numDays, Predicate continueLoading) {
        List<Event> events = Lists.newArrayList();
        if (PROFILE) {
            Debug.startMethodTracing("loadEvents");
        }

        Cursor cEvents = null;
        Cursor cAllday = null;

        try {
            int endDay = startJulianDay + numDays - 1;

            // We use the byDay instances query to get a list of all events for
            // the days we're interested in.
            // The sort order is: events with an earlier start time occur
            // first and if the start times are the same, then events with
            // a later end time occur first. The later end time is ordered
            // first so that long rectangles in the calendar views appear on
            // the left side. If the start and end times of two events are
            // the same then we sort alphabetically on the title. This isn't
            // required for correctness, it just adds a nice touch.

            // Respect the preference to show/hide declined events
            SharedPreferences prefs = mDependencyFactory.buildPreferencesUtils().getSharedPreferences(mContext);
            boolean hideDeclined = prefs.getBoolean(KEY_HIDE_DECLINED, false);

            String where = EVENTS_WHERE;
            String whereAllday = ALLDAY_WHERE;
            if (hideDeclined) {
                String hideString = " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
                        + Attendees.ATTENDEE_STATUS_DECLINED;
                where += hideString;
                whereAllday += hideString;
            }

            cEvents = instancesQuery(mContext.getContentResolver(), EVENT_PROJECTION,
                    startJulianDay,
                    endDay, where, null, SORT_EVENTS_BY);
            cAllday = instancesQuery(mContext.getContentResolver(), EVENT_PROJECTION,
                    startJulianDay,
                    endDay, whereAllday, null, SORT_ALLDAY_BY);

            // Check if we should return early because there are more recent
            // load requests waiting.
            if (!continueLoading.value()) {
                return events;
            }

            buildEventsFromCursor(events, cEvents, mContext, startJulianDay, endDay);
            buildEventsFromCursor(events, cAllday, mContext, startJulianDay, endDay);

            return events;

        } finally {
            if (cEvents != null) {
                cEvents.close();
            }
            if (cAllday != null) {
                cAllday.close();
            }
            if (PROFILE) {
                Debug.stopMethodTracing();
            }
        }
    }

    /**
     * @param cEvents Cursor pointing at event
     * @return An event created from the cursor
     */
    private Event generateEventFromCursor(Cursor cEvents) {
        Event e = new Event();

        e.setId(cEvents.getLong(PROJECTION_EVENT_ID_INDEX));
        e.setTitle(cEvents.getString(PROJECTION_TITLE_INDEX));
        e.setLocation(cEvents.getString(PROJECTION_LOCATION_INDEX));
        e.setAllDay(cEvents.getInt(PROJECTION_ALL_DAY_INDEX) != 0);
        e.setOrganizer(cEvents.getString(PROJECTION_ORGANIZER_INDEX));
        e.setGuestsCanModify(cEvents.getInt(PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX) != 0);

        if (e.getTitle() == null || e.getTitle().length() == 0) {
            e.setTitle(mNoTitleString);
        }

        if (!cEvents.isNull(PROJECTION_COLOR_INDEX)) {
            // Read the color from the database
            e.setColor(mDependencyFactory.buildRenderingUtils().getDisplayColorFromColor(cEvents.getInt(PROJECTION_COLOR_INDEX)));
        } else {
            e.setColor(mNoColorColor);
        }

        long eStart = cEvents.getLong(PROJECTION_BEGIN_INDEX);
        long eEnd = cEvents.getLong(PROJECTION_END_INDEX);

        e.setStartMillis(eStart);
        e.setStartTime(cEvents.getInt(PROJECTION_START_MINUTE_INDEX));
        e.setStartDay(cEvents.getInt(PROJECTION_START_DAY_INDEX));

        e.setEndMillis(eEnd);
        e.setEndTime(cEvents.getInt(PROJECTION_END_MINUTE_INDEX));
        e.setEndDay(cEvents.getInt(PROJECTION_END_DAY_INDEX));

        e.setHasAlarm(cEvents.getInt(PROJECTION_HAS_ALARM_INDEX) != 0);

        // Check if this is a repeating event
        String rrule = cEvents.getString(PROJECTION_RRULE_INDEX);
        String rdate = cEvents.getString(PROJECTION_RDATE_INDEX);
        if (!TextUtils.isEmpty(rrule) || !TextUtils.isEmpty(rdate)) {
            e.setRepeating(true);
        } else {
            e.setRepeating(false);
        }

        e.setSelfAttendeeStatus(cEvents.getInt(PROJECTION_SELF_ATTENDEE_STATUS_INDEX));
        return e;
    }

    /**
     * Performs a query to return all visible instances in the given range that
     * match the given selection. This is a blocking function and should not be
     * done on the UI thread. This will cause an expansion of recurring events
     * to fill this time range if they are not already expanded and will slow
     * down for larger time ranges with many recurring events.
     * 
     * @param cr The ContentResolver to use for the query
     * @param projection The columns to return
     * @param begin The start of the time range to query in UTC millis since
     *            epoch
     * @param end The end of the time range to query in UTC millis since epoch
     * @param selection Filter on the query as an SQL WHERE statement
     * @param selectionArgs Args to replace any '?'s in the selection
     * @param orderBy How to order the rows as an SQL ORDER BY statement
     * @return A Cursor of instances matching the selection
     */
    private static final Cursor instancesQuery(ContentResolver cr, String[] projection,
            int startDay, int endDay, String selection, String[] selectionArgs, String orderBy) {
        String WHERE_CALENDARS_SELECTED = Calendars.VISIBLE + "=?";
        String[] WHERE_CALENDARS_ARGS = {
            "1"
        };
        String DEFAULT_SORT_ORDER = "begin ASC";

        Uri.Builder builder = Instances.CONTENT_BY_DAY_URI.buildUpon();
        ContentUris.appendId(builder, startDay);
        ContentUris.appendId(builder, endDay);
        if (TextUtils.isEmpty(selection)) {
            selection = WHERE_CALENDARS_SELECTED;
            selectionArgs = WHERE_CALENDARS_ARGS;
        } else {
            selection = "(" + selection + ") AND " + WHERE_CALENDARS_SELECTED;
            if (selectionArgs != null && selectionArgs.length > 0) {
                selectionArgs = Arrays.copyOf(selectionArgs, selectionArgs.length + 1);
                selectionArgs[selectionArgs.length - 1] = WHERE_CALENDARS_ARGS[0];
            } else {
                selectionArgs = WHERE_CALENDARS_ARGS;
            }
        }
        return cr.query(builder.build(), projection, selection, selectionArgs,
                orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
    }

    /**
     * Adds all the events from the cursors to the events list.
     * 
     * @param events The list of events
     * @param cEvents Events to add to the list
     * @param context
     * @param startDay
     * @param endDay
     */
    public void buildEventsFromCursor(
            List<Event> events, Cursor cEvents, Context context, int startDay, int endDay) {
        if (cEvents == null || events == null) {
            Log.e(TAG, "buildEventsFromCursor: null cursor or null events list!");
            return;
        }

        int count = cEvents.getCount();

        if (count == 0) {
            return;
        }

        Resources res = context.getResources();
        mNoTitleString = res.getString(R.string.no_title_label);
        mNoColorColor = res.getColor(R.color.event_center);
        // Sort events in two passes so we ensure the allday and standard events
        // get sorted in the correct order
        cEvents.moveToPosition(-1);
        while (cEvents.moveToNext()) {
            Event e = generateEventFromCursor(cEvents);
            if (e.getStartDay() > endDay || e.getEndDay() < startDay) {
                continue;
            }
            events.add(e);
        }
    }

    @Override
    public int getEventAccessLevel(Event e) {
        ContentResolver cr = mContext.getContentResolver();

        int accessLevel = Calendars.CAL_ACCESS_NONE;

        // Get the calendar id for this event
        Cursor cursor = cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, e.getId()),
                new String[] {
                    Events.CALENDAR_ID
                },
                null /* selection */,
                null /* selectionArgs */,
                null /* sort */);

        if (cursor == null) {
            return ACCESS_LEVEL_NONE;
        }

        if (cursor.getCount() == 0) {
            cursor.close();
            return ACCESS_LEVEL_NONE;
        }

        cursor.moveToFirst();
        long calId = cursor.getLong(0);
        cursor.close();

        Uri uri = Calendars.CONTENT_URI;
        String where = String.format(CALENDARS_WHERE, calId);
        cursor = cr.query(uri, CALENDARS_PROJECTION, where, null, null);

        String calendarOwnerAccount = null;
        if (cursor != null) {
            cursor.moveToFirst();
            accessLevel = cursor.getInt(CALENDARS_INDEX_ACCESS_LEVEL);
            calendarOwnerAccount = cursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            cursor.close();
        }

        if (accessLevel < Calendars.CAL_ACCESS_CONTRIBUTOR) {
            return ACCESS_LEVEL_NONE;
        }

        if (e.isGuestsCanModify()) {
            return ACCESS_LEVEL_EDIT;
        }

        if (!TextUtils.isEmpty(calendarOwnerAccount)
                && calendarOwnerAccount.equalsIgnoreCase(e.getOrganizer())) {
            return ACCESS_LEVEL_EDIT;
        }

        return ACCESS_LEVEL_DELETE;
    }

}
