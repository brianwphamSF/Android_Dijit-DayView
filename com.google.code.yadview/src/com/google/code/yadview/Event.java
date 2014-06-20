/*
 * Copyright 2013 Chris Pope
 * 
 * Copyright (C) 2007 The Android Open Source Project
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

package com.google.code.yadview;

import android.provider.CalendarContract.Attendees;
import android.text.format.DateUtils;
import android.util.Log;

// TODO: should Event be Parcelable so it can be passed via Intents?
public class Event implements Cloneable {


    private long id;
    private int color;
    private CharSequence title;
    private CharSequence location;
    private boolean allDay;
    private String organizer;
    private boolean guestsCanModify;

    private int startDay;       // start Julian day
    private int endDay;         // end Julian day
    private int startTime;      // Start and end time are in minutes since midnight
    private int endTime;

    private long startMillis;   // UTC milliseconds since the epoch
    private long endMillis;     // UTC milliseconds since the epoch
    
    private double dStartTime;      // Start and end time are in minutes since midnight
    private double dEndTime;
    
    private boolean hasAlarm;
    private boolean isRepeating;

    private int selfAttendeeStatus;


    
    public Event() {
        setId(0);
        setTitle(null);
        setColor(0);
        setLocation(null);
        setAllDay(false);
        setStartDay(0);
        setEndDay(0);
        setStartTime(0);
        setEndTime(0);
        setStartMillis(0);
        setEndMillis(0);
        setHasAlarm(false);
        setRepeating(false);
        setSelfAttendeeStatus(Attendees.ATTENDEE_STATUS_NONE);    
    }
    

    @Override
    public final Object clone() throws CloneNotSupportedException {
        super.clone();
        Event e = new Event();

        e.setTitle(title);
        e.setColor(color);
        e.setLocation(location);
        e.setAllDay(allDay);
        e.setStartDay(startDay);
        e.setEndDay(endDay);
        e.setStartTime(startTime);
        e.setEndTime(endTime);
        e.setStartMillis(startMillis);
        e.setEndMillis(endMillis);
        e.setHasAlarm(hasAlarm);
        e.setRepeating(isRepeating);
        e.setSelfAttendeeStatus(selfAttendeeStatus);
        e.setOrganizer(organizer);
        e.setGuestsCanModify(guestsCanModify);

        return e;
    }

    public final void copyTo(Event dest) {
        dest.setId(id);
        dest.setTitle(title);
        dest.setColor(color);
        dest.setLocation(location);
        dest.setAllDay(allDay);
        dest.setStartDay(startDay);
        dest.setEndDay(endDay);
        dest.setStartTime(startTime);
        dest.setEndTime(endTime);
        dest.setStartMillis(startMillis);
        dest.setEndMillis(endMillis);
        dest.setHasAlarm(hasAlarm);
        dest.setRepeating(isRepeating);
        dest.setSelfAttendeeStatus(selfAttendeeStatus);
        dest.setOrganizer(organizer);
        dest.setGuestsCanModify(guestsCanModify);
    }



    public final void dump() {
        Log.e("Cal", "+-----------------------------------------+");
        Log.e("Cal", "+        id = " + getId());
        Log.e("Cal", "+     color = " + getColor());
        Log.e("Cal", "+     title = " + getTitle());
        Log.e("Cal", "+  location = " + getLocation());
        Log.e("Cal", "+    allDay = " + isAllDay());
        Log.e("Cal", "+  startDay = " + getStartDay());
        Log.e("Cal", "+    endDay = " + getEndDay());
        Log.e("Cal", "+ startTime = " + getStartTime());
        Log.e("Cal", "+   endTime = " + getEndTime());
        Log.e("Cal", "+ organizer = " + getOrganizer());
        Log.e("Cal", "+  guestwrt = " + isGuestsCanModify());
    }

    public final boolean intersects(int julianDay, int startMinute,
            int endMinute) {
        if (getEndDay() < julianDay) {
            return false;
        }

        if (getStartDay() > julianDay) {
            return false;
        }

        if (getEndDay() == julianDay) {
            if (getEndTime() < startMinute) {
                return false;
            }
            // An event that ends at the start minute should not be considered
            // as intersecting the given time span, but don't exclude
            // zero-length (or very short) events.
            if (getEndTime() == startMinute
                    && (getStartTime() != getEndTime() || getStartDay() != getEndDay())) {
                return false;
            }
        }

        if (getStartDay() == julianDay && getStartTime() > endMinute) {
            return false;
        }

        return true;
    }

    /**
     * Returns the event title and location separated by a comma.  If the
     * location is already part of the title (at the end of the title), then
     * just the title is returned.
     *
     * @return the event title and location as a String
     */
    public String getTitleAndLocation() {
        String text = getTitle().toString();

        // Append the location to the title, unless the title ends with the
        // location (for example, "meeting in building 42" ends with the
        // location).
        if (getLocation() != null) {
            String locationString = getLocation().toString();
            if (!text.endsWith(locationString)) {
                text += ", " + locationString;
            }
        }
        return text;
    }

    public boolean drawAsAllday() {
        // Use >= so we'll pick up Exchange allday events
        return isAllDay() || getEndMillis() - getStartMillis() >= DateUtils.DAY_IN_MILLIS;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public CharSequence getTitle() {
        return title;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
    }

    public CharSequence getLocation() {
        return location;
    }

    public void setLocation(CharSequence location) {
        this.location = location;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public void setAllDay(boolean allDay) {
        this.allDay = allDay;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public boolean isGuestsCanModify() {
        return guestsCanModify;
    }

    public void setGuestsCanModify(boolean guestsCanModify) {
        this.guestsCanModify = guestsCanModify;
    }

    public int getStartDay() {
        return startDay;
    }

    public void setStartDay(int startDay) {
        this.startDay = startDay;
    }

    public int getEndDay() {
        return endDay;
    }

    public void setEndDay(int endDay) {
        this.endDay = endDay;
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }
    
    public double getDStartTime() {
    	return dStartTime;
    }
    
    public void setDStartTime(double dStartTime) {
    	this.dStartTime = dStartTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }
    
    public double getDEndTime() {
    	return dEndTime;
    }
    
    public void setDEndTime(double dEndTime) {
    	this.dEndTime = dEndTime;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }

    public boolean isHasAlarm() {
        return hasAlarm;
    }

    public void setHasAlarm(boolean hasAlarm) {
        this.hasAlarm = hasAlarm;
    }

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean isRepeating) {
        this.isRepeating = isRepeating;
    }

    public int getSelfAttendeeStatus() {
        return selfAttendeeStatus;
    }

    public void setSelfAttendeeStatus(int selfAttendeeStatus) {
        this.selfAttendeeStatus = selfAttendeeStatus;
    }
}
