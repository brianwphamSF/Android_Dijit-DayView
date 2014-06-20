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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.google.code.yadview.DayViewResources;
import com.google.code.yadview.R;

public class DefaultDayViewResources implements DayViewResources {

    private static final int SINGLE_ALLDAY_HEIGHT = 34;
    private static int ALLDAY_TOP_MARGIN = 1;
    private static int MAX_HEIGHT_OF_ONE_ALLDAY_EVENT = 34;
    private static float NORMAL_FONT_SIZE = 12;
    private static float GRID_LINE_LEFT_MARGIN = 0;
    private static int HOURS_TOP_MARGIN = 2;
    private static int MIN_CELL_WIDTH_FOR_TEXT = 20;
    private static int CURRENT_TIME_LINE_SIDE_BUFFER = 4;
    private static int CURRENT_TIME_LINE_TOP_OFFSET = 2;
    private static int DEFAULT_CELL_HEIGHT = 64;
    private static int MAX_CELL_HEIGHT = 150;
    private static int MIN_Y_SPAN = 100;
    private static int DAY_HEADER_HEIGHT = 45;
    private static int DAY_HEADER_ONE_DAY_LEFT_MARGIN = 0;
    private static int DAY_HEADER_ONE_DAY_RIGHT_MARGIN = 5;
    private static int DAY_HEADER_ONE_DAY_BOTTOM_MARGIN = 6;
    private static int DAY_HEADER_RIGHT_MARGIN = 4;
    private static int CALENDAR_COLOR_SQUARE_SIZE = 10;
    private static int EVENT_RECT_TOP_MARGIN = 1;
    private static int EVENT_RECT_BOTTOM_MARGIN = 0;
    private static int EVENT_RECT_LEFT_MARGIN = 1;
    private static int EVENT_RECT_RIGHT_MARGIN = 0;
    private static int EVENT_RECT_STROKE_WIDTH = 2;
    private static int ALL_DAY_EVENT_RECT_BOTTOM_MARGIN = 1;
    private static int EVENT_SQUARE_WIDTH = 10;
    private static int EVENT_LINE_PADDING = 4;
    private static int NEW_EVENT_MARGIN = 4;
    private static int NEW_EVENT_WIDTH = 2;
    private static int NEW_EVENT_MAX_LENGTH = 16;
    private static int MAX_UNEXPANDED_ALLDAY_HEIGHT =(int) (28f * 4);
    private static final float GRID_LINE_INNER_WIDTH = 1;

    private static final int HOUR_GAP = 1;
    
    static final String[] s12HoursNoAmPm = {
            "12", "1", "2", "3", "4",
            "5", "6", "7", "8", "9", "10", "11", "12",
            "1", "2", "3", "4", "5", "6", "7", "8",
            "9", "10", "11", "12"
    };

    static final String[] s24Hours = {
            "00", "01", "02", "03", "04", "05",
            "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16",
            "17", "18", "19", "20", "21", "22", "23", "00"
    };

    private String mCreateNewEventString;

    private String mNewEventHintString;

    private int mDateHeaderFontSize;

    private int mOneDayHeaderHeight;

    private int mDayHeaderBottomMargin;

    private int mExpandAllDayBottomMargin;

    private int mHoursTextSize;

    private int mAMPMTextSize;

    private int mMinHoursWidth;

    private int mHoursLeftMargin;

    private int mHoursRightMargin;

    private int mMultiDayHeaderHeight;

    private int mNewEventHintTextFontSize;

    private float mMinEventHeight;

    private float mMinUnexpandedAllDayEventHeight;

    private int mEventTextTopMargin;

    private int mEventTextBottomMargin;

    private int mEventAllDayTextTopMargin;

    private int mEventAllDayTextBottomMargin;

    private int mDayHeaderFontSize;

    private int mEventTextLeftMargin;

    private int mEventTextRightMargin;

    private int mEventAllDayTextLeftMargin;

    private int mEventAlldayTextRightMargin;

    private int mHoursMargin;

    private Resources mResources;

    private Drawable mCurrentTimeLine;

    private Drawable mCurrentTimeAnimateLine;

    private Drawable mTodayHeaderDrawable;

    private Drawable mExpandAlldayDrawable;

    private Drawable mCollapseAlldayDrawable;

    private int mNewEventHintColor;

    private Drawable mAcceptedOrTentativeEventBoxDrawable;

