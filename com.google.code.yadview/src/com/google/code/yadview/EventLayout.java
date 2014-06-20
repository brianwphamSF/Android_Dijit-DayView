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


import java.util.ArrayList;
import java.util.Iterator;

public class EventLayout {
    
    
    private final Event mEvent;

    private int mColumn;
    private int mMaxColumns;
    // The coordinates of the event rectangle drawn on the screen.
    private float left;
    private float right;
    private float top;
    private float bottom;

    // These 4 fields are used for navigating among events within the selected
    // hour in the Day and Week view.
    private EventLayout nextRight;
    private EventLayout nextLeft;
    private EventLayout nextUp;
    private EventLayout nextDown;
    
    
    
    public EventLayout(Event e){
        mEvent = e;
    }
    
    /**
     * Computes a position for each event.  Each event is displayed
     * as a non-overlapping rectangle.  For normal events, these rectangles
     * are displayed in separate columns in the week view and day view.  For
     * all-day events, these rectangles are displayed in separate rows along
     * the top.  In both cases, each event is assigned two numbers: N, and
     * Max, that specify that this event is the Nth event of Max number of
     * events that are displayed in a group. The width and position of each
     * rectangle depend on the maximum number of rectangles that occur at
     * the same time.
     *
     * @param eventsList the list of events, sorted into increasing time order
     * @param minimumDurationMillis minimum duration acceptable as cell height of each event
     * rectangle in millisecond. Should be 0 when it is not determined.
     */
    public static void computePositions(ArrayList<EventLayout> eventsList,
            long minimumDurationMillis) {
        if (eventsList == null) {
            return;
        }

        // Compute the column positions separately for the all-day events
        doComputePositions(eventsList, minimumDurationMillis, false);
        doComputePositions(eventsList,  minimumDurationMillis, true);
    }
    
    //TODO: Allow different layout strategies
    private static void doComputePositions(ArrayList<EventLayout> eventLayouts, long minimumDurationMillis, boolean doAlldayEvents) {
        final ArrayList<EventLayout> activeList = new ArrayList<EventLayout>();
        final ArrayList<EventLayout> groupList = new ArrayList<EventLayout>();

        if (minimumDurationMillis < 0) {
            minimumDurationMillis = 0;
        }

        long colMask = 0;
        int maxCols = 0;
        for (EventLayout eventLayout : eventLayouts) {
            // Process all-day events separately
            if (eventLayout.getEvent().drawAsAllday() != doAlldayEvents)
                continue;

           if (!doAlldayEvents) {
                colMask = removeNonAlldayActiveEvents(
                        eventLayout, activeList.iterator(), minimumDurationMillis, colMask);
            } else {
                colMask = removeAlldayActiveEvents(eventLayout, activeList.iterator(), colMask);
            }

            // If the active list is empty, then reset the max columns, clear
            // the column bit mask, and empty the groupList.
            if (activeList.isEmpty()) {
                for (EventLayout ev : groupList) {
                    ev.setMaxColumns(maxCols);
                }
                maxCols = 0;
                colMask = 0;
                groupList.clear();
            }

            // Find the first empty column.  Empty columns are represented by
            // zero bits in the column mask "colMask".
            int col = findFirstZeroBit(colMask);
            if (col == 64)
                col = 63;
            colMask |= (1L << col);
            eventLayout.setColumn(col);
            activeList.add(eventLayout);
            groupList.add(eventLayout);
            int len = activeList.size();
            if (maxCols < len)
                maxCols = len;
        }
        for (EventLayout ev : groupList) {
            ev.setMaxColumns(maxCols);
        }
    }

    private static long removeAlldayActiveEvents(EventLayout event, Iterator<EventLayout> iter, long colMask) {
        // Remove the inactive allday events. An event on the active list
        // becomes inactive when the end day is less than the current event's
        // start day.
        while (iter.hasNext()) {
            final EventLayout active = iter.next();
            if (active.getEvent().getEndDay() < event.getEvent().getStartDay()) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    private static long removeNonAlldayActiveEvents(
            EventLayout eventLayout, Iterator<EventLayout> iterator, long minDurationMillis, long colMask) {
        long start = eventLayout.getEvent().getStartMillis();
        // Remove the inactive events. An event on the active list
        // becomes inactive when its end time is less than or equal to
        // the current event's start time.
        while (iterator.hasNext()) {
            final EventLayout active = iterator.next();

            final long duration = Math.max(
                    active.getEvent().getEndMillis() - active.getEvent().getStartMillis(), minDurationMillis);
            if ((active.getEvent().getStartMillis() + duration) <= start) {
                colMask &= ~(1L << active.getColumn());
                iterator.remove();
            }
        }
        return colMask;
    }

    public static int findFirstZeroBit(long val) {
        for (int ii = 0; ii < 64; ++ii) {
            if ((val & (1L << ii)) == 0)
                return ii;
        }
        return 64;
    }

    public Event getEvent() {
        return mEvent;
    }

    public int getColumn() {
        return mColumn;
    }

    public void setColumn(int column) {
        mColumn = column;
    }

    public int getMaxColumns() {
        return mMaxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        mMaxColumns = maxColumns;
    }

    public float getLeft() {
        return left;
    }

    public void setLeft(float left) {
        this.left = left;
    }

    public float getRight() {
        return right;
    }

    public void setRight(float right) {
        this.right = right;
    }

    public float getTop() {
        return top;
    }

    public void setTop(float top) {
        this.top = top;
    }

    public float getBottom() {
        return bottom;
    }

    public void setBottom(float bottom) {
        this.bottom = bottom;
    }

    public EventLayout getNextRight() {
        return nextRight;
    }

    public void setNextRight(EventLayout nextRight) {
        this.nextRight = nextRight;
    }

    public EventLayout getNextLeft() {
        return nextLeft;
    }

    public void setNextLeft(EventLayout nextLeft) {
        this.nextLeft = nextLeft;
    }

    public EventLayout getNextUp() {
        return nextUp;
    }

    public void setNextUp(EventLayout nextUp) {
        this.nextUp = nextUp;
    }

    public EventLayout getNextDown() {
        return nextDown;
    }

    public void setNextDown(EventLayout nextDown) {
        this.nextDown = nextDown;
    }
    

}
