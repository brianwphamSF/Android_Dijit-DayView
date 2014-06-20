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

import android.graphics.drawable.Drawable;

public interface DayViewResources {
//    public String getNoTitleEventTitle();
//    public int getNoColourEventColour();
    public String[] get12HoursNoAmPm();
    public String[] get24Hours();
    public abstract int getEventAlldayTextRightMargin();
    public abstract int getEventAllDayTextLeftMargin();
    public abstract int getEventTextRightMargin();
    public abstract int getEventTextLeftMargin();
    public abstract int getDayHeaderFontSize();
    public abstract int getEventAllDayTextBottomMargin();
    public abstract int getEventAllDayTextTopMargin();
    public abstract int getEventTextBottomMargin();
    public abstract int getEventTextTopMargin();
    public abstract float getMinUnexpandedAllDayEventHeight();
    
    // smallest height to draw an event with
    public abstract float getMinEventHeight();
    
    // sizing for "box +n" in allDay events
    public abstract int getNewEventHintTextFontSize();
    
    /**
     * The height of the day names/numbers for multi-day views
     */
    public abstract int getMultiDayHeaderHeight();
    
    public abstract int getHoursRightMargin();
    public abstract int getHoursLeftMargin();
    public abstract int getMinHoursWidth();
    public abstract int getAMPMTextSize();
    public abstract int getHoursTextSize();
    
    // margins and sizing for the expand allday icon
    public abstract int getExpandAllDayBottomMargin();
    public abstract int getDayHeaderBottomMargin();
    
    /**
     * The height of the day names/numbers when viewing a single day
     */
    public abstract int getOneDayHeaderHeight();
    public abstract int getDateHeaderFontSize();
    public abstract String getNewEventHintString();
    public abstract String getCreateNewEventString();
    public abstract int getHoursMargin();
    public abstract int getEventTextFontSize(int numDays);
    public abstract int getDayHeaderHeight(int numDays);
    public abstract CharSequence[] getLongPressItems();
    public abstract Drawable getAcceptedOrTentativeEventBoxDrawable();
    public abstract int getNewEventHintColor();
    public abstract Drawable getCollapseAlldayDrawable();
    public abstract Drawable getExpandAlldayDrawable();
    public abstract Drawable getTodayHeaderDrawable();
    public abstract Drawable getCurrentTimeAnimateLine();
    public abstract Drawable getCurrentTimeLine();
    public abstract int getMoreEventsTextColor();
    public abstract int getEventTextColor();
    public abstract int getClickedColor();
    public abstract int getPressedColor();
    public abstract int getCalendarHourLabelColor();
    public abstract int getCalendarGridLineInnerVerticalColor();
    public abstract int getCalendarGridLineInnerHorizontalColor();
    public abstract int getCalendarGridAreaSelected();
    public abstract int getCalendarAmPmLabel();
    public abstract int getBgColor();
    public abstract int getFutureBgColorRes();
    public abstract int getCalendarDateBannerTextColor();
    public abstract int getWeekSundayColor();
    public abstract int getWeekSaturdayColor();
    public abstract String getEventCountTemplate();
    public abstract int getGridLineColor();
    public int getEventPopupViewLayoutID();
    public String getMoreEventsMonthText(int remainingEvents);
    public CharSequence getViewEventMenuItemLabel();
    public CharSequence getEditEventMenuItemLabel();
    public CharSequence getDeleteEventMenuItemLabel();
    public CharSequence getCreateEventMenuItemLabel();
    public CharSequence getShowDayViewMenuItemLabel();
    
    
    public void resetDisplayDensity();
    public abstract int getNewEventMaxLength();
    public abstract int getNewEventWidth();
    public abstract int getNewEventMargin();
    public abstract int getEventLinePadding();
    public abstract int getEventSquareWidth();
    public abstract int getAllDayEventRectBottomMargin();
    public abstract int getEventRectStrokeWidth();
    public abstract int getEventRectRightMargin();
    public abstract int getEventRectLeftMargin();
    public abstract int getEventRectBottomMargin();
    public abstract int getEventRectTopMargin();
    public abstract int getCalendarColorSquareSize();
    public abstract int getDayHeaderRightMargin();
    public abstract int getDayHeaderOneDayBottomMargin();
    public abstract int getDayHeaderOneDayRightMargin();
    public abstract int getDayHeaderOneDayLeftMargin();
    public abstract int getDayHeaderHeight();
    public abstract int getMinYSpan();
    public abstract int getMaxCellHeight();
    public abstract int getDefaultCellHeight();
    public abstract int getCurrentTimeLineTopOffset();
    public abstract int getCurrentTimeLineSideBuffer();
    public abstract int getMinCellWidthForText();
    public abstract int getHoursTopMargin();
    public abstract float getGridLineLeftMargin();
    public abstract float getNormalFontSize();
    public abstract int getMaxHeightOfOneAlldayEvent();
    public abstract int getAlldayTopMargin();
    public abstract int getSingleAlldayHeight();
    
    /**
     * This is how big the unexpanded allday height is allowed to be. It will
     * get adjusted based on screen size
     */
    public abstract int getMAX_UNEXPANDED_ALLDAY_HEIGHT();
    public int getEventPopupTimeTextFieldID();
    public int getEventPopupEventWhereTextFieldID();
    public int getEventPopupTitleTextFieldID();
    public int getEventPopupReminderIconID();
    public int getEventPopupRepeatIconID();
    
    
    public float getGridLineWidth();
    

    /**
     * 
     * @return Number of pixels in-between two rows of hours on the schedule. Default returns 1.
     */
    int getHourGap();
}