    private CharSequence[] mLongPressItems;

    private int mWeekSaturdayColor;

    private int mWeekSundayColor;

    private int mCalendarDateBannerTextColor;

    private int mFutureBgColorRes;

    private int mBgColor;

    private int mCalendarAmPmLabel;

    private int mCalendarGridAreaSelected;

    private int mCalendarGridLineInnerHorizontalColor;

    private int mCalendarGridLineInnerVerticalColor;

    private int mCalendarHourLabelColor;

    private int mPressedColor;

    private int mClickedColor;

    private int mEventTextColor;

    private int mMoreEventsTextColor;

    private int mGridLineColor;

    private String mEventCountTemplate;
    private float mScale;

    public DefaultDayViewResources(Context ctx) {
        mResources = ctx.getResources();

        mCreateNewEventString = mResources.getString(R.string.event_create);
        mNewEventHintString = mResources.getString(R.string.day_view_new_event_hint);
        mDateHeaderFontSize = (int) mResources.getDimension(R.dimen.date_header_text_size);
        mDayHeaderFontSize = (int) mResources.getDimension(R.dimen.day_label_text_size);
        mOneDayHeaderHeight = (int) mResources.getDimension(R.dimen.one_day_header_height);
        mDayHeaderBottomMargin = (int) mResources.getDimension(R.dimen.day_header_bottom_margin);
        mExpandAllDayBottomMargin = (int) mResources.getDimension(R.dimen.all_day_bottom_margin);
        mHoursTextSize = (int) mResources.getDimension(R.dimen.hours_text_size);
        mAMPMTextSize = (int) mResources.getDimension(R.dimen.ampm_text_size);
        mMinHoursWidth = (int) mResources.getDimension(R.dimen.min_hours_width);
        mHoursLeftMargin = (int) mResources.getDimension(R.dimen.hours_left_margin);
        mHoursRightMargin = (int) mResources.getDimension(R.dimen.hours_right_margin);
        mMultiDayHeaderHeight = (int) mResources.getDimension(R.dimen.day_header_height);
        mNewEventHintTextFontSize = (int) mResources.getDimension(R.dimen.new_event_hint_text_size);
        mMinEventHeight = mResources.getDimension(R.dimen.event_min_height);
        mEventTextTopMargin = (int) mResources.getDimension(R.dimen.event_text_vertical_margin);
        mEventTextBottomMargin = mEventTextTopMargin;
        mEventAllDayTextTopMargin = mEventTextTopMargin;
        mEventAllDayTextBottomMargin = mEventTextTopMargin;

        mEventTextLeftMargin = (int) mResources.getDimension(R.dimen.event_text_horizontal_margin);
        mEventTextRightMargin = mEventTextLeftMargin;
        mEventAllDayTextLeftMargin = mEventTextLeftMargin;
        mEventAlldayTextRightMargin = mEventTextLeftMargin;

        mHoursMargin = mHoursLeftMargin + mHoursRightMargin;
        mMinUnexpandedAllDayEventHeight = mMinEventHeight;

        mCurrentTimeLine = mResources.getDrawable(R.drawable.timeline_indicator_holo_light);
        mCurrentTimeAnimateLine = mResources
                .getDrawable(R.drawable.timeline_indicator_activated_holo_light);
        mTodayHeaderDrawable = mResources.getDrawable(R.drawable.today_blue_week_holo_light);
        mExpandAlldayDrawable = mResources.getDrawable(R.drawable.ic_expand_holo_light);
        mCollapseAlldayDrawable = mResources.getDrawable(R.drawable.ic_collapse_holo_light);
        mNewEventHintColor = mResources.getColor(R.color.new_event_hint_text_color);
        mAcceptedOrTentativeEventBoxDrawable = mResources
                .getDrawable(R.drawable.panel_month_event_holo_light);
        mLongPressItems = new CharSequence[] {
                mResources.getString(R.string.new_event_dialog_option)
        };

        mWeekSaturdayColor = mResources.getColor(R.color.week_saturday);
        mWeekSundayColor = mResources.getColor(R.color.week_sunday);
        mCalendarDateBannerTextColor = mResources.getColor(R.color.calendar_date_banner_text_color);
        mFutureBgColorRes = mResources.getColor(R.color.calendar_future_bg_color);
        mBgColor = mResources.getColor(R.color.calendar_hour_background);
        mCalendarAmPmLabel = mResources.getColor(R.color.calendar_ampm_label);
        mCalendarGridAreaSelected = mResources.getColor(R.color.calendar_grid_area_selected);
        mCalendarGridLineInnerHorizontalColor = mResources
                .getColor(R.color.calendar_grid_line_inner_horizontal_color);
        mCalendarGridLineInnerVerticalColor = mResources
                .getColor(R.color.calendar_grid_line_inner_vertical_color);
        mCalendarHourLabelColor = mResources.getColor(R.color.calendar_hour_label);
        mPressedColor = mResources.getColor(R.color.pressed);
        mClickedColor = mResources.getColor(R.color.day_event_clicked_background_color);
        mEventTextColor = mResources.getColor(R.color.calendar_event_text_color);
        mMoreEventsTextColor = mResources.getColor(R.color.month_event_other_color);

        mCreateNewEventString = mResources.getString(R.string.event_create);
        mNewEventHintString = mResources.getString(R.string.day_view_new_event_hint);
        mGridLineColor = mResources.getColor(R.color.calendar_grid_line_highlight_color);
        mEventCountTemplate = mResources.getString(R.string.template_announce_item_index);

        resetDisplayDensity();
    }

    @Override
    public String[] get12HoursNoAmPm() {
        return s12HoursNoAmPm;
    }

    @Override
    public String[] get24Hours() {
        return s24Hours;
    }

    @Override
    public int getEventTextFontSize(int numDays) {
        int eventTextSizeId;
        if (numDays == 1) {
            eventTextSizeId = R.dimen.day_view_event_text_size;
        } else {
            eventTextSizeId = R.dimen.week_view_event_text_size;
        }
        return (int) mResources.getDimension(eventTextSizeId);
    }

    @Override
    public int getDayHeaderHeight(int numDays) {
        return numDays == 1 ? mOneDayHeaderHeight : mMultiDayHeaderHeight;
    }

    @Override
    public String getCreateNewEventString() {
        return mCreateNewEventString;
    }

    @Override
    public String getNewEventHintString() {
        return mNewEventHintString;
    }

    @Override
    public int getDateHeaderFontSize() {
        return mDateHeaderFontSize;
    }

    @Override
    public int getOneDayHeaderHeight() {
        return mOneDayHeaderHeight;
    }

    @Override
    public int getDayHeaderBottomMargin() {
        return mDayHeaderBottomMargin;
    }

    @Override
    public int getExpandAllDayBottomMargin() {
        return mExpandAllDayBottomMargin;
    }

    @Override
    public int getHoursTextSize() {
        return mHoursTextSize;
    }

    @Override
    public int getAMPMTextSize() {
        return mAMPMTextSize;
    }

    @Override
    public int getMinHoursWidth() {
        return mMinHoursWidth;
    }

    @Override
    public int getHoursLeftMargin() {
        return mHoursLeftMargin;
    }

    @Override
    public int getHoursRightMargin() {
        return mHoursRightMargin;
    }

    @Override
    public int getMultiDayHeaderHeight() {
        return mMultiDayHeaderHeight;
    }

    @Override
    public int getNewEventHintTextFontSize() {
        return mNewEventHintTextFontSize;
    }

    @Override
    public float getMinEventHeight() {
        return mMinEventHeight;
    }

    @Override
    public float getMinUnexpandedAllDayEventHeight() {
        return mMinUnexpandedAllDayEventHeight;
    }

    @Override
    public int getEventTextTopMargin() {
        return mEventTextTopMargin;
    }

    @Override
    public int getEventTextBottomMargin() {
        return mEventTextBottomMargin;
    }

    @Override
    public int getEventAllDayTextTopMargin() {
        return mEventAllDayTextTopMargin;
    }

    @Override
    public int getEventAllDayTextBottomMargin() {
        return mEventAllDayTextBottomMargin;
    }

    @Override
    public int getDayHeaderFontSize() {
        return mDayHeaderFontSize;
    }

    @Override
    public int getEventTextLeftMargin() {
        return mEventTextLeftMargin;
    }

    @Override
    public int getEventTextRightMargin() {
        return mEventTextRightMargin;
    }

    @Override
    public int getEventAllDayTextLeftMargin() {
        return mEventAllDayTextLeftMargin;
    }

    @Override
    public int getEventAlldayTextRightMargin() {
        return mEventAlldayTextRightMargin;
    }

    @Override
    public int getHoursMargin() {
        return mHoursMargin;
    }

    public Resources getResources() {
        return mResources;
    }

    @Override
    public Drawable getCurrentTimeLine() {
        return mCurrentTimeLine;
    }

    @Override
    public Drawable getCurrentTimeAnimateLine() {
        return mCurrentTimeAnimateLine;
    }

    @Override
    public Drawable getTodayHeaderDrawable() {
        return mTodayHeaderDrawable;
    }

    @Override
    public Drawable getExpandAlldayDrawable() {
        return mExpandAlldayDrawable;
    }

    @Override
    public Drawable getCollapseAlldayDrawable() {
        return mCollapseAlldayDrawable;
    }

    @Override
    public int getNewEventHintColor() {
        return mNewEventHintColor;
    }

    @Override
    public Drawable getAcceptedOrTentativeEventBoxDrawable() {
        return mAcceptedOrTentativeEventBoxDrawable;
    }

    @Override
    public CharSequence[] getLongPressItems() {
        return mLongPressItems;
    }

    @Override
    public int getWeekSaturdayColor() {
        return mWeekSaturdayColor;
    }

    @Override
    public int getWeekSundayColor() {
        return mWeekSundayColor;
    }

    @Override
    public int getCalendarDateBannerTextColor() {
        return mCalendarDateBannerTextColor;
    }

    @Override
    public int getFutureBgColorRes() {
        return mFutureBgColorRes;
    }

    @Override
    public int getBgColor() {
        return mBgColor;
    }

    @Override
    public int getCalendarAmPmLabel() {
        return mCalendarAmPmLabel;
    }

    @Override
    public int getCalendarGridAreaSelected() {
        return mCalendarGridAreaSelected;
    }

    @Override
    public int getCalendarGridLineInnerHorizontalColor() {
        return mCalendarGridLineInnerHorizontalColor;
    }

    @Override
    public int getCalendarGridLineInnerVerticalColor() {
        return mCalendarGridLineInnerVerticalColor;
    }

    @Override
    public int getCalendarHourLabelColor() {
        return mCalendarHourLabelColor;
    }

    @Override
    public int getPressedColor() {
        return mPressedColor;
    }

    @Override
    public int getClickedColor() {
        return mClickedColor;
    }

    @Override
    public int getEventTextColor() {
        return mEventTextColor;
    }

    @Override
    public int getMoreEventsTextColor() {
        return mMoreEventsTextColor;
    }

    @Override
    public int getGridLineColor() {
        return mGridLineColor;
    }

    @Override
    public String getEventCountTemplate() {
        return mEventCountTemplate;
    }

    @Override
    public int getEventPopupViewLayoutID() {
        return R.layout.bubble_event;
    }

    @Override
    public String getMoreEventsMonthText(int remainingEvents) {
        return mResources.getQuantityString(R.plurals.month_more_events, remainingEvents);
    }

    @Override
    public CharSequence getViewEventMenuItemLabel() {
        return mResources.getString(R.string.event_view);
    }

    @Override
    public CharSequence getEditEventMenuItemLabel() {
        return mResources.getString(R.string.event_edit);
    }

    @Override
    public CharSequence getDeleteEventMenuItemLabel() {
        return mResources.getString(R.string.event_delete);

    }

    @Override
    public CharSequence getCreateEventMenuItemLabel() {

        return mResources.getString(R.string.event_create);
    }

    @Override
    public CharSequence getShowDayViewMenuItemLabel() {
        return mResources.getString(R.string.show_day_view);
    }

    @Override
    public void resetDisplayDensity() {
        mScale = mResources.getDisplayMetrics().density;

    }

    @Override
    public int getSingleAlldayHeight() {
        return (int) (SINGLE_ALLDAY_HEIGHT * mScale);
    }

    @Override
    public int getAlldayTopMargin() {
        return (int) (ALLDAY_TOP_MARGIN * mScale);
    }

    @Override
    public int getMaxHeightOfOneAlldayEvent() {
        return (int) (MAX_HEIGHT_OF_ONE_ALLDAY_EVENT * mScale);
    }

    @Override
    public float getNormalFontSize() {
        return NORMAL_FONT_SIZE * mScale;
    }

    @Override
    public float getGridLineLeftMargin() {
        return GRID_LINE_LEFT_MARGIN * mScale;
    }

    @Override
    public int getHoursTopMargin() {
        return (int) (HOURS_TOP_MARGIN * mScale);
    }

    @Override
    public int getMinCellWidthForText() {
        return (int) (MIN_CELL_WIDTH_FOR_TEXT * mScale);
    }

    @Override
    public int getCurrentTimeLineSideBuffer() {
        return (int) (CURRENT_TIME_LINE_SIDE_BUFFER * mScale);
    }

    @Override
    public int getCurrentTimeLineTopOffset() {
        return (int) (CURRENT_TIME_LINE_TOP_OFFSET * mScale);
    }

    @Override
    public int getDefaultCellHeight() {
        return (int) (DEFAULT_CELL_HEIGHT * mScale);
    }

    @Override
    public int getMaxCellHeight() {
        return (int) (MAX_CELL_HEIGHT * mScale);
    }

    @Override
    public int getMinYSpan() {
        return (int) (MIN_Y_SPAN * mScale);
    }

    @Override
    public int getDayHeaderHeight() {
        return (int) (DAY_HEADER_HEIGHT * mScale);
    }

    @Override
    public int getDayHeaderOneDayLeftMargin() {
        return (int) (DAY_HEADER_ONE_DAY_LEFT_MARGIN * mScale);
    }

    @Override
    public int getDayHeaderOneDayRightMargin() {
        return (int) (DAY_HEADER_ONE_DAY_RIGHT_MARGIN * mScale);
    }

    @Override
    public int getDayHeaderOneDayBottomMargin() {
        return (int) (DAY_HEADER_ONE_DAY_BOTTOM_MARGIN * mScale);
    }

    @Override
    public int getDayHeaderRightMargin() {
        return (int) (DAY_HEADER_RIGHT_MARGIN * mScale);
    }

    @Override
    public int getCalendarColorSquareSize() {
        return (int) (CALENDAR_COLOR_SQUARE_SIZE * mScale);
    }

    @Override
    public int getEventRectTopMargin() {
        return (int) (EVENT_RECT_TOP_MARGIN * mScale);
    }

    @Override
    public int getEventRectBottomMargin() {
        return (int) (EVENT_RECT_BOTTOM_MARGIN * mScale);
    }

    @Override
    public int getEventRectLeftMargin() {
        return (int) (EVENT_RECT_LEFT_MARGIN * mScale);
    }

    @Override
    public int getEventRectRightMargin() {
        return (int) (EVENT_RECT_RIGHT_MARGIN * mScale);
    }

    @Override
    public int getEventRectStrokeWidth() {
        return (int) (EVENT_RECT_STROKE_WIDTH * mScale);
    }

    @Override
    public int getAllDayEventRectBottomMargin() {
        return (int) (ALL_DAY_EVENT_RECT_BOTTOM_MARGIN * mScale);
    }

    @Override
    public int getEventSquareWidth() {
        return (int) (EVENT_SQUARE_WIDTH * mScale);
    }

    @Override
    public int getEventLinePadding() {
        return (int) (EVENT_LINE_PADDING * mScale);
    }

    @Override
    public int getNewEventMargin() {
        return (int) (NEW_EVENT_MARGIN * mScale);
    }

    @Override
    public int getNewEventWidth() {
        return (int) (NEW_EVENT_WIDTH * mScale);
    }

    @Override
    public int getNewEventMaxLength() {
        return (int) (NEW_EVENT_MAX_LENGTH * mScale);
    }
    
    @Override
    public int getMAX_UNEXPANDED_ALLDAY_HEIGHT() {
        return (int)(MAX_UNEXPANDED_ALLDAY_HEIGHT * mScale);
    }

    @Override
    public int getEventPopupTimeTextFieldID() {
        return R.id.time;
    }

    @Override
    public int getEventPopupEventWhereTextFieldID() {
        return R.id.where;
    }
    
    @Override
    public int getEventPopupTitleTextFieldID() {
        return R.id.event_title;
    }
    
    @Override
    public int getEventPopupReminderIconID() {
        return R.id.reminder_icon;
    }

    @Override
    public int getEventPopupRepeatIconID() {
        return R.id.repeat_icon ;
    }

    @Override
    public float getGridLineWidth() {
        return GRID_LINE_INNER_WIDTH;
    }
    
    @Override
    public int getHourGap() {
        return HOUR_GAP;
    }
    
    
}
