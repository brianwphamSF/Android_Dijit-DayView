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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EdgeEffect;
import android.widget.ImageView;
import android.widget.OverScroller;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.google.code.yadview.DayViewScrollingController.HorizontalScrollDirection;
import com.google.code.yadview.DayViewScrollingController.HorizontalScrollingStartedEvent;
import com.google.code.yadview.DayViewScrollingController.ScrollEvent;
import com.google.code.yadview.DayViewScrollingController.VerticalScrollingStartedEvent;
import com.google.code.yadview.events.CreateEventEvent;
import com.google.code.yadview.events.ShowDateInCurrentViewEvent;
import com.google.code.yadview.events.ShowDateInDayViewEvent;
import com.google.code.yadview.events.UpdateTitleEvent;
import com.google.code.yadview.events.ViewEventEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

/**
 * View for multi-day view. So far only 1 and 7 day have been tested.
 */
public class DayView extends View implements ScaleGestureDetector.OnScaleGestureListener, View.OnClickListener
{


    private static String TAG = "DayView";
    private static boolean DEBUG = false;
    private static boolean DEBUG_SCALING = false;
    private static final String PERIOD_SPACE = ". ";

    static final long INVALID_EVENT_ID = -1; // This is used for
                                                     // remembering a null event
    // Duration of the allday expansion
    private static final long ANIMATION_DURATION = 400;
    // duration of the more allday event text fade
    private static final long ANIMATION_SECONDARY_DURATION = 200;
    // duration of the scroll to go to a specified time
    private static final int GOTO_SCROLL_DURATION = 200;
    // duration for events' cross-fade animation
    private static final int EVENTS_CROSS_FADE_DURATION = 400;
    // duration to show the event clicked
    private static final int CLICK_DISPLAY_DURATION = 50;


    private boolean mOnFlingCalled;
    protected boolean mPaused = true;
    private Handler mHandler;
    /**
     * ID of the last event which was displayed with the toast popup. This is
     * used to prevent popping up multiple quick views for the same event,
     * especially during calendar syncs. This becomes valid when an event is
     * selected, either by default on starting calendar or by scrolling to an
     * event. It becomes invalid when the user explicitly scrolls to an empty
     * time slot, changes views, or deletes the event.
     */
    long mLastPopupEventID;

    protected Context mContext;


    private static final int FROM_NONE = 0;
    private static final int FROM_ABOVE = 1;
    private static final int FROM_BELOW = 2;
    private static final int FROM_LEFT = 4;
    private static final int FROM_RIGHT = 8;


    private static int mHorizontalSnapBackThreshold = 128;

    private final ContinueScroll mContinueScroll = new ContinueScroll();

    // Make this visible within the package for more informative debugging
    Time mBaseDate;
    private Time mCurrentTime;
    // Update the current time line every five minutes if the window is left
    // open that long
    private static final int UPDATE_CURRENT_TIME_DELAY = 300000;
    private final UpdateCurrentTime mUpdateCurrentTime = new UpdateCurrentTime();
    private int mTodayJulianDay;

    private final Typeface mBold = Typeface.DEFAULT_BOLD;
    private int mFirstJulianDay;
    private int mLoadedFirstJulianDay = -1;
    private int mLastJulianDay;

    private int mMonthLength;
    private int mFirstVisibleDate;
    private int mFirstVisibleDayOfWeek;
    private int[] mEarliestStartHour; // indexed by the week day offset
    private boolean[] mHasAllDayEvent; // indexed by the week day offset

    private EventLayout mClickedEvent; // The event the user clicked on
    private EventLayout mSavedClickedEvent;
    private int mOnDownDelay;
    private long mDownTouchTime;

    private int mEventsAlpha = 255;
    private ObjectAnimator mEventsCrossFadeAnimation;

    protected static StringBuilder mStringBuilder = new StringBuilder(50);
    // TODO recreate formatter when locale changes
    protected static Formatter mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

    private EventBus mEventBus = new EventBus(); 
    private DayViewResources mDayViewResources;


    public static final String KEY_DEFAULT_CELL_HEIGHT = "preferences_default_cell_height";

    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            String tz = mDependencyFactory.buildTimezoneUtils().getTimeZone(mContext, this);
            mBaseDate.timezone = tz;
            mBaseDate.normalize(true);
            mCurrentTime.switchTimezone(tz);
            invalidate();
        }
    };

    // Sets the "clicked" color from the clicked event
    private final Runnable mSetClick = new Runnable() {
        @Override
        public void run() {
            mClickedEvent = mSavedClickedEvent;
            mSavedClickedEvent = null;
            DayView.this.invalidate();
        }
    };

    // Clears the "clicked" color from the clicked event and launch the event
    private final Runnable mClearClick = new Runnable() {
        @Override
        public void run() {
            if (mClickedEvent != null) {

                //TODO: Necessary to supply derived coordinates?
                //This used to supply:
//                mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, mClickedEvent.getEvent().getId(),
//                        mClickedEvent.getEvent().getStartMillis(), mClickedEvent.getEvent().getStartMillis(),
//                        DayView.this.getWidth() / 2, mClickedYLocation,
//                        getSelectedTimeInMillis());

                mEventBus.post(new ViewEventEvent(mClickedEvent.getEvent(), getSelectedTimeInMillis()));

            }
            mClickedEvent = null;
            DayView.this.invalidate();
        }
    };

    private final TodayAnimatorListener mTodayAnimatorListener = new TodayAnimatorListener();

    class TodayAnimatorListener extends AnimatorListenerAdapter {
        private volatile Animator mAnimator = null;
        private volatile boolean mFadingIn = false;

        @Override
        public void onAnimationEnd(Animator animation) {
            synchronized (this) {
                if (mAnimator != animation) {
                    animation.removeAllListeners();
                    animation.cancel();
                    return;
                }
                if (mFadingIn) {
                    if (mTodayAnimator != null) {
                        mTodayAnimator.removeAllListeners();
                        mTodayAnimator.cancel();
                    }
                    mTodayAnimator = ObjectAnimator
                            .ofInt(DayView.this, "animateTodayAlpha", 255, 0);
                    mAnimator = mTodayAnimator;
                    mFadingIn = false;
                    mTodayAnimator.addListener(this);
                    mTodayAnimator.setDuration(600);
                    mTodayAnimator.start();
                } else {
                    mAnimateToday = false;
                    mAnimateTodayAlpha = 0;
                    mAnimator.removeAllListeners();
                    mAnimator = null;
                    mTodayAnimator = null;
                    invalidate();
                }
            }
        }

        public void setAnimator(Animator animation) {
            mAnimator = animation;
        }

        public void setFadingIn(boolean fadingIn) {
            mFadingIn = fadingIn;
        }

    }

    AnimatorListenerAdapter mAnimatorListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            mScrolling = true;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mScrolling = false;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mScrolling = false;
            resetSelectedHour();
            invalidate();
        }
    };

    /**
     * This variable helps to avoid unnecessarily reloading events by keeping
     * track of the start millis parameter used for the most recent loading of
     * events. If the next reload matches this, then the events are not
     * reloaded. To force a reload, set this to zero (this is set to zero in the
     * method clearCachedEvents()).
     */
    private long mLastReloadMillis;

    private ArrayList<EventLayout> mEvents = new ArrayList<EventLayout>();
    private ArrayList<EventLayout> mAllDayEvents = new ArrayList<EventLayout>();
//    private StaticLayout[] mLayouts = null;
    private StaticLayout[] mAllDayLayouts = null;
    private int mSelectionDay; // Julian day
    private int mSelectionHour;

    boolean mSelectionAllday;

    // Current selection info for accessibility
    private int mSelectionDayForAccessibility; // Julian day
    private int mSelectionHourForAccessibility;
    private EventLayout mSelectedEventForAccessibility;
    // Last selection info for accessibility
    private int mLastSelectionDayForAccessibility;
    private int mLastSelectionHourForAccessibility;
    private EventLayout mLastSelectedEventForAccessibility;

    /** Width of a day or non-conflicting event */
    private int mCellWidth;

    // Pre-allocate these objects and re-use them
    private final Rect mRect = new Rect();
    private final Rect mDestRect = new Rect();
    private final Rect mSelectionRect = new Rect();
    // This encloses the more allDay events icon
    private final Rect mExpandAllDayRect = new Rect();
    // TODO Clean up paint usage
    private final Paint mPaint = new Paint();
    private final Paint mEventTextPaint = new Paint();
    private final Paint mSelectionPaint = new Paint();
//    private float[] mLines;

    private int mFirstDayOfWeek; // First day of the week

    /*package - uygly */ PopupWindow mPopup;
    private View mPopupView;

    // The number of milliseconds to show the popup window
    private static final int POPUP_DISMISS_DELAY = 3000;
    private final DismissPopup mDismissPopup = new DismissPopup();

    private boolean mRemeasure = true;

    private final DayViewEventLoader mEventLoader;
    protected final EventGeometry mEventGeometry;


    private static final int DAY_GAP = 1;
    
    // This is the standard height of an allday event with no restrictions
    /**
     * This is the minimum desired height of a allday event. When unexpanded,
     * allday events will use this height. When expanded allDay events will
     * attempt to grow to fit all events at this height.
     */
    // private static float MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT = 28.0F; // in
    // pixels
    /**
     * This is the minimum size reserved for displaying regular events. The
     * expanded allDay region can't expand into this.
     */
    private static int MIN_HOURS_HEIGHT = 180;
    // The largest a single allDay event will become.



    /* package */static final int MINUTES_PER_HOUR = 60;
    /* package */static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * 24;
    /* package */static final int MILLIS_PER_MINUTE = 60 * 1000;
    /* package */static final int MILLIS_PER_HOUR = (3600 * 1000);
    /* package */static final int MILLIS_PER_DAY = MILLIS_PER_HOUR * 24;

    // More events text will transition between invisible and this alpha
    private static final int MORE_EVENTS_MAX_ALPHA = 0x4C;
    

    private static int mFutureBgColor;
    private static int mMoreAlldayEventsTextAlpha = MORE_EVENTS_MAX_ALPHA;

    private float mAnimationDistance = 0;
    private int mViewStartX;
    private int mViewStartY;
    private int mMaxViewStartY;
    private int mViewHeight;
    private int mViewWidth;
    private int mGridAreaHeight = -1;
    private static int mCellHeight = 0; // shared among all DayViews
    private static int mMinCellHeight = 32;
    private int mScrollStartY;
    private int mScaledPagingTouchSlop = 0;

    /**
     * Vertical distance or span between the two touch points at the start of a
     * scaling gesture
     */
    private float mStartingSpanY = 0;
    /** Height of 1 hour in pixels at the start of a scaling gesture */
    private int mCellHeightBeforeScaleGesture;
    /** The hour at the center two touch points */
    private float mGestureCenterHour = 0;

    private boolean mRecalCenterHour = false;

    /**
     * Flag to decide whether to handle the up event. Cases where up events
     * should be ignored are 1) right after a scale gesture and 2) finger was
     * down before app launch
     */
    private boolean mHandleActionUp = true;

    private int mHoursTextHeight;
    /**
     * The height of the area used for allday events
     */
    private int mAlldayHeight;
    /**
     * The height of the allday event area used during animation
     */
    private int mAnimateDayHeight = 0;
    /**
     * The height of an individual allday event during animation
     */
    private int mAnimateDayEventHeight;

    private int mMaxUnexpandedAllDayHeight;

    /**
     * Whether to use the expand or collapse icon.
     */
    private static boolean mUseExpandIcon = true;
    /**
     * The height of the day names/numbers
     */
     
    /**
     * Max of all day events in a given day in this view.
     */
    private int mMaxAlldayEvents;
    /**
     * A count of the number of allday events that were not drawn for each day
     */
    private int[] mSkippedAlldayEvents;
    /**
     * The number of allDay events at which point we start hiding allDay events.
     */
    private int mMaxUnexpandedAlldayEventCount = 1;
    /**
     * Whether or not to expand the allDay area to fill the screen
     */
    private static boolean mShowAllAllDayEvents = false;
    protected int mNumDays = 7;
    private int mNumHours = 10;

    /** Width of the time line (list of hours) to the left. */
    private int mHoursWidth;
    private int mDateStrWidth;
    /** Top of the scrollable region i.e. below date labels and all day events */
    private int mFirstCell;
    /** First fully visibile hour */
    private int mFirstHour = -1;
    /** Distance between the mFirstCell and the top of first fully visible hour. */
    private int mFirstHourOffset;
    private String[] mHourStrs;
    private String[] mDayStrs;
    private String[] mDayStrs2Letter;
    private boolean mIs24HourFormat;

    private final ArrayList<EventLayout> mSelectedEvents = new ArrayList<EventLayout>();
    private boolean mComputeSelectedEvents;
    private boolean mUpdateToast;
    private EventLayout mSelectedEvent;
    private EventLayout mPrevSelectedEvent;
    private final Rect mPrevBox = new Rect();
    protected final Resources mResources;

    private String mAmString;
    private String mPmString;
    private static int sCounter = 0;


    ScaleGestureDetector mScaleGestureDetector;



    /**
     * The selection modes are HIDDEN, PRESSED, SELECTED, and LONGPRESS.
     */
    static final int SELECTION_HIDDEN = 0;
    static final int SELECTION_PRESSED = 1; // D-pad down but not up yet
    static final int SELECTION_SELECTED = 2;
    static final int SELECTION_LONGPRESS = 3;

    private int mSelectionMode = SELECTION_HIDDEN;

    private boolean mScrolling = false;

    private boolean mAnimateToday = false;
    private int mAnimateTodayAlpha = 0;

    // Animates the height of the allday region
    ObjectAnimator mAlldayAnimator;
    // Animates the height of events in the allday region
    ObjectAnimator mAlldayEventAnimator;
    // Animates the transparency of the more events text
    ObjectAnimator mMoreAlldayEventsAnimator;
    // Animates the current time marker when Today is pressed
    ObjectAnimator mTodayAnimator;
    // whether or not an event is stopping because it was cancelled
    private boolean mCancellingAnimations = false;
    // tracks whether a touch originated in the allday area
    private boolean mTouchStartedInAlldayArea = false;

    private final ViewSwitcher mViewSwitcher;
    private final GestureDetector mGestureDetector;
    private final OverScroller mScroller;
    private final EdgeEffect mEdgeEffectTop;
    private final EdgeEffect mEdgeEffectBottom;
    private boolean mCallEdgeEffectOnAbsorb;
    private final int OVERFLING_DISTANCE;
    private float mLastVelocity;

    private final ScrollInterpolator mHScrollInterpolator;
    private AccessibilityManager mAccessibilityMgr = null;
    private boolean mIsAccessibilityEnabled = false;
    private boolean mTouchExplorationEnabled = false;
	private EventRenderer mEventRenderer;
    private DayViewScrollingController mScrollController;
    private DayViewDependencyFactory mDependencyFactory;

    public DayView(Context context, ViewSwitcher viewSwitcher,
            int numDays, DayViewEventLoader eventLoader, DayViewResources resources,
            DayViewDependencyFactory dependencyFactory) {
        super(context);
        mContext = context;
        mDependencyFactory = dependencyFactory;
        mDayViewResources = resources;

        initAccessibilityVariables();

        mResources = context.getResources();

        mAnimateDayEventHeight = (int) mDayViewResources.getMinUnexpandedAllDayEventHeight();
        mMaxUnexpandedAllDayHeight = mDayViewResources.getMAX_UNEXPANDED_ALLDAY_HEIGHT();
        
        
        mNumDays = numDays;

        mDayViewResources.resetDisplayDensity();
        

        mEventLoader = eventLoader;
        mEventGeometry = new EventGeometry();
        mEventGeometry.setMinEventHeight(mDayViewResources.getMinEventHeight());
        mEventGeometry.setHourGap(mDayViewResources.getHourGap());
        mEventGeometry.setCellMargin(DAY_GAP);
        mLastPopupEventID = INVALID_EVENT_ID;
        mViewSwitcher = viewSwitcher;
        mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
        mScaleGestureDetector = new ScaleGestureDetector(getContext(), this);
        if (mCellHeight == 0) {
            mCellHeight = mDependencyFactory.buildPreferencesUtils().getSharedPreference(mContext,
                    KEY_DEFAULT_CELL_HEIGHT, mDayViewResources.getDefaultCellHeight());
        }
        mScroller = new OverScroller(context);
        mHScrollInterpolator = new ScrollInterpolator();
        mEdgeEffectTop = new EdgeEffect(context);
        mEdgeEffectBottom = new EdgeEffect(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        mScaledPagingTouchSlop = vc.getScaledPagingTouchSlop();
        mOnDownDelay = ViewConfiguration.getTapTimeout();
        OVERFLING_DISTANCE = vc.getScaledOverflingDistance();
        
        mScrollController = mDependencyFactory.buildScrollingController(mEventBus);
        mEventBus.register(new DayViewScrollEventHandler());
        mEventRenderer = mDependencyFactory.buildEventRenderer();
        mDayViewRenderer = mDependencyFactory.buildDayViewRenderer();

        init(context);
    }

    @Override
    protected void onAttachedToWindow() {
        if (mHandler == null) {
            mHandler = getHandler();
            mHandler.post(mUpdateCurrentTime);
        }
    }

    private void init(Context context) {
        setFocusable(true);

        // Allow focus in touch mode so that we can do keyboard shortcuts
        // even after we've entered touch mode.
        setFocusableInTouchMode(true);
        setClickable(true);

       mFirstDayOfWeek = mDependencyFactory.buildDateUtils().getFirstDayOfWeek(context);

        mCurrentTime = new Time(mDependencyFactory.buildTimezoneUtils().getTimeZone(context, mTZUpdater));
        long currentTime = System.currentTimeMillis();
        mCurrentTime.set(currentTime);
        mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);



        mEventTextPaint.setTextSize(mDayViewResources.getEventTextFontSize(mNumDays));
        mEventTextPaint.setTextAlign(Paint.Align.LEFT);
        mEventTextPaint.setAntiAlias(true);
        
        Paint p = mSelectionPaint;
        p.setColor(mDayViewResources.getGridLineColor());
        p.setStyle(Style.FILL);
        p.setAntiAlias(false);

        p = mPaint;
        p.setAntiAlias(true);

        // Allocate space for 2 weeks worth of weekday names so that we can
        // easily start the week display at any week day.
        mDayStrs = new String[14];

        // Also create an array of 2-letter abbreviations.
        mDayStrs2Letter = new String[14];

        for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++) {
            int index = i - Calendar.SUNDAY;
            // e.g. Tue for Tuesday
            mDayStrs[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_MEDIUM)
                    .toUpperCase();
            mDayStrs[index + 7] = mDayStrs[index];
            // e.g. Tu for Tuesday
            mDayStrs2Letter[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_SHORT)
                    .toUpperCase();

            // If we don't have 2-letter day strings, fall back to 1-letter.
            if (mDayStrs2Letter[index].equals(mDayStrs[index])) {
                mDayStrs2Letter[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_SHORTEST);
            }

            mDayStrs2Letter[index + 7] = mDayStrs2Letter[index];
        }

        // Figure out how much space we need for the 3-letter abbrev names
        // in the worst case.
        p.setTextSize(mDayViewResources.getDayHeaderFontSize());
        p.setTypeface(mBold);
        String[] dateStrs = {
                " 28", " 30"
        };
        mDateStrWidth = computeMaxStringWidth(0, dateStrs, p);
        p.setTextSize(mDayViewResources.getDayHeaderFontSize());
        mDateStrWidth += computeMaxStringWidth(0, mDayStrs, p);

        p.setTextSize(mDayViewResources.getHoursTextSize());
        p.setTypeface(null);
        handleOnResume();

        mAmString = DateUtils.getAMPMString(Calendar.AM).toUpperCase();
        mPmString = DateUtils.getAMPMString(Calendar.PM).toUpperCase();
        String[] ampm = {
                mAmString, mPmString
        };
        p.setTextSize(mDayViewResources.getAMPMTextSize());
        mHoursWidth = Math.max(mDayViewResources.getHoursMargin(),
                computeMaxStringWidth(mHoursWidth, ampm, p)
                        + mDayViewResources.getHoursRightMargin());
        mHoursWidth = Math.max(mDayViewResources.getMinHoursWidth(), mHoursWidth);

        LayoutInflater inflater;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(mDayViewResources.getEventPopupViewLayoutID(), null);
        mPopupView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mPopup = new PopupWindow(context);
        mPopup.setContentView(mPopupView);
        Resources.Theme dialogTheme = getResources().newTheme();
        dialogTheme.applyStyle(android.R.style.Theme_Dialog, true);
        TypedArray ta = dialogTheme.obtainStyledAttributes(new int[] {
                android.R.attr.windowBackground
        });
        mPopup.setBackgroundDrawable(ta.getDrawable(0));
        ta.recycle();

        // Enable touching the popup window
        mPopupView.setOnClickListener(this);
        // Catch long clicks for creating a new event

        mBaseDate = new Time(mDependencyFactory.buildTimezoneUtils().getTimeZone(context, mTZUpdater));
        long millis = System.currentTimeMillis();
        mBaseDate.set(millis);

        mEarliestStartHour = new int[mNumDays];
        mHasAllDayEvent = new boolean[mNumDays];


    }

    /**
     * This is called when the popup window is pressed.
     */
    public void onClick(View v) {
        if (v == mPopupView) {
            // Pretend it was a trackball click because that will always
            // jump to the "View event" screen.
            switchViews(true /* trackball */);
        }
    }

    public void handleOnResume() {
        initAccessibilityVariables();
        // Don't understand what this otherPreference is for. it's never set
        // anywhere. Historical, maybe?
        // if(mDependencyFactory.buildPreferencesUtils().getSharedPreference(mContext,
        // OtherPreferences.KEY_OTHER_1, false)) {
        // mFutureBgColor = 0;
        // } else {
        mFutureBgColor = mDayViewResources.getFutureBgColorRes();
        // }
        mIs24HourFormat = DateFormat.is24HourFormat(mContext);
        mHourStrs = mIs24HourFormat ? mDayViewResources.get24Hours() : mDayViewResources
                .get12HoursNoAmPm();
        mFirstDayOfWeek = mDependencyFactory.buildDateUtils().getFirstDayOfWeek(mContext);
        mLastSelectionDayForAccessibility = 0;
        mLastSelectionHourForAccessibility = 0;
        mLastSelectedEventForAccessibility = null;
        mSelectionMode = SELECTION_HIDDEN;
    }

    private void initAccessibilityVariables() {
        mAccessibilityMgr = (AccessibilityManager) mContext
                .getSystemService(Service.ACCESSIBILITY_SERVICE);
        mIsAccessibilityEnabled = mAccessibilityMgr != null && mAccessibilityMgr.isEnabled();
        mTouchExplorationEnabled = isTouchExplorationEnabled();
    }

    /**
     * Returns the start of the selected time in milliseconds since the epoch.
     * 
     * @return selected time in UTC milliseconds since the epoch.
     */
    public long getSelectedTimeInMillis() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDay);
        time.hour = mSelectionHour;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        return time.normalize(true /* ignore isDst */);
    }

    Time getSelectedTime() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDay);
        time.hour = mSelectionHour;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        time.normalize(true /* ignore isDst */);
        return time;
    }

    Time getSelectedTimeForAccessibility() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDayForAccessibility);
        time.hour = mSelectionHourForAccessibility;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        time.normalize(true /* ignore isDst */);
        return time;
    }

    /**
     * Returns the start of the selected time in minutes since midnight, local
     * time. The derived class must ensure that this is consistent with the
     * return value from getSelectedTimeInMillis().
     */
    int getSelectedMinutesSinceMidnight() {
        return mSelectionHour * MINUTES_PER_HOUR;
    }

    public int getFirstVisibleHour() {
        return mFirstHour;
    }

    public void setFirstVisibleHour(int firstHour) {
        mFirstHour = firstHour;
        mFirstHourOffset = 0;
    }

    public void setSelected(Time time, boolean ignoreTime, boolean animateToday) {
        mBaseDate.set(time);
        setSelectedHour(mBaseDate.hour);
        setSelectedEvent(null);
        mPrevSelectedEvent = null;
        long millis = mBaseDate.toMillis(false /* use isDst */);
        setSelectedDay(Time.getJulianDay(millis, mBaseDate.gmtoff));
        mSelectedEvents.clear();
        mComputeSelectedEvents = true;

        int gotoY = Integer.MIN_VALUE;

        if (!ignoreTime && mGridAreaHeight != -1) {
            int lastHour = 0;

            if (mBaseDate.hour < mFirstHour) {
                // Above visible region
                gotoY = mBaseDate.hour * (mCellHeight + mDayViewResources.getHourGap());
            } else {
                lastHour = (mGridAreaHeight - mFirstHourOffset) / (mCellHeight + mDayViewResources.getHourGap())
                        + mFirstHour;

                if (mBaseDate.hour >= lastHour) {
                    // Below visible region

                    // target hour + 1 (to give it room to see the event) -
                    // grid height (to get the y of the top of the visible
                    // region)
                    gotoY = (int) ((mBaseDate.hour + 1 + mBaseDate.minute / 60.0f)
                            * (mCellHeight + mDayViewResources.getHourGap()) - mGridAreaHeight);
                }
            }

            if (DEBUG) {
                Log.e(TAG, "Go " + gotoY + " 1st " + mFirstHour + ":" + mFirstHourOffset + "CH "
                        + (mCellHeight + mDayViewResources.getHourGap()) + " lh " + lastHour + " gh " + mGridAreaHeight
                        + " ymax " + mMaxViewStartY);
            }

            if (gotoY > mMaxViewStartY) {
                gotoY = mMaxViewStartY;
            } else if (gotoY < 0 && gotoY != Integer.MIN_VALUE) {
                gotoY = 0;
            }
        }

        recalc();

        mRemeasure = true;
        invalidate();

        boolean delayAnimateToday = false;
        if (gotoY != Integer.MIN_VALUE) {
            ValueAnimator scrollAnim = ObjectAnimator.ofInt(this, "viewStartY", mViewStartY, gotoY);
            scrollAnim.setDuration(GOTO_SCROLL_DURATION);
            scrollAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            scrollAnim.addListener(mAnimatorListener);
            scrollAnim.start();
            delayAnimateToday = true;
        }
        if (animateToday) {
            synchronized (mTodayAnimatorListener) {
                if (mTodayAnimator != null) {
                    mTodayAnimator.removeAllListeners();
                    mTodayAnimator.cancel();
                }
                mTodayAnimator = ObjectAnimator.ofInt(this, "animateTodayAlpha",
                        mAnimateTodayAlpha, 255);
                mAnimateToday = true;
                mTodayAnimatorListener.setFadingIn(true);
                mTodayAnimatorListener.setAnimator(mTodayAnimator);
                mTodayAnimator.addListener(mTodayAnimatorListener);
                mTodayAnimator.setDuration(150);
                if (delayAnimateToday) {
                    mTodayAnimator.setStartDelay(GOTO_SCROLL_DURATION);
                }
                mTodayAnimator.start();
            }
        }
        sendAccessibilityEventAsNeeded(false);
    }

    // Called from animation framework via reflection. Do not remove
    public void setViewStartY(int viewStartY) {
        if (viewStartY > mMaxViewStartY) {
            viewStartY = mMaxViewStartY;
        }

        mViewStartY = viewStartY;

        computeFirstHour();
        invalidate();
    }

    public void setAnimateTodayAlpha(int todayAlpha) {
        mAnimateTodayAlpha = todayAlpha;
        invalidate();
    }

    public Time getSelectedDay() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDay);
        time.hour = mSelectionHour;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        time.normalize(true /* ignore isDst */);
        return time;
    }

    public void updateTitle() {
        Time start = new Time(mBaseDate);
        start.normalize(true);
        Time end = new Time(start);
        end.monthDay += mNumDays - 1;
        // Move it forward one minute so the formatter doesn't lose a day
        end.minute += 1;
        end.normalize(true);
        
        //TODO: mNumDays should be part of the display model - should be shared between dayview and event handler - no need to supply as part of event
        mEventBus.post(new UpdateTitleEvent(start, end, mNumDays));
    }

    /**
     * return a negative number if "time" is comes before the visible time
     * range, a positive number if "time" is after the visible time range, and 0
     * if it is in the visible time range.
     */
    public int compareToVisibleTimeRange(Time time) {

        int savedHour = mBaseDate.hour;
        int savedMinute = mBaseDate.minute;
        int savedSec = mBaseDate.second;

        mBaseDate.hour = 0;
        mBaseDate.minute = 0;
        mBaseDate.second = 0;

        if (DEBUG) {
            Log.d(TAG, "Begin " + mBaseDate.toString());
            Log.d(TAG, "Diff  " + time.toString());
        }

        // Compare beginning of range
        int diff = Time.compare(time, mBaseDate);
        if (diff > 0) {
            // Compare end of range
            mBaseDate.monthDay += mNumDays;
            mBaseDate.normalize(true);
            diff = Time.compare(time, mBaseDate);

            if (DEBUG)
                Log.d(TAG, "End   " + mBaseDate.toString());

            mBaseDate.monthDay -= mNumDays;
            mBaseDate.normalize(true);
            if (diff < 0) {
                // in visible time
                diff = 0;
            } else if (diff == 0) {
                // Midnight of following day
                diff = 1;
            }
        }

        if (DEBUG)
            Log.d(TAG, "Diff: " + diff);

        mBaseDate.hour = savedHour;
        mBaseDate.minute = savedMinute;
        mBaseDate.second = savedSec;
        return diff;
    }

    private void recalc() {
        // Set the base date to the beginning of the week if we are displaying
        // 7 days at a time.
        if (mNumDays == 7) {
            adjustToBeginningOfWeek(mBaseDate);
        }

        final long start = mBaseDate.toMillis(false /* use isDst */);
        mFirstJulianDay = Time.getJulianDay(start, mBaseDate.gmtoff);
        mLastJulianDay = mFirstJulianDay + mNumDays - 1;

        mMonthLength = mBaseDate.getActualMaximum(Time.MONTH_DAY);
        mFirstVisibleDate = mBaseDate.monthDay;
        mFirstVisibleDayOfWeek = mBaseDate.weekDay;
    }

    private void adjustToBeginningOfWeek(Time time) {
        int dayOfWeek = time.weekDay;
        int diff = dayOfWeek - mFirstDayOfWeek;
        if (diff != 0) {
            if (diff < 0) {
                diff += 7;
            }
            time.monthDay -= diff;
            time.normalize(true /* ignore isDst */);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        mViewWidth = width;
        mViewHeight = height;
        mEdgeEffectTop.setSize(mViewWidth, mViewHeight);
        mEdgeEffectBottom.setSize(mViewWidth, mViewHeight);
        int gridAreaWidth = width - mHoursWidth;
        mCellWidth = (gridAreaWidth - (mNumDays * DAY_GAP)) / mNumDays;

        // This would be about 1 day worth in a 7 day view
        mHorizontalSnapBackThreshold = width / 7;

        Paint p = new Paint();
        p.setTextSize(mDayViewResources.getHoursTextSize());
        mHoursTextHeight = (int) Math.abs(p.ascent());
        remeasure(width, height);
    }

    /**
     * Measures the space needed for various parts of the view after loading new
     * events. This can change if there are all-day events.
     */
    private void remeasure(int width, int height) {
        // Shrink to fit available space but make sure we can display at least
        // two events
        mMaxUnexpandedAllDayHeight = (int) (mDayViewResources.getMinUnexpandedAllDayEventHeight() * 1);
        mMaxUnexpandedAllDayHeight = Math.min(mMaxUnexpandedAllDayHeight, height / 6);
        mMaxUnexpandedAllDayHeight = Math.max(mMaxUnexpandedAllDayHeight,
                (int) mDayViewResources.getMinUnexpandedAllDayEventHeight() * 2);
        mMaxUnexpandedAlldayEventCount =
                (int) (mMaxUnexpandedAllDayHeight / mDayViewResources
                        .getMinUnexpandedAllDayEventHeight());

        // First, clear the array of earliest start times, and the array
        // indicating presence of an all-day event.
        for (int day = 0; day < mNumDays; day++) {
            mEarliestStartHour[day] = 25; // some big number
            mHasAllDayEvent[day] = false;
        }

        int maxAllDayEvents = mMaxAlldayEvents;

        // The min is where 24 hours cover the entire visible area
        mMinCellHeight = Math.max((height - mDayViewResources.getDayHeaderHeight(mNumDays)) / 24,
                (int) mDayViewResources.getMinEventHeight());
        if (mCellHeight < mMinCellHeight) {
            mCellHeight = mMinCellHeight;
        }

        // Calculate mAllDayHeight
        mFirstCell = mDayViewResources.getDayHeaderHeight(mNumDays);
        int allDayHeight = 0;
        if (maxAllDayEvents > 0) {
            int maxAllAllDayHeight = height - mDayViewResources.getDayHeaderHeight(mNumDays)
                    - MIN_HOURS_HEIGHT;
            // If there is at most one all-day event per day, then use less
            // space (but more than the space for a single event).
            if (maxAllDayEvents == 1) {
                allDayHeight = mDayViewResources.getSingleAlldayHeight();
            } else if (maxAllDayEvents <= mMaxUnexpandedAlldayEventCount) {
                // Allow the all-day area to grow in height depending on the
                // number of all-day events we need to show, up to a limit.
                allDayHeight = maxAllDayEvents * mDayViewResources.getMaxHeightOfOneAlldayEvent();
                if (allDayHeight > mMaxUnexpandedAllDayHeight) {
                    allDayHeight = mMaxUnexpandedAllDayHeight;
                }
            } else {
                // if we have more than the magic number, check if we're
                // animating
                // and if not adjust the sizes appropriately
                if (mAnimateDayHeight != 0) {
                    // Don't shrink the space past the final allDay space. The
                    // animation
                    // continues to hide the last event so the more events text
                    // can
                    // fade in.
                    allDayHeight = Math.max(mAnimateDayHeight, mMaxUnexpandedAllDayHeight);
                } else {
                    // Try to fit all the events in
                    allDayHeight = (int) (maxAllDayEvents * mDayViewResources
                            .getMinUnexpandedAllDayEventHeight());
                    // But clip the area depending on which mode we're in
                    if (!mShowAllAllDayEvents && allDayHeight > mMaxUnexpandedAllDayHeight) {
                        allDayHeight = (int) (mMaxUnexpandedAlldayEventCount *
                                mMaxUnexpandedAllDayHeight);
                    } else if (allDayHeight > maxAllAllDayHeight) {
                        allDayHeight = maxAllAllDayHeight;
                    }
                }
            }
            mFirstCell = mDayViewResources.getDayHeaderHeight(mNumDays) + allDayHeight
                    + mDayViewResources.getAlldayTopMargin();
        } else {
            mSelectionAllday = false;
        }
        mAlldayHeight = allDayHeight;

        mGridAreaHeight = height - mFirstCell;

        // Set up the expand icon position
        int allDayIconWidth = mDayViewResources.getExpandAlldayDrawable().getIntrinsicWidth();
        mExpandAllDayRect.left = Math.max((mHoursWidth - allDayIconWidth) / 2,
                mDayViewResources.getEventAllDayTextLeftMargin());
        mExpandAllDayRect.right = Math.min(mExpandAllDayRect.left + allDayIconWidth, mHoursWidth
                - mDayViewResources.getEventAlldayTextRightMargin());
        mExpandAllDayRect.bottom = mFirstCell - mDayViewResources.getExpandAllDayBottomMargin();
        mExpandAllDayRect.top = mExpandAllDayRect.bottom
                - mDayViewResources.getExpandAlldayDrawable().getIntrinsicHeight();

        mNumHours = mGridAreaHeight / (mCellHeight + mDayViewResources.getHourGap());
        mEventGeometry.setHourHeight(mCellHeight);

        final long minimumDurationMillis = (long)
                (mDayViewResources.getMinEventHeight() * DateUtils.MINUTE_IN_MILLIS / (mCellHeight / 60.0f));
        EventLayout.computePositions(mEvents, minimumDurationMillis);

        // Compute the top of our reachable view
        mMaxViewStartY = mDayViewResources.getHourGap() + 24 * (mCellHeight + mDayViewResources.getHourGap()) - mGridAreaHeight;
        if (DEBUG) {
            Log.e(TAG, "mViewStartY: " + mViewStartY);
            Log.e(TAG, "mMaxViewStartY: " + mMaxViewStartY);
        }
        if (mViewStartY > mMaxViewStartY) {
            mViewStartY = mMaxViewStartY;
            computeFirstHour();
        }

        if (mFirstHour == -1) {
            initFirstHour();
            mFirstHourOffset = 0;
        }

        // When we change the base date, the number of all-day events may
        // change and that changes the cell height. When we switch dates,
        // we use the mFirstHourOffset from the previous view, but that may
        // be too large for the new view if the cell height is smaller.
        if (mFirstHourOffset >= mCellHeight + mDayViewResources.getHourGap()) {
            mFirstHourOffset = mCellHeight + mDayViewResources.getHourGap() - 1;
        }
        mViewStartY = mFirstHour * (mCellHeight + mDayViewResources.getHourGap()) - mFirstHourOffset;

        final int eventAreaWidth = mNumDays * (mCellWidth + DAY_GAP);
        // When we get new events we don't want to dismiss the popup unless the
        // event changes
        if (mSelectedEvent != null && mLastPopupEventID != mSelectedEvent.getEvent().getId()) {
            mPopup.dismiss();
        }
        mPopup.setWidth(eventAreaWidth - 20);
        mPopup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Initialize the state for another view. The given view is one that has its
     * own bitmap and will use an animation to replace the current view. The
     * current view and new view are either both Week views or both Day views.
     * They differ in their base date.
     * 
     * @param view the view to initialize.
     */
    private void initView(DayView view) {
        view.setSelectedHour(mSelectionHour);
        view.mSelectedEvents.clear();
        view.mComputeSelectedEvents = true;
        view.mFirstHour = mFirstHour;
        view.mFirstHourOffset = mFirstHourOffset;
        view.remeasure(getWidth(), getHeight());
        view.initAllDayHeights();

        view.setSelectedEvent(null);
        view.mPrevSelectedEvent = null;
        view.mFirstDayOfWeek = mFirstDayOfWeek;
        if (view.mEvents.size() > 0) {
            view.mSelectionAllday = mSelectionAllday;
        } else {
            view.mSelectionAllday = false;
        }

        // Redraw the screen so that the selection box will be redrawn. We may
        // have scrolled to a different part of the day in some other view
        // so the selection box in this view may no longer be visible.
        view.recalc();
    }

    /**
     * Switch to another view based on what was selected (an event or a free
     * slot) and how it was selected (by touch or by trackball).
     * 
     * @param trackBallSelection true if the selection was made using the
     *            trackball.
     */
    void switchViews(boolean trackBallSelection) {
        EventLayout selectedEvent = mSelectedEvent;

        mPopup.dismiss();
        mLastPopupEventID = INVALID_EVENT_ID;
        if (mNumDays > 1) {
            // This is the Week view.
            // With touch, we always switch to Day/Agenda View
            // With track ball, if we selected a free slot, then create an
            // event.
            // If we selected a specific event, switch to EventInfo view.
            if (trackBallSelection) {
                if (selectedEvent == null) {
                    // Switch to the EditEvent view
                    
                    mEventBus.post(new CreateEventEvent(getSelectedTimeInMillis(), mSelectionAllday));
                } else {
                    if (mIsAccessibilityEnabled) {
                        mAccessibilityMgr.interrupt();
                    }
                    // Switch to the EventInfo view
                    mEventBus.post(new ViewEventEvent(selectedEvent.getEvent(), getSelectedTimeInMillis()));
                }
            } else {
                // This was a touch selection. If the touch selected a single
                // unambiguous event, then view that event. Otherwise go to
                // Day/Agenda view.
                if (mSelectedEvents.size() == 1) {
                    if (mIsAccessibilityEnabled) {
                        mAccessibilityMgr.interrupt();
                    }
                    mEventBus.post(new ViewEventEvent(selectedEvent.getEvent(), getSelectedTimeInMillis()));
                }
            }
        } else {
            // This is the Day view.
            // If we selected a free slot, then create an event.
            // If we selected an event, then go to the EventInfo view.
            if (selectedEvent == null) {
                // Switch to the EditEvent view
                mEventBus.post(new CreateEventEvent(getSelectedTimeInMillis(), mSelectionAllday));
            } else {
                if (mIsAccessibilityEnabled) {
                    mAccessibilityMgr.interrupt();
                }
                mEventBus.post(new ViewEventEvent(selectedEvent.getEvent(), getSelectedTimeInMillis()));
            }
        }
    }

    public void switchToDay(int selectionDay) {
        
        
        if ((selectionDay < getFirstJulianDay()) || (selectionDay > getLastJulianDay())) {
            DayView view = (DayView) mViewSwitcher.getNextView();
            Time date = view.mBaseDate;
            date.set(mBaseDate);
            if (selectionDay < mFirstJulianDay) {
                date.monthDay -= mNumDays;
            } else {
                date.monthDay += mNumDays;
            }
            date.normalize(true /* ignore isDst */);
            view.setSelectedDay(selectionDay);

            initView(view);

            Time end = new Time(date);
            end.hour = mSelectionHour;
            end.monthDay += mNumDays - 1;
            
            getEventBus().post(new ShowDateInCurrentViewEvent(date, end));
        } else if (mSelectionDay != selectionDay) {
            //new day is on the current calendar
            mSelectionDay = selectionDay;
            Time date = new Time(mBaseDate);
            date.setJulianDay(selectionDay);
            date.hour = mSelectionHour;
            
            getEventBus().post(new ShowDateInCurrentViewEvent(date, date));
        }
        
        setSelectedDay(selectionDay);
        mSelectedEvents.clear();
        mComputeSelectedEvents = true;
        mUpdateToast = true;
    }
    
    

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (DEBUG) {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    Log.e(TAG, "ACTION_HOVER_ENTER");
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    Log.e(TAG, "ACTION_HOVER_MOVE");
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    Log.e(TAG, "ACTION_HOVER_EXIT");
                    break;
                default:
                    Log.e(TAG, "Unknown hover event action. " + event);
            }
        }

        // Mouse also generates hover events
        // Send accessibility events if accessibility and exploration are on.
        if (!mTouchExplorationEnabled) {
            return super.onHoverEvent(event);
        }
        if (event.getAction() != MotionEvent.ACTION_HOVER_EXIT) {
            setSelectionFromPosition((int) event.getX(), (int) event.getY(), true);
            invalidate();
        }
        return true;
    }

    private boolean isTouchExplorationEnabled() {
        return mIsAccessibilityEnabled && mAccessibilityMgr.isTouchExplorationEnabled();
    }

    private void sendAccessibilityEventAsNeeded(boolean speakEvents) {
        if (!mIsAccessibilityEnabled) {
            return;
        }
        boolean dayChanged = mLastSelectionDayForAccessibility != mSelectionDayForAccessibility;
        boolean hourChanged = mLastSelectionHourForAccessibility != mSelectionHourForAccessibility;
        if (dayChanged || hourChanged ||
                mLastSelectedEventForAccessibility != mSelectedEventForAccessibility) {
            mLastSelectionDayForAccessibility = mSelectionDayForAccessibility;
            mLastSelectionHourForAccessibility = mSelectionHourForAccessibility;
            mLastSelectedEventForAccessibility = mSelectedEventForAccessibility;

            StringBuilder b = new StringBuilder();

            // Announce only the changes i.e. day or hour or both
            if (dayChanged) {
                b.append(getSelectedTimeForAccessibility().format("%A "));
            }
            if (hourChanged) {
                b.append(getSelectedTimeForAccessibility().format(mIs24HourFormat ? "%k" : "%l%p"));
            }
            if (dayChanged || hourChanged) {
                b.append(PERIOD_SPACE);
            }

            if (speakEvents) {
                // Read out the relevant event(s)
                int numEvents = mSelectedEvents.size();
                if (numEvents > 0) {
                    if (mSelectedEventForAccessibility == null) {
                        // Read out all the events
                        int i = 1;
                        for (EventLayout calEvent : mSelectedEvents) {
                            if (numEvents > 1) {
                                // Read out x of numEvents if there are more
                                // than one event
                                mStringBuilder.setLength(0);
                                b.append(mFormatter.format(mDayViewResources.getEventCountTemplate(), i++, numEvents));
                                b.append(" ");
                            }
                            appendEventAccessibilityString(b, calEvent.getEvent());
                        }
                    } else {
                        if (numEvents > 1) {
                            // Read out x of numEvents if there are more than
                            // one event
                            mStringBuilder.setLength(0);
                            b.append(mFormatter.format(mDayViewResources.getEventCountTemplate(), mSelectedEvents
                                    .indexOf(mSelectedEventForAccessibility) + 1, numEvents));
                            b.append(" ");
                        }
                        appendEventAccessibilityString(b, mSelectedEventForAccessibility.getEvent());
                    }
                } else {
                    b.append(mDayViewResources.getCreateNewEventString());
                }
            }

            if (dayChanged || hourChanged || speakEvents) {
                AccessibilityEvent event = AccessibilityEvent
                        .obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                CharSequence msg = b.toString();
                event.getText().add(msg);
                event.setAddedCount(msg.length());
                sendAccessibilityEventUnchecked(event);
            }
        }
    }

    /**
     * @param b
     * @param calEvent
     */
    private void appendEventAccessibilityString(StringBuilder b, Event calEvent) {
        b.append(calEvent.getTitleAndLocation());
        b.append(PERIOD_SPACE);
        String when;
        int flags = DateUtils.FORMAT_SHOW_DATE;
        if (calEvent.isAllDay()) {
            flags |= DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_WEEKDAY;
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(mContext)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        }
        when = mDependencyFactory.buildTimezoneUtils().formatDateRange(mContext, calEvent.getStartMillis(),
                calEvent.getEndMillis(), flags);
        b.append(when);
        b.append(PERIOD_SPACE);
    }

    private class GotoBroadcaster implements Animation.AnimationListener {
        private final int mCounter;
        private final Time mStart;
        private final Time mEnd;

        public GotoBroadcaster(Time start, Time end) {
            mCounter = ++sCounter;
            mStart = start;
            mEnd = end;
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            DayView view = (DayView) mViewSwitcher.getCurrentView();
            view.mViewStartX = 0;
            view = (DayView) mViewSwitcher.getNextView();
            view.mViewStartX = 0;

            if (mCounter == sCounter) {
                mEventBus.post(new ShowDateInCurrentViewEvent(mStart, mEnd));
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    private View switchViews(boolean forward, float xOffSet, float width, float velocity) {
        mAnimationDistance = width - xOffSet;
        if (DEBUG) {
            Log.d(TAG, "switchViews(" + forward + ") O:" + xOffSet + " Dist:" + mAnimationDistance);
        }

        float progress = Math.abs(xOffSet) / width;
        if (progress > 1.0f) {
            progress = 1.0f;
        }

        float inFromXValue, inToXValue;
        float outFromXValue, outToXValue;
        if (forward) {
            inFromXValue = 1.0f - progress;
            inToXValue = 0.0f;
            outFromXValue = -progress;
            outToXValue = -1.0f;
        } else {
            inFromXValue = progress - 1.0f;
            inToXValue = 0.0f;
            outFromXValue = progress;
            outToXValue = 1.0f;
        }

        final Time start = new Time(mBaseDate);
        if (forward) {
            start.monthDay += mNumDays;
        } else {
            start.monthDay -= mNumDays;
        }

        start.normalize(true);
        
        Time newSelected = start;

        if (mNumDays == 7) {
            newSelected = new Time(start);
            adjustToBeginningOfWeek(start);
        }

        final Time end = new Time(start);
        end.monthDay += mNumDays - 1;

        // We have to allocate these animation objects each time we switch views
        // because that is the only way to set the animation parameters.
        TranslateAnimation inAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, inFromXValue,
                Animation.RELATIVE_TO_SELF, inToXValue,
                Animation.ABSOLUTE, 0.0f,
                Animation.ABSOLUTE, 0.0f);

        TranslateAnimation outAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, outFromXValue,
                Animation.RELATIVE_TO_SELF, outToXValue,
                Animation.ABSOLUTE, 0.0f,
                Animation.ABSOLUTE, 0.0f);

        long duration = calculateDuration(width - Math.abs(xOffSet), width, velocity);
        inAnimation.setDuration(duration);
        inAnimation.setInterpolator(mHScrollInterpolator);
        outAnimation.setInterpolator(mHScrollInterpolator);
        outAnimation.setDuration(duration);
        outAnimation.setAnimationListener(new GotoBroadcaster(start, end));
        mViewSwitcher.setInAnimation(inAnimation);
        mViewSwitcher.setOutAnimation(outAnimation);

        DayView view = (DayView) mViewSwitcher.getCurrentView();
        view.cleanup();
        mViewSwitcher.showNext();
        view = (DayView) mViewSwitcher.getCurrentView();
        view.setSelected(newSelected, true, false);
        view.requestFocus();
        view.reloadEvents();
        view.updateTitle();
        view.restartCurrentTimeUpdates();

        return view;
    }

    // This is called after scrolling stops to move the selected hour
    // to the visible part of the screen.
    private void resetSelectedHour() {
        if (mSelectionHour < mFirstHour + 1) {
            setSelectedHour(mFirstHour + 1);
            setSelectedEvent(null);
            mSelectedEvents.clear();
            mComputeSelectedEvents = true;
        } else if (mSelectionHour > mFirstHour + mNumHours - 3) {
            setSelectedHour(mFirstHour + mNumHours - 3);
            setSelectedEvent(null);
            mSelectedEvents.clear();
            mComputeSelectedEvents = true;
        }
    }

    private void initFirstHour() {
        mFirstHour = mSelectionHour - mNumHours / 5;
        if (mFirstHour < 0) {
            mFirstHour = 0;
        } else if (mFirstHour + mNumHours > 24) {
            mFirstHour = 24 - mNumHours;
        }
    }

    /**
     * Recomputes the first full hour that is visible on screen after the screen
     * is scrolled.
     */
    private void computeFirstHour() {
        // Compute the first full hour that is visible on screen
        mFirstHour = (mViewStartY + mCellHeight + mDayViewResources.getHourGap() - 1) / (mCellHeight + mDayViewResources.getHourGap());
        mFirstHourOffset = mFirstHour * (mCellHeight + mDayViewResources.getHourGap()) - mViewStartY;
    }

    void adjustHourSelection() {
        if (mSelectionHour < 0) {
            setSelectedHour(0);
            if (mMaxAlldayEvents > 0) {
                mPrevSelectedEvent = null;
                mSelectionAllday = true;
            }
        }

        if (mSelectionHour > 23) {
            setSelectedHour(23);
        }

        // If the selected hour is at least 2 time slots from the top and
        // bottom of the screen, then don't scroll the view.
        if (mSelectionHour < mFirstHour + 1) {
            // If there are all-days events for the selected day but there
            // are no more normal events earlier in the day, then jump to
            // the all-day event area.
            // Exception 1: allow the user to scroll to 8am with the trackball
            // before jumping to the all-day event area.
            // Exception 2: if 12am is on screen, then allow the user to select
            // 12am before going up to the all-day event area.
            int daynum = mSelectionDay - mFirstJulianDay;
            if (mMaxAlldayEvents > 0 && mEarliestStartHour[daynum] > mSelectionHour
                    && mFirstHour > 0 && mFirstHour < 8) {
                mPrevSelectedEvent = null;
                mSelectionAllday = true;
                setSelectedHour(mFirstHour + 1);
                return;
            }

            if (mFirstHour > 0) {
                mFirstHour -= 1;
                mViewStartY -= (mCellHeight + mDayViewResources.getHourGap());
                if (mViewStartY < 0) {
                    mViewStartY = 0;
                }
                return;
            }
        }

        if (mSelectionHour > mFirstHour + mNumHours - 3) {
            if (mFirstHour < 24 - mNumHours) {
                mFirstHour += 1;
                mViewStartY += (mCellHeight + mDayViewResources.getHourGap());
                if (mViewStartY > mMaxViewStartY) {
                    mViewStartY = mMaxViewStartY;
                }
                return;
            } else if (mFirstHour == 24 - mNumHours && mFirstHourOffset > 0) {
                mViewStartY = mMaxViewStartY;
            }
        }
    }

    public void clearCachedEvents() {
        mLastReloadMillis = 0;
    }

    private final Runnable mCancelCallback = new Runnable() {
        public void run() {
            clearCachedEvents();
        }
    };
    private DayViewRenderer mDayViewRenderer;

    /* package */public void reloadEvents() {
        // Protect against this being called before this view has been
        // initialized.
        // if (mContext == null) {
        // return;
        // }

        // Make sure our time zones are up to date
        mTZUpdater.run();

        setSelectedEvent(null);
        mPrevSelectedEvent = null;
        mSelectedEvents.clear();

        // The start date is the beginning of the week at 12am
        Time weekStart = new Time(mDependencyFactory.buildTimezoneUtils().getTimeZone(mContext,
                mTZUpdater));
        weekStart.set(mBaseDate);
        weekStart.hour = 0;
        weekStart.minute = 0;
        weekStart.second = 0;
        long millis = weekStart.normalize(true /* ignore isDst */);

        // Avoid reloading events unnecessarily.
        if (millis == mLastReloadMillis) {
            return;
        }
        mLastReloadMillis = millis;

        // load events in the background
        // mContext.startProgressSpinner();
        final ArrayList<Event> events = new ArrayList<Event>();
        mEventLoader.loadEventsInBackground(mNumDays, events, mFirstJulianDay, new Runnable() {
            public void run() {
                boolean fadeinEvents = mFirstJulianDay != mLoadedFirstJulianDay;
                
                mEvents.clear();
                for (Event event : events) {
                    mEvents.add(new EventLayout(event));
                }
                mLoadedFirstJulianDay = mFirstJulianDay;
               
                mAllDayEvents.clear();
                

                // Create a shorter array for all day events
                //share references between mEvents and mAllDayEvents 
                for (EventLayout e : mEvents) {
                    if (e.getEvent().drawAsAllday()) {
                        mAllDayEvents.add(e);
                    }
                }

                // New events, new layouts
                mEventRenderer.prepareForEvents(events);

                if (mAllDayLayouts == null || mAllDayLayouts.length < mAllDayEvents.size()) {
                    mAllDayLayouts = new StaticLayout[events.size()];
                } else {
                    Arrays.fill(mAllDayLayouts, null);
                }

                computeEventRelations();

                mRemeasure = true;
                mComputeSelectedEvents = true;
                recalc();

                // Start animation to cross fade the events
                if (fadeinEvents) {
                    if (mEventsCrossFadeAnimation == null) {
                        mEventsCrossFadeAnimation =
                                ObjectAnimator.ofInt(DayView.this, "EventsAlpha", 0, 255);
                        mEventsCrossFadeAnimation.setDuration(EVENTS_CROSS_FADE_DURATION);
                    }
                    mEventsCrossFadeAnimation.start();
                } else {
                    invalidate();
                }
            }
        }, mCancelCallback);
    }

    public void stopEventsAnimation() {
        if (mEventsCrossFadeAnimation != null) {
            mEventsCrossFadeAnimation.cancel();
        }
        mEventsAlpha = 255;
    }

    private void computeEventRelations() {
        // Compute the layout relation between each event before measuring cell
        // width, as the cell width should be adjusted along with the relation.
        //
        // Examples: A (1:00pm - 1:01pm), B (1:02pm - 2:00pm)
        // We should mark them as "overwapped". Though they are not overwapped
        // logically, but
        // minimum cell height implicitly expands the cell height of A and it
        // should look like
        // (1:00pm - 1:15pm) after the cell height adjustment.

        // Compute the space needed for the all-day events, if any.
        // Make a pass over all the events, and keep track of the maximum
        // number of all-day events in any one day. Also, keep track of
        // the earliest event in each day.
        int maxAllDayEvents = 0;
        final ArrayList<EventLayout> events = mEvents;
        final int len = events.size();
        // Num of all-day-events on each day.
        final int eventsCount[] = new int[mLastJulianDay - mFirstJulianDay + 1];
        Arrays.fill(eventsCount, 0);
        for (int ii = 0; ii < len; ii++) {
            Event event = events.get(ii).getEvent();
            if (event.getStartDay() > mLastJulianDay || event.getEndDay() < mFirstJulianDay) {
                continue;
            }
            if (event.drawAsAllday()) {
                // Count all the events being drawn as allDay events
                final int firstDay = Math.max(event.getStartDay(), mFirstJulianDay);
                final int lastDay = Math.min(event.getEndDay(), mLastJulianDay);
                for (int day = firstDay; day <= lastDay; day++) {
                    final int count = ++eventsCount[day - mFirstJulianDay];
                    if (maxAllDayEvents < count) {
                        maxAllDayEvents = count;
                    }
                }

                int daynum = event.getStartDay() - mFirstJulianDay;
                int durationDays = event.getEndDay() - event.getStartDay() + 1;
                if (daynum < 0) {
                    durationDays += daynum;
                    daynum = 0;
                }
                if (daynum + durationDays > mNumDays) {
                    durationDays = mNumDays - daynum;
                }
                for (int day = daynum; durationDays > 0; day++, durationDays--) {
                    mHasAllDayEvent[day] = true;
                }
            } else {
                int daynum = event.getStartDay() - mFirstJulianDay;
                int hour = event.getStartTime() / 60;
                if (daynum >= 0 && hour < mEarliestStartHour[daynum]) {
                    mEarliestStartHour[daynum] = hour;
                }

                // Also check the end hour in case the event spans more than
                // one day.
                daynum = event.getEndDay() - mFirstJulianDay;
                hour = event.getEndTime() / 60;
                if (daynum < mNumDays && hour < mEarliestStartHour[daynum]) {
                    mEarliestStartHour[daynum] = hour;
                }
            }
        }
        mMaxAlldayEvents = maxAllDayEvents;
        initAllDayHeights();
    }

    @SuppressLint("WrongCall")
	@Override
    protected void onDraw(Canvas canvas) {
        if (mRemeasure) {
            remeasure(getWidth(), getHeight());
            mRemeasure = false;
        }
        canvas.save();

        float yTranslate = -mViewStartY + mDayViewResources.getDayHeaderHeight(mNumDays)
                + mAlldayHeight;
        // offset canvas by the current drag and header position
        canvas.translate(-mViewStartX, yTranslate);
        // clip to everything below the allDay area
        Rect dest = mDestRect;
        dest.top = (int) (mFirstCell - yTranslate);
        dest.bottom = (int) (mViewHeight - yTranslate);
        dest.left = 0;
        dest.right = mViewWidth;
        canvas.save();
        canvas.clipRect(dest);
        // Draw the movable part of the view
        doDraw(canvas);
        // restore to having no clip
        canvas.restore();

        if (mScrollController.isHorizontalScrolling()) {
            float xTranslate;
            if (mViewStartX > 0) {
                xTranslate = mViewWidth;
            } else {
                xTranslate = -mViewWidth;
            }
            // Move the canvas around to prep it for the next view
            // specifically, shift it by a screen and undo the
            // yTranslation which will be redone in the nextView's onDraw().
            canvas.translate(xTranslate, -yTranslate);
            DayView nextView = (DayView) mViewSwitcher.getNextView();

            // Prevent infinite recursive calls to onDraw().
            nextView.mScrollController.reset();

            nextView.onDraw(canvas);
            // Move it back for this view
            canvas.translate(-xTranslate, 0);
        } else {
            // If we drew another view we already translated it back
            // If we didn't draw another view we should be at the edge of the
            // screen
            canvas.translate(mViewStartX, -yTranslate);
        }

        // Draw the fixed areas (that don't scroll) directly to the canvas.
        drawAfterScroll(canvas);
        if (mComputeSelectedEvents && mUpdateToast) {
            updateEventDetails();
            mUpdateToast = false;
        }
        mComputeSelectedEvents = false;

        // Draw overscroll glow
        if (!mEdgeEffectTop.isFinished()) {
            if (mDayViewResources.getDayHeaderHeight(mNumDays) != 0) {
                canvas.translate(0, mDayViewResources.getDayHeaderHeight(mNumDays));
            }
            if (mEdgeEffectTop.draw(canvas)) {
                invalidate();
            }
            if (mDayViewResources.getDayHeaderHeight(mNumDays) != 0) {
                canvas.translate(0, -mDayViewResources.getDayHeaderHeight(mNumDays));
            }
        }
        if (!mEdgeEffectBottom.isFinished()) {
            canvas.rotate(180, mViewWidth / 2, mViewHeight / 2);
            if (mEdgeEffectBottom.draw(canvas)) {
                invalidate();
            }
        }
        canvas.restore();
    }

    private void drawAfterScroll(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;

        drawAllDayHighlights(r, canvas, p);
        if (mMaxAlldayEvents != 0) {
            drawAllDayEvents(mFirstJulianDay, mNumDays, canvas, p);
            drawUpperLeftCorner(r, canvas, p);
        }

        drawScrollLine(r, canvas, p);
        drawDayHeaderLoop(r, canvas, p);

        // Draw the AM and PM indicators if we're in 12 hour mode
        if (!mIs24HourFormat) {
            drawAmPm(canvas, p);
        }
    }

    // This isn't really the upper-left corner. It's the square area just
    // below the upper-left corner, above the hours and to the left of the
    // all-day area.
    private void drawUpperLeftCorner(Rect r, Canvas canvas, Paint p) {
        setupHourTextPaint(p);
        if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
            // Draw the allDay expand/collapse icon
            if (mUseExpandIcon) {
                mDayViewResources.getExpandAlldayDrawable().setBounds(mExpandAllDayRect);
                mDayViewResources.getExpandAlldayDrawable().draw(canvas);
            } else {
                mDayViewResources.getCollapseAlldayDrawable().setBounds(mExpandAllDayRect);
                mDayViewResources.getCollapseAlldayDrawable().draw(canvas);
            }
        }
    }

    private void drawScrollLine(Rect r, Canvas canvas, Paint p) {
        final int right = computeDayLeftPosition(mNumDays);
        final int y = mFirstCell - 1;

        p.setAntiAlias(false);
        p.setStyle(Style.FILL);

        p.setColor(mDayViewResources.getCalendarGridLineInnerHorizontalColor());
        p.setStrokeWidth(mDayViewResources.getGridLineWidth());
        canvas.drawLine(mDayViewResources.getGridLineLeftMargin(), y, right, y, p);
        p.setAntiAlias(true);
    }

    // Computes the x position for the left side of the given day (base 0)
    private int computeDayLeftPosition(int day) {
        int effectiveWidth = mViewWidth - mHoursWidth;
        return day * effectiveWidth / mNumDays + mHoursWidth;
    }
    
    private int[] computeDayLeftEdge() {
        int dayStops[] = new int[mNumDays + 1];
        for(int i = 0; i < dayStops.length; i++){
            dayStops[i] = computeDayLeftPosition(i);
        }
        return dayStops;
    }
    
    
    private void drawAllDayHighlights(Rect r, Canvas canvas, Paint p) {
        if (mFutureBgColor != 0) {
            // First, color the labels area light gray
            r.top = 0;
            r.bottom = mDayViewResources.getDayHeaderHeight(mNumDays);
            r.left = 0;
            r.right = mViewWidth;
            p.setColor(mDayViewResources.getBgColor());
            p.setStyle(Style.FILL);
            canvas.drawRect(r, p);
            // and the area that says All day
            r.top = mDayViewResources.getDayHeaderHeight(mNumDays);
            r.bottom = mFirstCell - 1;
            r.left = 0;
            r.right = mHoursWidth;
            canvas.drawRect(r, p);

            int startIndex = -1;

            int todayIndex = mTodayJulianDay - mFirstJulianDay;
            if (todayIndex < 0) {
                // Future
                startIndex = 0;
            } else if (todayIndex >= 1 && todayIndex + 1 < mNumDays) {
                // Multiday - tomorrow is visible.
                startIndex = todayIndex + 1;
            }

            if (startIndex >= 0) {
                // Draw the future highlight
                r.top = 0;
                r.bottom = mFirstCell - 1;
                r.left = computeDayLeftPosition(startIndex) + 1;
                r.right = computeDayLeftPosition(mNumDays);
                p.setColor(mFutureBgColor);
                p.setStyle(Style.FILL);
                canvas.drawRect(r, p);
            }
        }

        if (mSelectionAllday && mSelectionMode != SELECTION_HIDDEN) {
            // Draw the selection highlight on the selected all-day area
            mRect.top = mDayViewResources.getDayHeaderHeight(mNumDays) + 1;
            mRect.bottom = mRect.top + mAlldayHeight + mDayViewResources.getAlldayTopMargin() - 2;
            int daynum = mSelectionDay - mFirstJulianDay;
            mRect.left = computeDayLeftPosition(daynum) + 1;
            mRect.right = computeDayLeftPosition(daynum + 1);
            p.setColor(mDayViewResources.getCalendarGridAreaSelected());
            canvas.drawRect(mRect, p);
        }
    }

    private void drawDayHeaderLoop(Rect r, Canvas canvas, Paint p) {
        // Draw the horizontal day background banner
        // p.setColor(mCalendarDateBannerBackground);
        // r.top = 0;
        // r.bottom = DAY_HEADER_HEIGHT;
        // r.left = 0;
        // r.right = mHoursWidth + mNumDays * (mCellWidth + DAY_GAP);
        // canvas.drawRect(r, p);
        //
        // Fill the extra space on the right side with the default background
        // r.left = r.right;
        // r.right = mViewWidth;
        // p.setColor(mCalendarGridAreaBackground);
        // canvas.drawRect(r, p);
        if (mNumDays == 1 && mDayViewResources.getOneDayHeaderHeight() == 0) {
            return;
        }

        p.setTypeface(mBold);
        p.setTextAlign(Paint.Align.RIGHT);
        int cell = mFirstJulianDay;

        String[] dayNames;
        if (mDateStrWidth < mCellWidth) {
            dayNames = mDayStrs;
        } else {
            dayNames = mDayStrs2Letter;
        }

        p.setAntiAlias(true);
        for (int day = 0; day < mNumDays; day++, cell++) {
            int dayOfWeek = day + mFirstVisibleDayOfWeek;
            if (dayOfWeek >= 14) {
                dayOfWeek -= 14;
            }

            int color = mDayViewResources.getCalendarDateBannerTextColor();
            if (mNumDays == 1) {
                if (dayOfWeek == Time.SATURDAY) {
                    color = mDayViewResources.getWeekSaturdayColor();
                } else if (dayOfWeek == Time.SUNDAY) {
                    color = mDayViewResources.getWeekSundayColor();
                }
            } else {
                final int column = day % 7;
                if (mDependencyFactory.buildDateUtils().isSaturday(column, mFirstDayOfWeek)) {
                    color = mDayViewResources.getWeekSaturdayColor();
                } else if (mDependencyFactory.buildDateUtils().isSunday(column, mFirstDayOfWeek)) {
                    color = mDayViewResources.getWeekSundayColor();
                }
            }

            p.setColor(color);
            drawDayHeader(dayNames[dayOfWeek], day, cell, canvas, p);
        }
        p.setTypeface(null);
    }

    private void drawAmPm(Canvas canvas, Paint p) {
        p.setColor(mDayViewResources.getCalendarAmPmLabel());
        p.setTextSize(mDayViewResources.getAMPMTextSize());
        p.setTypeface(mBold);
        p.setAntiAlias(true);
        p.setTextAlign(Paint.Align.RIGHT);
        String text = mAmString;
        if (mFirstHour >= 12) {
            text = mPmString;
        }
        int y = mFirstCell + mFirstHourOffset + 2 * mHoursTextHeight + mDayViewResources.getHourGap();
        canvas.drawText(text, mDayViewResources.getHoursLeftMargin(), y, p);

        if (mFirstHour < 12 && mFirstHour + mNumHours > 12) {
            // Also draw the "PM"
            text = mPmString;
            y = mFirstCell + mFirstHourOffset + (12 - mFirstHour) * (mCellHeight + mDayViewResources.getHourGap())
                    + 2 * mHoursTextHeight + mDayViewResources.getHourGap();
            canvas.drawText(text, mDayViewResources.getHoursLeftMargin(), y, p);
        }
    }

    private void drawCurrentTimeLine(Rect r, final int day, final int top, Canvas canvas,
            Paint p) {
        r.left = computeDayLeftPosition(day) - mDayViewResources.getCurrentTimeLineSideBuffer() + 1;
        r.right = computeDayLeftPosition(day + 1) + mDayViewResources.getCurrentTimeLineSideBuffer() + 1;

        r.top = top - mDayViewResources.getCurrentTimeLineTopOffset();
        r.bottom = r.top + mDayViewResources.getCurrentTimeLine().getIntrinsicHeight();

        mDayViewResources.getCurrentTimeLine().setBounds(r);
        mDayViewResources.getCurrentTimeLine().draw(canvas);
        if (mAnimateToday) {
            mDayViewResources.getCurrentTimeAnimateLine().setBounds(r);
            mDayViewResources.getCurrentTimeAnimateLine().setAlpha(mAnimateTodayAlpha);
            mDayViewResources.getCurrentTimeAnimateLine().draw(canvas);
        }
    }

    private void doDraw(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;

        if (mFutureBgColor != 0) {
            drawBgColors(r, canvas, p);
        }
        drawGridBackground(r, canvas, p);
        drawHours(r, canvas, p);

        // Draw each day
        int cell = mFirstJulianDay;
        p.setAntiAlias(false);
        int alpha = p.getAlpha();
        p.setAlpha(mEventsAlpha);
        for (int day = 0; day < mNumDays; day++, cell++) {
            // TODO Wow, this needs cleanup. drawEvents loop through all the
            // events on every call.
            drawEvents(cell, day, mDayViewResources.getHourGap(), canvas, p);
            // If this is today
            if (cell == mTodayJulianDay) {
                int lineY = mCurrentTime.hour * (mCellHeight + mDayViewResources.getHourGap())
                        + ((mCurrentTime.minute * mCellHeight) / 60) + 1;

                // And the current time shows up somewhere on the screen
                if (lineY >= mViewStartY && lineY < mViewStartY + mViewHeight - 2) {
                    drawCurrentTimeLine(r, day, lineY, canvas, p);
                }
            }
        }
        p.setAntiAlias(true);
        p.setAlpha(alpha);

        drawSelectedRect(r, canvas, p);
    }

    private void drawSelectedRect(Rect r, Canvas canvas, Paint p) {
        // Draw a highlight on the selected hour (if needed)
        if (mSelectionMode != SELECTION_HIDDEN && !mSelectionAllday) {
            int daynum = mSelectionDay - mFirstJulianDay;
            r.top = mSelectionHour * (mCellHeight + mDayViewResources.getHourGap());
            r.bottom = r.top + mCellHeight + mDayViewResources.getHourGap();
            r.left = computeDayLeftPosition(daynum) + 1;
            r.right = computeDayLeftPosition(daynum + 1) + 1;

            saveSelectionPosition(r.left, r.top, r.right, r.bottom);

            // Draw the highlight on the grid
            p.setColor(mDayViewResources.getCalendarGridAreaSelected());
            r.top += mDayViewResources.getHourGap();
            r.right -= DAY_GAP;
            p.setAntiAlias(false);
            canvas.drawRect(r, p);

            // Draw a "new event hint" on top of the highlight
            // For the week view, show a "+", for day view, show "+ New event"
            p.setColor(mDayViewResources.getNewEventHintColor());
            if (mNumDays > 1) {
                p.setStrokeWidth(mDayViewResources.getNewEventWidth());
                int width = r.right - r.left;
                int midX = r.left + width / 2;
                int midY = r.top + mCellHeight / 2;
                int length = Math.min(mCellHeight, width) - mDayViewResources.getNewEventMargin() * 2;
                length = Math.min(length, mDayViewResources.getNewEventMaxLength());
                int verticalPadding = (mCellHeight - length) / 2;
                int horizontalPadding = (width - length) / 2;
                canvas.drawLine(r.left + horizontalPadding, midY, r.right - horizontalPadding,
                        midY, p);
                canvas.drawLine(midX, r.top + verticalPadding, midX, r.bottom - verticalPadding, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(mDayViewResources.getNewEventHintTextFontSize());
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
                canvas.drawText(
                        mDayViewResources.getNewEventHintString(),
                        r.left + mDayViewResources.getEventTextLeftMargin(),
                        r.top + Math.abs(p.getFontMetrics().ascent)
                                + mDayViewResources.getEventTextTopMargin(), p);
            }
        }
    }

    private void drawHours(Rect r, Canvas canvas, Paint p) {
        setupHourTextPaint(p);

        int y = mDayViewResources.getHourGap() + mHoursTextHeight + mDayViewResources.getHoursTopMargin();

        for (int i = 0; i < 24; i++) {
            String time = mHourStrs[i];
            canvas.drawText(time, mDayViewResources.getHoursLeftMargin(), y, p);
            y += mCellHeight + mDayViewResources.getHourGap();
        }
    }

    private void setupHourTextPaint(Paint p) {
        p.setColor(mDayViewResources.getCalendarHourLabelColor());
        p.setTextSize(mDayViewResources.getHoursTextSize());
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setAntiAlias(true);
    }

    private void drawDayHeader(String dayStr, int day, int cell, Canvas canvas, Paint p) {
        int dateNum = mFirstVisibleDate + day;
        int x;
        if (dateNum > mMonthLength) {
            dateNum -= mMonthLength;
        }
        p.setAntiAlias(true);

        int todayIndex = mTodayJulianDay - mFirstJulianDay;
        // Draw day of the month
        String dateNumStr = String.valueOf(dateNum);
        if (mNumDays > 1) {
            float y = mDayViewResources.getDayHeaderHeight(mNumDays)
                    - mDayViewResources.getDayHeaderBottomMargin();

            // Draw day of the month
            x = computeDayLeftPosition(day + 1) - mDayViewResources.getDayHeaderRightMargin();
            p.setTextAlign(Align.RIGHT);
            p.setTextSize(mDayViewResources.getDayHeaderFontSize());

            p.setTypeface(todayIndex == day ? mBold : Typeface.DEFAULT);
            canvas.drawText(dateNumStr, x, y, p);

            // Draw day of the week
            x -= p.measureText(" " + dateNumStr);
            p.setTextSize(mDayViewResources.getDayHeaderFontSize());
            p.setTypeface(Typeface.DEFAULT);
            canvas.drawText(dayStr, x, y, p);
        } else {
            float y = mDayViewResources.getOneDayHeaderHeight() - mDayViewResources.getDayHeaderOneDayBottomMargin();
            p.setTextAlign(Align.LEFT);

            // Draw day of the week
            x = computeDayLeftPosition(day) + mDayViewResources.getDayHeaderOneDayLeftMargin();
            p.setTextSize(mDayViewResources.getDayHeaderFontSize());
            p.setTypeface(Typeface.DEFAULT);
            canvas.drawText(dayStr, x, y, p);

            // Draw day of the month
            x += p.measureText(dayStr) + mDayViewResources.getDayHeaderOneDayRightMargin();
            p.setTextSize(mDayViewResources.getDateHeaderFontSize());
            p.setTypeface(todayIndex == day ? mBold : Typeface.DEFAULT);
            canvas.drawText(dateNumStr, x, y, p);
        }
    }

    private void drawGridBackground(Rect r, Canvas canvas, Paint p) {
        Paint.Style savedStyle = p.getStyle();

        final float startY = 0;
        final float stopY = mDayViewResources.getHourGap() + 24 * (mCellHeight + mDayViewResources.getHourGap());
        
        int[] dayStops = computeDayLeftEdge();
        
        mDayViewRenderer.drawGridLines(canvas, p, dayStops, startY, stopY, mCellHeight);
        
        // Restore the saved style.
        p.setStyle(savedStyle);
        p.setAntiAlias(true);
    }


    /**
     * @param r
     * @param canvas
     * @param p
     */
    private void drawBgColors(Rect r, Canvas canvas, Paint p) {
        int todayIndex = mTodayJulianDay - mFirstJulianDay;
        // Draw the hours background color
        r.top = mDestRect.top;
        r.bottom = mDestRect.bottom;
        r.left = 0;
        r.right = mHoursWidth;
        p.setColor(mDayViewResources.getBgColor());
        p.setStyle(Style.FILL);
        p.setAntiAlias(false);
        canvas.drawRect(r, p);

        // Draw background for grid area
        if (mNumDays == 1 && todayIndex == 0) {
            // Draw a white background for the time later than current time
            int lineY = mCurrentTime.hour * (mCellHeight + mDayViewResources.getHourGap())
                    + ((mCurrentTime.minute * mCellHeight) / 60) + 1;
            if (lineY < mViewStartY + mViewHeight) {
                lineY = Math.max(lineY, mViewStartY);
                r.left = mHoursWidth;
                r.right = mViewWidth;
                r.top = lineY;
                r.bottom = mViewStartY + mViewHeight;
                p.setColor(mFutureBgColor);
                canvas.drawRect(r, p);
            }
        } else if (todayIndex >= 0 && todayIndex < mNumDays) {
            // Draw today with a white background for the time later than
            // current time
            int lineY = mCurrentTime.hour * (mCellHeight + mDayViewResources.getHourGap())
                    + ((mCurrentTime.minute * mCellHeight) / 60) + 1;
            if (lineY < mViewStartY + mViewHeight) {
                lineY = Math.max(lineY, mViewStartY);
                r.left = computeDayLeftPosition(todayIndex) + 1;
                r.right = computeDayLeftPosition(todayIndex + 1);
                r.top = lineY;
                r.bottom = mViewStartY + mViewHeight;
                p.setColor(mFutureBgColor);
                canvas.drawRect(r, p);
            }

            // Paint Tomorrow and later days with future color
            if (todayIndex + 1 < mNumDays) {
                r.left = computeDayLeftPosition(todayIndex + 1) + 1;
                r.right = computeDayLeftPosition(mNumDays);
                r.top = mDestRect.top;
                r.bottom = mDestRect.bottom;
                p.setColor(mFutureBgColor);
                canvas.drawRect(r, p);
            }
        } else if (todayIndex < 0) {
            // Future
            r.left = computeDayLeftPosition(0) + 1;
            r.right = computeDayLeftPosition(mNumDays);
            r.top = mDestRect.top;
            r.bottom = mDestRect.bottom;
            p.setColor(mFutureBgColor);
            canvas.drawRect(r, p);
        }
        p.setAntiAlias(true);
    }

    /**
     * 
     * @return The currently selected event, or null if there is none.
     */
    public Event getSelectedEvent() {
        if(mSelectedEvent == null){
            return null;
        } else {
            return mSelectedEvent.getEvent();    
        }
    }
    
    
    
    
    public EventLayout getSelectedEventLayout() {
        return mSelectedEvent;
    }
    

    public boolean isEventSelected() {
        return (mSelectedEvent != null);
    }

    private int computeMaxStringWidth(int currentMax, String[] strings, Paint p) {
        float maxWidthF = 0.0f;

        int len = strings.length;
        for (int i = 0; i < len; i++) {
            float width = p.measureText(strings[i]);
            maxWidthF = Math.max(width, maxWidthF);
        }
        int maxWidth = (int) (maxWidthF + 0.5);
        if (maxWidth < currentMax) {
            maxWidth = currentMax;
        }
        return maxWidth;
    }

    private void saveSelectionPosition(float left, float top, float right, float bottom) {
        mPrevBox.left = (int) left;
        mPrevBox.right = (int) right;
        mPrevBox.top = (int) top;
        mPrevBox.bottom = (int) bottom;
    }

    private Rect getCurrentSelectionPosition() {
        Rect box = new Rect();
        box.top = mSelectionHour * (mCellHeight + mDayViewResources.getHourGap());
        box.bottom = box.top + mCellHeight + mDayViewResources.getHourGap();
        int daynum = mSelectionDay - mFirstJulianDay;
        box.left = computeDayLeftPosition(daynum) + 1;
        box.right = computeDayLeftPosition(daynum + 1);
        return box;
    }







    private void drawAllDayEvents(int firstDay, int numDays, Canvas canvas, Paint p) {


        final float startY = mDayViewResources.getDayHeaderHeight(mNumDays);
        final float stopY = startY + mAlldayHeight + mDayViewResources.getAlldayTopMargin();

        mDayViewRenderer.drawAllDayGridLines(canvas, p, computeDayLeftEdge(), startY, stopY);
        
        p.setStyle(Style.FILL);

        
        int y = mDayViewResources.getDayHeaderHeight(mNumDays) + mDayViewResources.getAlldayTopMargin();
        int lastDay = firstDay + numDays - 1;
        final ArrayList<EventLayout> events = mAllDayEvents;
        int numEvents = events.size();
        // Whether or not we should draw the more events text
        boolean hasMoreEvents = false;
        // size of the allDay area
        float drawHeight = mAlldayHeight;
        // max number of events being drawn in one day of the allday area
        float numRectangles = mMaxAlldayEvents;
        // Where to cut off drawn allday events
        int allDayEventClip = mDayViewResources.getDayHeaderHeight(mNumDays) + mAlldayHeight
                + mDayViewResources.getAlldayTopMargin();
        // The number of events that weren't drawn in each day
        mSkippedAlldayEvents = new int[numDays];
        if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount && !mShowAllAllDayEvents &&
                mAnimateDayHeight == 0) {
            // We draw one fewer event than will fit so that more events text
            // can be drawn
            numRectangles = mMaxUnexpandedAlldayEventCount - 1;
            // We also clip the events above the more events text
            allDayEventClip -= mDayViewResources.getMinUnexpandedAllDayEventHeight();
            hasMoreEvents = true;
        } else if (mAnimateDayHeight != 0) {
            // clip at the end of the animating space
            allDayEventClip = mDayViewResources.getDayHeaderHeight(mNumDays) + mAnimateDayHeight
                    + mDayViewResources.getAlldayTopMargin();
        }
        Paint eventTextPaint = mEventTextPaint;
        int alpha = eventTextPaint.getAlpha();
        eventTextPaint.setAlpha(mEventsAlpha);
        for (int i = 0; i < numEvents; i++) {
            EventLayout event = events.get(i);
            int startDay = event.getEvent().getStartDay();
            int endDay = event.getEvent().getEndDay();
            if (startDay > lastDay || endDay < firstDay) {
                continue;
            }
            if (startDay < firstDay) {
                startDay = firstDay;
            }
            if (endDay > lastDay) {
                endDay = lastDay;
            }
            int startIndex = startDay - firstDay;
            int endIndex = endDay - firstDay;
            float height = mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount ? mAnimateDayEventHeight
                    :
                    drawHeight / numRectangles;

            // Prevent a single event from getting too big
            if (height > mDayViewResources.getMaxHeightOfOneAlldayEvent()) {
                height = mDayViewResources.getMaxHeightOfOneAlldayEvent();
            }

            // Leave a one-pixel space between the vertical day lines and the
            // event rectangle.
            event.setLeft(computeDayLeftPosition(startIndex));
            event.setRight(computeDayLeftPosition(endIndex + 1) - DAY_GAP);
            event.setTop(y + height * event.getColumn());
            event.setBottom(event.getTop() + height - mDayViewResources.getAllDayEventRectBottomMargin());
            if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
                // check if we should skip this event. We skip if it starts
                // after the clip bound or ends after the skip bound and we're
                // not animating.
                if (event.getTop() >= allDayEventClip) {
                    incrementSkipCount(mSkippedAlldayEvents, startIndex, endIndex);
                    continue;
                } else if (event.getBottom() > allDayEventClip) {
                    if (hasMoreEvents) {
                        incrementSkipCount(mSkippedAlldayEvents, startIndex, endIndex);
                        continue;
                    }
                    event.setBottom(allDayEventClip);
                }
            }
            mEventRenderer.drawEvent(event, canvas, p, eventTextPaint, (int) event.getTop(),
                    (int) event.getBottom(), event == mClickedEvent && mClickedEvent != null, mSelectionMode == SELECTION_PRESSED || mSelectionMode == SELECTION_SELECTED);


            // Check if this all-day event intersects the selected day
            if (mSelectionAllday && mComputeSelectedEvents) {
                if (startDay <= mSelectionDay && endDay >= mSelectionDay) {
                    mSelectedEvents.add(event);
                }
            }
        }
        eventTextPaint.setAlpha(alpha);

        if (mMoreAlldayEventsTextAlpha != 0 && mSkippedAlldayEvents != null) {
            // If the more allday text should be visible, draw it.
            alpha = p.getAlpha();
            p.setAlpha(mEventsAlpha);
            p.setColor(mMoreAlldayEventsTextAlpha << 24 & mDayViewResources.getMoreEventsTextColor());
            for (int i = 0; i < mSkippedAlldayEvents.length; i++) {
                if (mSkippedAlldayEvents[i] > 0) {
                    drawMoreAlldayEvents(canvas, mSkippedAlldayEvents[i], i, p);
                }
            }
            p.setAlpha(alpha);
        }

        if (mSelectionAllday) {
            // Compute the neighbors for the list of all-day events that
            // intersect the selected day.
            computeAllDayNeighbors();

            // Set the selection position to zero so that when we move down
            // to the normal event area, we will highlight the topmost event.
            saveSelectionPosition(0f, 0f, 0f, 0f);
        }
    }

    // Helper method for counting the number of allday events skipped on each
    // day
    private void incrementSkipCount(int[] counts, int startIndex, int endIndex) {
        if (counts == null || startIndex < 0 || endIndex > counts.length) {
            return;
        }
        for (int i = startIndex; i <= endIndex; i++) {
            counts[i]++;
        }
    }

    // Draws the "box +n" text for hidden allday events
    protected void drawMoreAlldayEvents(Canvas canvas, int remainingEvents, int day, Paint p) {
        int x = computeDayLeftPosition(day) + mDayViewResources.getEventAllDayTextLeftMargin();
        int y = (int) (mAlldayHeight - .5f * mDayViewResources.getMinUnexpandedAllDayEventHeight()
                - .5f
                * mDayViewResources.getEventSquareWidth() + mDayViewResources.getDayHeaderHeight(mNumDays) + mDayViewResources.getAlldayTopMargin());
        Rect r = mRect;
        r.top = y;
        r.left = x;
        r.bottom = y + mDayViewResources.getEventSquareWidth();
        r.right = x + mDayViewResources.getEventSquareWidth();
        p.setColor(mDayViewResources.getMoreEventsTextColor());
        p.setStrokeWidth(mDayViewResources.getEventRectStrokeWidth());
        p.setStyle(Style.STROKE);
        p.setAntiAlias(false);
        canvas.drawRect(r, p);
        p.setAntiAlias(true);
        p.setStyle(Style.FILL);
        p.setTextSize(mDayViewResources.getEventTextFontSize(mNumDays));
        
        
        
        y += mDayViewResources.getEventSquareWidth();
        x += mDayViewResources.getEventSquareWidth() + mDayViewResources.getEventLinePadding();
        canvas.drawText(String.format(mDayViewResources.getMoreEventsMonthText(remainingEvents), remainingEvents), x, y, p);
    }

    private void computeAllDayNeighbors() {
        int len = mSelectedEvents.size();
        if (len == 0 || mSelectedEvent != null) {
            return;
        }

        // First, clear all the links
        for (int ii = 0; ii < len; ii++) {
            EventLayout ev = mSelectedEvents.get(ii);
            ev.setNextUp(null);
            ev.setNextDown(null);
            ev.setNextLeft(null);
            ev.setNextRight(null);
        }

        // For each event in the selected event list "mSelectedEvents", find
        // its neighbors in the up and down directions. This could be done
        // more efficiently by sorting on the Event.getColumn() field, but
        // the list is expected to be very small.

        // Find the event in the same row as the previously selected all-day
        // event, if any.
        int startPosition = -1;
        if (mPrevSelectedEvent != null && mPrevSelectedEvent.getEvent().drawAsAllday()) {
            startPosition = mPrevSelectedEvent.getColumn();
        }
        int maxPosition = -1;
        EventLayout startEvent = null;
        EventLayout maxPositionEvent = null;
        for (int ii = 0; ii < len; ii++) {
            EventLayout ev = mSelectedEvents.get(ii);
            int position = ev.getColumn();
            if (position == startPosition) {
                startEvent = ev;
            } else if (position > maxPosition) {
                maxPositionEvent = ev;
                maxPosition = position;
            }
            for (int jj = 0; jj < len; jj++) {
                if (jj == ii) {
                    continue;
                }
                EventLayout neighbor = mSelectedEvents.get(jj);
                int neighborPosition = neighbor.getColumn();
                if (neighborPosition == position - 1) {
                    ev.setNextUp(neighbor);
                } else if (neighborPosition == position + 1) {
                    ev.setNextDown(neighbor);
                }
            }
        }
        if (startEvent != null) {
            setSelectedEvent(startEvent);
        } else {
            setSelectedEvent(maxPositionEvent);
        }
    }

    private void drawEvents(int date, int dayIndex, int top, Canvas canvas, Paint p) {
        Paint eventTextPaint = mEventTextPaint;
        int left = computeDayLeftPosition(dayIndex) + 1;
        int cellWidth = computeDayLeftPosition(dayIndex + 1) - left + 1;
        int cellHeight = mCellHeight;

        // Use the selected hour as the selection region
        Rect selectionArea = mSelectionRect;
        selectionArea.top = top + mSelectionHour * (cellHeight + mDayViewResources.getHourGap());
        selectionArea.bottom = selectionArea.top + cellHeight;
        selectionArea.left = left;
        selectionArea.right = selectionArea.left + cellWidth;

        final ArrayList<EventLayout> events = mEvents;
        int numEvents = events.size();
        EventGeometry geometry = mEventGeometry;

        final int viewEndY = mViewStartY + mViewHeight
                - mDayViewResources.getDayHeaderHeight(mNumDays) - mAlldayHeight;

        int alpha = eventTextPaint.getAlpha();
        eventTextPaint.setAlpha(mEventsAlpha);
        for (int i = 0; i < numEvents; i++) {
            EventLayout event = events.get(i);
            if (!geometry.computeEventRect(date, left, top, cellWidth, event)) {
                continue;
            }

            // Don't draw it if it is not visible
            if (event.getBottom() < mViewStartY || event.getTop() > viewEndY) {
                continue;
            }

            if (date == mSelectionDay && !mSelectionAllday && mComputeSelectedEvents
                    && geometry.eventIntersectsSelection(event, selectionArea)) {
                mSelectedEvents.add(event);
            }

            
            if(mSelectionMode == SELECTION_PRESSED || mSelectionMode == SELECTION_SELECTED){
                mPrevSelectedEvent = event;
            }
            
            mEventRenderer.drawEvent(event, canvas, p, eventTextPaint, mViewStartY, viewEndY, event == mClickedEvent && mClickedEvent != null, mSelectionMode == SELECTION_PRESSED || mSelectionMode == SELECTION_SELECTED);
        }
        eventTextPaint.setAlpha(alpha);

        if (date == mSelectionDay && !mSelectionAllday && isFocused()
                && mSelectionMode != SELECTION_HIDDEN) {
            computeNeighbors();
        }
    }

    // Computes the "nearest" neighbor event in four directions (left, right,
    // up, down) for each of the events in the mSelectedEvents array.
    private void computeNeighbors() {
        int len = mSelectedEvents.size();
        if (len == 0 || mSelectedEvent != null) {
            return;
        }

        // First, clear all the links
        for (int ii = 0; ii < len; ii++) {
            EventLayout ev = mSelectedEvents.get(ii);
            ev.setNextUp(null);
            ev.setNextDown(null);
            ev.setNextLeft(null);
            ev.setNextRight(null);
        }

        EventLayout startEvent = mSelectedEvents.get(0);
        int startEventDistance1 = 100000; // any large number
        int startEventDistance2 = 100000; // any large number
        int prevLocation = FROM_NONE;
        int prevTop;
        int prevBottom;
        int prevLeft;
        int prevRight;
        int prevCenter = 0;
        Rect box = getCurrentSelectionPosition();
        if (mPrevSelectedEvent != null) {
            prevTop = (int) mPrevSelectedEvent.getTop();
            prevBottom = (int) mPrevSelectedEvent.getBottom();
            prevLeft = (int) mPrevSelectedEvent.getLeft();
            prevRight = (int) mPrevSelectedEvent.getRight();
            // Check if the previously selected event intersects the previous
            // selection box. (The previously selected event may be from a
            // much older selection box.)
            if (prevTop >= mPrevBox.bottom || prevBottom <= mPrevBox.top
                    || prevRight <= mPrevBox.left || prevLeft >= mPrevBox.right) {
                mPrevSelectedEvent = null;
                prevTop = mPrevBox.top;
                prevBottom = mPrevBox.bottom;
                prevLeft = mPrevBox.left;
                prevRight = mPrevBox.right;
            } else {
                // Clip the top and bottom to the previous selection box.
                if (prevTop < mPrevBox.top) {
                    prevTop = mPrevBox.top;
                }
                if (prevBottom > mPrevBox.bottom) {
                    prevBottom = mPrevBox.bottom;
                }
            }
        } else {
            // Just use the previously drawn selection box
            prevTop = mPrevBox.top;
            prevBottom = mPrevBox.bottom;
            prevLeft = mPrevBox.left;
            prevRight = mPrevBox.right;
        }

        // Figure out where we came from and compute the center of that area.
        if (prevLeft >= box.right) {
            // The previously selected event was to the right of us.
            prevLocation = FROM_RIGHT;
            prevCenter = (prevTop + prevBottom) / 2;
        } else if (prevRight <= box.left) {
            // The previously selected event was to the left of us.
            prevLocation = FROM_LEFT;
            prevCenter = (prevTop + prevBottom) / 2;
        } else if (prevBottom <= box.top) {
            // The previously selected event was above us.
            prevLocation = FROM_ABOVE;
            prevCenter = (prevLeft + prevRight) / 2;
        } else if (prevTop >= box.bottom) {
            // The previously selected event was below us.
            prevLocation = FROM_BELOW;
            prevCenter = (prevLeft + prevRight) / 2;
        }

        // For each event in the selected event list "mSelectedEvents", search
        // all the other events in that list for the nearest neighbor in 4
        // directions.
        for (int ii = 0; ii < len; ii++) {
            EventLayout ev = mSelectedEvents.get(ii);

            int startTime = ev.getEvent().getStartTime();
            int endTime = ev.getEvent().getEndTime();
            int left = (int) ev.getLeft();
            int right = (int) ev.getRight();
            int top = (int) ev.getTop();
            if (top < box.top) {
                top = box.top;
            }
            int bottom = (int) ev.getBottom();
            if (bottom > box.bottom) {
                bottom = box.bottom;
            }
            // if (false) {
            // int flags = DateUtils.FORMAT_SHOW_TIME |
            // DateUtils.FORMAT_ABBREV_ALL
            // | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
            // if (DateFormat.is24HourFormat(mContext)) {
            // flags |= DateUtils.FORMAT_24HOUR;
            // }
            // String timeRange = DateUtils.formatDateRange(mContext,
            // ev.startMillis,
            // ev.endMillis, flags);
            // Log.i("Cal", "left: " + left + " right: " + right + " top: " +
            // top + " bottom: "
            // + bottom + " ev: " + timeRange + " " + ev.title);
            // }
            int upDistanceMin = 10000; // any large number
            int downDistanceMin = 10000; // any large number
            int leftDistanceMin = 10000; // any large number
            int rightDistanceMin = 10000; // any large number
            EventLayout upEvent = null;
            EventLayout downEvent = null;
            EventLayout leftEvent = null;
            EventLayout rightEvent = null;

            // Pick the starting event closest to the previously selected event,
            // if any. distance1 takes precedence over distance2.
            int distance1 = 0;
            int distance2 = 0;
            if (prevLocation == FROM_ABOVE) {
                if (left >= prevCenter) {
                    distance1 = left - prevCenter;
                } else if (right <= prevCenter) {
                    distance1 = prevCenter - right;
                }
                distance2 = top - prevBottom;
            } else if (prevLocation == FROM_BELOW) {
                if (left >= prevCenter) {
                    distance1 = left - prevCenter;
                } else if (right <= prevCenter) {
                    distance1 = prevCenter - right;
                }
                distance2 = prevTop - bottom;
            } else if (prevLocation == FROM_LEFT) {
                if (bottom <= prevCenter) {
                    distance1 = prevCenter - bottom;
                } else if (top >= prevCenter) {
                    distance1 = top - prevCenter;
                }
                distance2 = left - prevRight;
            } else if (prevLocation == FROM_RIGHT) {
                if (bottom <= prevCenter) {
                    distance1 = prevCenter - bottom;
                } else if (top >= prevCenter) {
                    distance1 = top - prevCenter;
                }
                distance2 = prevLeft - right;
            }
            if (distance1 < startEventDistance1
                    || (distance1 == startEventDistance1 && distance2 < startEventDistance2)) {
                startEvent = ev;
                startEventDistance1 = distance1;
                startEventDistance2 = distance2;
            }

            // For each neighbor, figure out if it is above or below or left
            // or right of me and compute the distance.
            for (int jj = 0; jj < len; jj++) {
                if (jj == ii) {
                    continue;
                }
                EventLayout neighbor = mSelectedEvents.get(jj);
                int neighborLeft = (int) neighbor.getLeft();
                int neighborRight = (int) neighbor.getRight();
                if (neighbor.getEvent().getEndTime() <= startTime) {
                    // This neighbor is entirely above me.
                    // If we overlap the same column, then compute the distance.
                    if (neighborLeft < right && neighborRight > left) {
                        int distance = startTime - neighbor.getEvent().getEndTime();
                        if (distance < upDistanceMin) {
                            upDistanceMin = distance;
                            upEvent = neighbor;
                        } else if (distance == upDistanceMin) {
                            int center = (left + right) / 2;
                            int currentDistance = 0;
                            int currentLeft = (int) upEvent.getLeft();
                            int currentRight = (int) upEvent.getRight();
                            if (currentRight <= center) {
                                currentDistance = center - currentRight;
                            } else if (currentLeft >= center) {
                                currentDistance = currentLeft - center;
                            }

                            int neighborDistance = 0;
                            if (neighborRight <= center) {
                                neighborDistance = center - neighborRight;
                            } else if (neighborLeft >= center) {
                                neighborDistance = neighborLeft - center;
                            }
                            if (neighborDistance < currentDistance) {
                                upDistanceMin = distance;
                                upEvent = neighbor;
                            }
                        }
                    }
                } else if (neighbor.getEvent().getStartTime() >= endTime) {
                    // This neighbor is entirely below me.
                    // If we overlap the same column, then compute the distance.
                    if (neighborLeft < right && neighborRight > left) {
                        int distance = neighbor.getEvent().getStartTime() - endTime;
                        if (distance < downDistanceMin) {
                            downDistanceMin = distance;
                            downEvent = neighbor;
                        } else if (distance == downDistanceMin) {
                            int center = (left + right) / 2;
                            int currentDistance = 0;
                            int currentLeft = (int) downEvent.getLeft();
                            int currentRight = (int) downEvent.getRight();
                            if (currentRight <= center) {
                                currentDistance = center - currentRight;
                            } else if (currentLeft >= center) {
                                currentDistance = currentLeft - center;
                            }

                            int neighborDistance = 0;
                            if (neighborRight <= center) {
                                neighborDistance = center - neighborRight;
                            } else if (neighborLeft >= center) {
                                neighborDistance = neighborLeft - center;
                            }
                            if (neighborDistance < currentDistance) {
                                downDistanceMin = distance;
                                downEvent = neighbor;
                            }
                        }
                    }
                }

                if (neighborLeft >= right) {
                    // This neighbor is entirely to the right of me.
                    // Take the closest neighbor in the y direction.
                    int center = (top + bottom) / 2;
                    int distance = 0;
                    int neighborBottom = (int) neighbor.getBottom();
                    int neighborTop = (int) neighbor.getTop();
                    if (neighborBottom <= center) {
                        distance = center - neighborBottom;
                    } else if (neighborTop >= center) {
                        distance = neighborTop - center;
                    }
                    if (distance < rightDistanceMin) {
                        rightDistanceMin = distance;
                        rightEvent = neighbor;
                    } else if (distance == rightDistanceMin) {
                        // Pick the closest in the x direction
                        int neighborDistance = neighborLeft - right;
                        int currentDistance = (int) rightEvent.getLeft() - right;
                        if (neighborDistance < currentDistance) {
                            rightDistanceMin = distance;
                            rightEvent = neighbor;
                        }
                    }
                } else if (neighborRight <= left) {
                    // This neighbor is entirely to the left of me.
                    // Take the closest neighbor in the y direction.
                    int center = (top + bottom) / 2;
                    int distance = 0;
                    int neighborBottom = (int) neighbor.getBottom();
                    int neighborTop = (int) neighbor.getTop();
                    if (neighborBottom <= center) {
                        distance = center - neighborBottom;
                    } else if (neighborTop >= center) {
                        distance = neighborTop - center;
                    }
                    if (distance < leftDistanceMin) {
                        leftDistanceMin = distance;
                        leftEvent = neighbor;
                    } else if (distance == leftDistanceMin) {
                        // Pick the closest in the x direction
                        int neighborDistance = left - neighborRight;
                        int currentDistance = left - (int) leftEvent.getRight();
                        if (neighborDistance < currentDistance) {
                            leftDistanceMin = distance;
                            leftEvent = neighbor;
                        }
                    }
                }
            }
            ev.setNextUp(upEvent);
            ev.setNextDown(downEvent);
            ev.setNextLeft(leftEvent);
            ev.setNextRight(rightEvent);
        }
        setSelectedEvent(startEvent);
    }



    // This is to replace p.setStyle(Style.STROKE); canvas.drawRect() since it
    // doesn't work well with hardware acceleration
    // private void drawEmptyRect(Canvas canvas, Rect r, int color) {
    // int linesIndex = 0;
    // mLines[linesIndex++] = r.left;
    // mLines[linesIndex++] = r.top;
    // mLines[linesIndex++] = r.right;
    // mLines[linesIndex++] = r.top;
    //
    // mLines[linesIndex++] = r.left;
    // mLines[linesIndex++] = r.bottom;
    // mLines[linesIndex++] = r.right;
    // mLines[linesIndex++] = r.bottom;
    //
    // mLines[linesIndex++] = r.left;
    // mLines[linesIndex++] = r.top;
    // mLines[linesIndex++] = r.left;
    // mLines[linesIndex++] = r.bottom;
    //
    // mLines[linesIndex++] = r.right;
    // mLines[linesIndex++] = r.top;
    // mLines[linesIndex++] = r.right;
    // mLines[linesIndex++] = r.bottom;
    // mPaint.setColor(color);
    // canvas.drawLines(mLines, 0, linesIndex, mPaint);
    // }

    private void updateEventDetails() {
        if (mSelectedEvent == null || mSelectionMode == SELECTION_HIDDEN
                || mSelectionMode == SELECTION_LONGPRESS) {
            mPopup.dismiss();
            return;
        }
        if (mLastPopupEventID == mSelectedEvent.getEvent().getId()) {
            return;
        }

        mLastPopupEventID = mSelectedEvent.getEvent().getId();

        // Remove any outstanding callbacks to dismiss the popup.
        mHandler.removeCallbacks(mDismissPopup);

        Event event = mSelectedEvent.getEvent();
        TextView titleView = (TextView) mPopupView.findViewById(mDayViewResources.getEventPopupTitleTextFieldID());
        titleView.setText(event.getTitle());

        ImageView imageView = (ImageView) mPopupView.findViewById(mDayViewResources.getEventPopupReminderIconID());
        imageView.setVisibility(event.isHasAlarm() ? View.VISIBLE : View.GONE);

        imageView = (ImageView) mPopupView.findViewById(mDayViewResources.getEventPopupRepeatIconID());
        imageView.setVisibility(event.isRepeating() ? View.VISIBLE : View.GONE);

        int flags;
        if (event.isAllDay()) {
            flags = DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL
                    | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
        }
        if (DateFormat.is24HourFormat(mContext)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        String timeRange = mDependencyFactory.buildTimezoneUtils().formatDateRange(mContext,
                event.getStartMillis(), event.getEndMillis(),
                flags);
        TextView timeView = (TextView) mPopupView.findViewById(mDayViewResources.getEventPopupTimeTextFieldID());
        timeView.setText(timeRange);

        TextView whereView = (TextView) mPopupView.findViewById(mDayViewResources.getEventPopupEventWhereTextFieldID());
        final boolean empty = TextUtils.isEmpty(event.getLocation());
        whereView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty)
            whereView.setText(event.getLocation());

        mPopup.showAtLocation(this, Gravity.BOTTOM | Gravity.LEFT, mHoursWidth, 5);
        mHandler.postDelayed(mDismissPopup, POPUP_DISMISS_DELAY);
    }

    // The following routines are called from the parent activity when certain
    // touch events occur.
    private void doDown(MotionEvent ev) {
        mScrollController.reset();
        mViewStartX = 0;
        mOnFlingCalled = false;
        mHandler.removeCallbacks(mContinueScroll);
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        // Save selection information: we use setSelectionFromPosition to find
        // the selected event
        // in order to show the "clicked" color. But since it is also setting
        // the selected info
        // for new events, we need to restore the old info after calling the
        // function.
        EventLayout oldSelectedEvent = mSelectedEvent;
        int oldSelectionDay = mSelectionDay;
        int oldSelectionHour = mSelectionHour;
        if (setSelectionFromPosition(x, y, false)) {
            // If a time was selected (a blue selection box is visible) and the
            // click location
            // is in the selected time, do not show a click on an event to
            // prevent a situation
            // of both a selection and an event are clicked when they overlap.
            boolean pressedSelected = (mSelectionMode != SELECTION_HIDDEN)
                    && oldSelectionDay == mSelectionDay && oldSelectionHour == mSelectionHour;
            if (!pressedSelected && mSelectedEvent != null) {
                mSavedClickedEvent = mSelectedEvent;
                mDownTouchTime = System.currentTimeMillis();
                postDelayed(mSetClick, mOnDownDelay);
            } else {
                eventClickCleanup();
            }
        }
        mSelectedEvent = oldSelectedEvent;
        mSelectionDay = oldSelectionDay;
        mSelectionHour = oldSelectionHour;
        invalidate();
    }

    // Kicks off all the animations when the expand allday area is tapped
    private void doExpandAllDayClick() {
        mShowAllAllDayEvents = !mShowAllAllDayEvents;

        ObjectAnimator.setFrameDelay(0);

        // Determine the starting height
        if (mAnimateDayHeight == 0) {
            mAnimateDayHeight = mShowAllAllDayEvents ?
                    mAlldayHeight - (int) mDayViewResources.getMinUnexpandedAllDayEventHeight()
                    : mAlldayHeight;
        }
        // Cancel current animations
        mCancellingAnimations = true;
        if (mAlldayAnimator != null) {
            mAlldayAnimator.cancel();
        }
        if (mAlldayEventAnimator != null) {
            mAlldayEventAnimator.cancel();
        }
        if (mMoreAlldayEventsAnimator != null) {
            mMoreAlldayEventsAnimator.cancel();
        }
        mCancellingAnimations = false;
        // get new animators
        mAlldayAnimator = getAllDayAnimator();
        mAlldayEventAnimator = getAllDayEventAnimator();
        mMoreAlldayEventsAnimator = ObjectAnimator.ofInt(this,
                "moreAllDayEventsTextAlpha",
                mShowAllAllDayEvents ? MORE_EVENTS_MAX_ALPHA : 0,
                mShowAllAllDayEvents ? 0 : MORE_EVENTS_MAX_ALPHA);

        // Set up delays and start the animators
        mAlldayAnimator.setStartDelay(mShowAllAllDayEvents ? ANIMATION_SECONDARY_DURATION : 0);
        mAlldayAnimator.start();
        mMoreAlldayEventsAnimator.setStartDelay(mShowAllAllDayEvents ? 0 : ANIMATION_DURATION);
        mMoreAlldayEventsAnimator.setDuration(ANIMATION_SECONDARY_DURATION);
        mMoreAlldayEventsAnimator.start();
        if (mAlldayEventAnimator != null) {
            // This is the only animator that can return null, so check it
            mAlldayEventAnimator
                    .setStartDelay(mShowAllAllDayEvents ? ANIMATION_SECONDARY_DURATION : 0);
            mAlldayEventAnimator.start();
        }
    }

    /**
     * Figures out the initial heights for allDay events and space when a view
     * is being set up.
     */
    public void initAllDayHeights() {
        if (mMaxAlldayEvents <= mMaxUnexpandedAlldayEventCount) {
            return;
        }
        if (mShowAllAllDayEvents) {
            int maxADHeight = mViewHeight - mDayViewResources.getDayHeaderHeight(mNumDays)
                    - MIN_HOURS_HEIGHT;
            maxADHeight = Math
                    .min(maxADHeight,
                            (int) (mMaxAlldayEvents * mDayViewResources
                                    .getMinUnexpandedAllDayEventHeight()));
            mAnimateDayEventHeight = maxADHeight / mMaxAlldayEvents;
        } else {
            mAnimateDayEventHeight = (int) mDayViewResources.getMinUnexpandedAllDayEventHeight();
        }
    }

    // Sets up an animator for changing the height of allday events
    private ObjectAnimator getAllDayEventAnimator() {
        // First calculate the absolute max height
        int maxADHeight = mViewHeight - mDayViewResources.getDayHeaderHeight(mNumDays)
                - MIN_HOURS_HEIGHT;
        // Now expand to fit but not beyond the absolute max
        maxADHeight =
                Math.min(maxADHeight, (int) (mMaxAlldayEvents * mDayViewResources
                        .getMinUnexpandedAllDayEventHeight()));
        // calculate the height of individual events in order to fit
        int fitHeight = maxADHeight / mMaxAlldayEvents;
        int currentHeight = mAnimateDayEventHeight;
        int desiredHeight =
                mShowAllAllDayEvents ? fitHeight : (int) mDayViewResources
                        .getMinUnexpandedAllDayEventHeight();
        // if there's nothing to animate just return
        if (currentHeight == desiredHeight) {
            return null;
        }

        // Set up the animator with the calculated values
        ObjectAnimator animator = ObjectAnimator.ofInt(this, "animateDayEventHeight",
                currentHeight, desiredHeight);
        animator.setDuration(ANIMATION_DURATION);
        return animator;
    }

    // Sets up an animator for changing the height of the allday area
    private ObjectAnimator getAllDayAnimator() {
        // Calculate the absolute max height
        int maxADHeight = mViewHeight - mDayViewResources.getDayHeaderHeight(mNumDays)
                - MIN_HOURS_HEIGHT;
        // Find the desired height but don't exceed abs max
        maxADHeight =
                Math.min(maxADHeight, (int) (mMaxAlldayEvents * mDayViewResources
                        .getMinUnexpandedAllDayEventHeight()));
        // calculate the current and desired heights
        int currentHeight = mAnimateDayHeight != 0 ? mAnimateDayHeight : mAlldayHeight;
        int desiredHeight = mShowAllAllDayEvents ? maxADHeight :
                (int) (mMaxUnexpandedAllDayHeight
                        - mDayViewResources.getMinUnexpandedAllDayEventHeight() - 1);

        // Set up the animator with the calculated values
        ObjectAnimator animator = ObjectAnimator.ofInt(this, "animateDayHeight",
                currentHeight, desiredHeight);
        animator.setDuration(ANIMATION_DURATION);

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mCancellingAnimations) {
                    // when finished, set this to 0 to signify not animating
                    mAnimateDayHeight = 0;
                    mUseExpandIcon = !mShowAllAllDayEvents;
                }
                mRemeasure = true;
                invalidate();
            }
        });
        return animator;
    }

    // setter for the 'box +n' alpha text used by the animator
    public void setMoreAllDayEventsTextAlpha(int alpha) {
        mMoreAlldayEventsTextAlpha = alpha;
        invalidate();
    }

    // setter for the height of the allday area used by the animator
    public void setAnimateDayHeight(int height) {
        mAnimateDayHeight = height;
        mRemeasure = true;
        invalidate();
    }

    // setter for the height of allday events used by the animator
    public void setAnimateDayEventHeight(int height) {
        mAnimateDayEventHeight = height;
        mRemeasure = true;
        invalidate();
    }

    private void doSingleTapUp(MotionEvent ev) {
        if (!mHandleActionUp || mScrolling) {
            return;
        }

        int x = (int) ev.getX();
        int y = (int) ev.getY();
        int selectedDay = mSelectionDay;
        int selectedHour = mSelectionHour;

        if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
            // check if the tap was in the allday expansion area
            int bottom = mFirstCell;
            if ((x < mHoursWidth && y > mDayViewResources.getDayHeaderHeight(mNumDays) && y < mDayViewResources
                    .getDayHeaderHeight(mNumDays) + mAlldayHeight)
                    || (!mShowAllAllDayEvents && mAnimateDayHeight == 0 && y < bottom &&
                    y >= bottom - mDayViewResources.getMinUnexpandedAllDayEventHeight())) {
                doExpandAllDayClick();
                return;
            }
        }

        boolean validPosition = setSelectionFromPosition(x, y, false);
        if (!validPosition) {
            if (y < mDayViewResources.getDayHeaderHeight(mNumDays)) {
                Time selectedTime = new Time(mBaseDate);
                selectedTime.setJulianDay(mSelectionDay);
                selectedTime.hour = mSelectionHour;
                selectedTime.normalize(true /* ignore isDst */);
                
                mEventBus.post(new ShowDateInDayViewEvent(selectedTime));
            }
            return;
        }

        boolean hasSelection = mSelectionMode != SELECTION_HIDDEN;
        boolean pressedSelected = (hasSelection || mTouchExplorationEnabled)
                && selectedDay == mSelectionDay && selectedHour == mSelectionHour;

        if (pressedSelected && mSavedClickedEvent == null) {
            // If the tap is on an already selected hour slot, then create a new
            // event
            mSelectionMode = SELECTION_SELECTED;
            
            
            //TODO: 
            //Previously was supplying the touch event's coordinates. Is this necessary? Doesn't seem to impact the
            //event creation dialog, which fills the whole screen.
            //            mController.sendEventRelatedEventWithExtra(this, EventType.CREATE_EVENT, -1,
            //            getSelectedTimeInMillis(), 0, (int) ev.getRawX(), (int) ev.getRawY(),
            //            extraLong, -1);

            mEventBus.post(new CreateEventEvent(getSelectedTimeInMillis(), mSelectionAllday));
        } else if (mSelectedEvent != null) {
            // If the tap is on an event, launch the "View event" view
            if (mIsAccessibilityEnabled) {
                mAccessibilityMgr.interrupt();
            }

            mSelectionMode = SELECTION_HIDDEN;

            long clearDelay = (CLICK_DISPLAY_DURATION + mOnDownDelay) -
                    (System.currentTimeMillis() - mDownTouchTime);
            if (clearDelay > 0) {
                this.postDelayed(mClearClick, clearDelay);
            } else {
                this.post(mClearClick);
            }
        } else {
            // Select time
            Time startTime = new Time(mBaseDate);
            startTime.setJulianDay(mSelectionDay);
            startTime.hour = mSelectionHour;
            startTime.normalize(true /* ignore isDst */);

            Time endTime = new Time(startTime);
            endTime.hour++;

            mSelectionMode = SELECTION_SELECTED;
            mEventBus.post(new ShowDateInCurrentViewEvent(startTime, endTime));
        }
        invalidate();
    }

    private void doLongPress(MotionEvent ev) {
        eventClickCleanup();
        if (mScrolling) {
            return;
        }

        // Scale gesture in progress
        if (mStartingSpanY != 0) {
            return;
        }

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        boolean validPosition = setSelectionFromPosition(x, y, false);
        if (!validPosition) {
            // return if the touch wasn't on an area of concern
            return;
        }

        mSelectionMode = SELECTION_LONGPRESS;
        invalidate();
        performLongClick();
    }

    private void doScroll(MotionEvent e1, MotionEvent e2, float deltaX, float deltaY) {
        cancelAnimation();
        
        final float focusY = getAverageY(e2);
        final float focusX = getAverageX(e2);

        
        mScrollController.scrolled(focusX, focusY, deltaX, deltaY);

        
        if (mRecalCenterHour) {
            // Calculate the hour that correspond to the average of the Y touch
            // points
            mGestureCenterHour = (mViewStartY + focusY
                    - mDayViewResources.getDayHeaderHeight(mNumDays) - mAlldayHeight)
                    / (mCellHeight + DAY_GAP);
            mRecalCenterHour = false;
        }

        // If we haven't figured out the predominant scroll direction yet,
        // then do it now.
        int distanceX = (int)mScrollController.getCumulativeScrollX();
        int distanceY = (int)mScrollController.getCumulativeScrollY();
        if (!mScrollController.isHorizontalScrolling() && !mScrollController.isVerticalScrolling()) {
            int absDistanceX = Math.abs(distanceX);
            int absDistanceY = Math.abs(distanceY);
            

            if (absDistanceX > absDistanceY) {
                int slopFactor = mScaleGestureDetector.isInProgress() ? 20 : 2;
                if (absDistanceX > mScaledPagingTouchSlop * slopFactor) {
                    mScrollController.startedHorizontalScrolling(HorizontalScrollDirection.resolveForDistance(distanceX));
                }
            } else {
                mScrollController.startedVerticalScrolling();
            }
        } else if (mScrollController.isHorizontalScrolling()) {
            // We are already scrolling horizontally, so check if we
            // changed the direction of scrolling so that the other week
            // is now visible.
            
            if (distanceX != 0) {
                HorizontalScrollDirection direction = HorizontalScrollDirection.resolveForDistance(distanceX);
                //can't be null, we know distanceX != 0
                
                
                if (direction != mScrollController.getHorizontalScrollDirection()) {
                    mScrollController.startedHorizontalScrolling(direction);
                }
            }
        }


        mScrolling = true;

        mSelectionMode = SELECTION_HIDDEN;
        invalidate();
    }

    private float getAverageY(MotionEvent me) {
        int count = me.getPointerCount();
        float focusY = 0;
        for (int i = 0; i < count; i++) {
            focusY += me.getY(i);
        }
        focusY /= count;
        return focusY;
    }
    
    private float getAverageX(MotionEvent me) {
        int count = me.getPointerCount();
        float focusY = 0;
        for (int i = 0; i < count; i++) {
            focusY += me.getX(i);
        }
        focusY /= count;
        return focusY;
    }

    private void cancelAnimation() {
        Animation in = mViewSwitcher.getInAnimation();
        if (in != null) {
            // cancel() doesn't terminate cleanly.
            in.scaleCurrentDuration(0);
        }
        Animation out = mViewSwitcher.getOutAnimation();
        if (out != null) {
            // cancel() doesn't terminate cleanly.
            out.scaleCurrentDuration(0);
        }
    }

    private void doFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        cancelAnimation();

        mSelectionMode = SELECTION_HIDDEN;
        eventClickCleanup();

        mOnFlingCalled = true;

        if (mScrollController.isHorizontalScrolling()) {
            // Horizontal fling.
            // initNextView(deltaX);
            mScrollController.reset();
            if (DEBUG)
                Log.d(TAG, "doFling: velocityX " + velocityX);
            int deltaX = (int) e2.getX() - (int) e1.getX();
            switchViews(deltaX < 0, mViewStartX, mViewWidth, velocityX);
            mViewStartX = 0;
            return;
        }

        if (mScrollController.isVerticalScrolling()) {
            // Vertical fling.
            mScrollController.reset();
            mViewStartX = 0;

            if (DEBUG) {
                Log.d(TAG, "doFling: mViewStartY" + mViewStartY + " velocityY " + velocityY);
            }

            // Continue scrolling vertically
            mScrolling = true;
            mScroller.fling(0 /* startX */, mViewStartY /* startY */, 0 /* velocityX */,
                    (int) -velocityY, 0 /* minX */, 0 /* maxX */, 0 /* minY */,
                    mMaxViewStartY /* maxY */, OVERFLING_DISTANCE, OVERFLING_DISTANCE);

            // When flinging down, show a glow when it hits the end only if it
            // wasn't started at the top
            if (velocityY > 0 && mViewStartY != 0) {
                mCallEdgeEffectOnAbsorb = true;
            }
            // When flinging up, show a glow when it hits the end only if it wasn't
            // started at the bottom
            else if (velocityY < 0 && mViewStartY != mMaxViewStartY) {
                mCallEdgeEffectOnAbsorb = true;
            }
            mHandler.post(mContinueScroll);

        } else {
            if (DEBUG)
                Log.d(TAG, "doFling: no fling");
        }

    }

    private boolean initNextView(int deltaX) {
        // Change the view to the previous day or week
        DayView view = (DayView) mViewSwitcher.getNextView();
        Time date = view.mBaseDate;
        date.set(mBaseDate);
        boolean switchForward;
        if (deltaX > 0) {
            date.monthDay -= mNumDays;
            view.setSelectedDay(mSelectionDay - mNumDays);
            switchForward = false;
        } else {
            date.monthDay += mNumDays;
            view.setSelectedDay(mSelectionDay + mNumDays);
            switchForward = true;
        }
        date.normalize(true /* ignore isDst */);
        initView(view);
        view.layout(getLeft(), getTop(), getRight(), getBottom());
        view.reloadEvents();
        return switchForward;
    }

    // ScaleGestureDetector.OnScaleGestureListener
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mHandleActionUp = false;
        float gestureCenterInPixels = detector.getFocusY()
                - mDayViewResources.getDayHeaderHeight(mNumDays) - mAlldayHeight;
        mGestureCenterHour = (mViewStartY + gestureCenterInPixels) / (mCellHeight + DAY_GAP);

        mStartingSpanY = Math.max(mDayViewResources.getMinYSpan(), Math.abs(detector.getCurrentSpanY()));
        mCellHeightBeforeScaleGesture = mCellHeight;

        if (DEBUG_SCALING) {
            float ViewStartHour = mViewStartY / (float) (mCellHeight + DAY_GAP);
            Log.d(TAG, "onScaleBegin: mGestureCenterHour:" + mGestureCenterHour
                    + "\tViewStartHour: " + ViewStartHour + "\tmViewStartY:" + mViewStartY
                    + "\tmCellHeight:" + mCellHeight + " SpanY:" + detector.getCurrentSpanY());
        }

        return true;
    }

    // ScaleGestureDetector.OnScaleGestureListener
    public boolean onScale(ScaleGestureDetector detector) {
        float spanY = Math.max(mDayViewResources.getMinYSpan(), Math.abs(detector.getCurrentSpanY()));

        mCellHeight = (int) (mCellHeightBeforeScaleGesture * spanY / mStartingSpanY);

        if (mCellHeight < mMinCellHeight) {
            // If mStartingSpanY is too small, even a small increase in the
            // gesture can bump the mCellHeight beyond MAX_CELL_HEIGHT
            mStartingSpanY = spanY;
            mCellHeight = mMinCellHeight;
            mCellHeightBeforeScaleGesture = mMinCellHeight;
        } else if (mCellHeight > mDayViewResources.getMaxCellHeight()) {
            mStartingSpanY = spanY;
            mCellHeight = mDayViewResources.getMaxCellHeight();
            mCellHeightBeforeScaleGesture = mDayViewResources.getMaxCellHeight();
        }

        int gestureCenterInPixels = (int) detector.getFocusY()
                - mDayViewResources.getDayHeaderHeight(mNumDays) - mAlldayHeight;
        mViewStartY = (int) (mGestureCenterHour * (mCellHeight + DAY_GAP)) - gestureCenterInPixels;
        mMaxViewStartY = mDayViewResources.getHourGap() + 24 * (mCellHeight + mDayViewResources.getHourGap()) - mGridAreaHeight;

        if (DEBUG_SCALING) {
            float ViewStartHour = mViewStartY / (float) (mCellHeight + DAY_GAP);
            Log.d(TAG, "onScale: mGestureCenterHour:" + mGestureCenterHour + "\tViewStartHour: "
                    + ViewStartHour + "\tmViewStartY:" + mViewStartY + "\tmCellHeight:"
                    + mCellHeight + " SpanY:" + detector.getCurrentSpanY());
        }

        if (mViewStartY < 0) {
            mViewStartY = 0;
            mGestureCenterHour = (mViewStartY + gestureCenterInPixels)
                    / (float) (mCellHeight + DAY_GAP);
        } else if (mViewStartY > mMaxViewStartY) {
            mViewStartY = mMaxViewStartY;
            mGestureCenterHour = (mViewStartY + gestureCenterInPixels)
                    / (float) (mCellHeight + DAY_GAP);
        }
        computeFirstHour();

        mRemeasure = true;
        invalidate();
        return true;
    }

    // ScaleGestureDetector.OnScaleGestureListener
    public void onScaleEnd(ScaleGestureDetector detector) {
        mScrollStartY = mViewStartY;
        mStartingSpanY = 0;
        mScrollController.reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (DEBUG)
            Log.e(TAG, "" + action + " ev.getPointerCount() = " + ev.getPointerCount());

        if ((ev.getActionMasked() == MotionEvent.ACTION_DOWN) ||
                (ev.getActionMasked() == MotionEvent.ACTION_UP) ||
                (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) ||
                (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
            mRecalCenterHour = true;
        }

        if (!mScrollController.isHorizontalScrolling()) {
            mScaleGestureDetector.onTouchEvent(ev);
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mScrollController.reset();
                if (DEBUG) {
                    Log.e(TAG, "ACTION_DOWN ev.getDownTime = " + ev.getDownTime() + " Cnt="
                            + ev.getPointerCount());
                }

                int bottom = mAlldayHeight + mDayViewResources.getDayHeaderHeight(mNumDays)
                        + mDayViewResources.getAlldayTopMargin();
                if (ev.getY() < bottom) {
                    mTouchStartedInAlldayArea = true;
                } else {
                    mTouchStartedInAlldayArea = false;
                }
                mHandleActionUp = true;
                mGestureDetector.onTouchEvent(ev);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (DEBUG)
                    Log.e(TAG, "ACTION_MOVE Cnt=" + ev.getPointerCount() + DayView.this);
                mGestureDetector.onTouchEvent(ev);
                return true;

            case MotionEvent.ACTION_UP:
                if (DEBUG)
                    Log.e(TAG, "ACTION_UP Cnt=" + ev.getPointerCount() + mHandleActionUp);
                mEdgeEffectTop.onRelease();
                mEdgeEffectBottom.onRelease();
                mGestureDetector.onTouchEvent(ev);
                if (!mHandleActionUp) {
                    mHandleActionUp = true;
                    mViewStartX = 0;
                    invalidate();
                    return true;
                }

                if (mOnFlingCalled) {
                    return true;
                }

                // If we were scrolling, then reset the selected hour so that it
                // is visible.
                if (mScrolling) {
                    mScrolling = false;
                    resetSelectedHour();
                    invalidate();
                }

                if (mScrollController.isHorizontalScrolling()) {
                    mScrollController.reset();
                    if (Math.abs(mViewStartX) > mHorizontalSnapBackThreshold) {
                        // The user has gone beyond the threshold so switch
                        // views
                        if (DEBUG)
                            Log.d(TAG, "- horizontal scroll: switch views");
                        switchViews(mViewStartX > 0, mViewStartX, mViewWidth, 0);
                        mViewStartX = 0;
                        return true;
                    } else {
                        // Not beyond the threshold so invalidate which will
                        // cause
                        // the view to snap back. Also call recalc() to ensure
                        // that we have the correct starting date and title.
                        if (DEBUG)
                            Log.d(TAG, "- horizontal scroll: snap back");
                        recalc();
                        invalidate();
                        mViewStartX = 0;
                    }
                }

                return true;

                // This case isn't expected to happen.
            case MotionEvent.ACTION_CANCEL:
                if (DEBUG)
                    Log.e(TAG, "ACTION_CANCEL");
                mGestureDetector.onTouchEvent(ev);
                mScrolling = false;
                resetSelectedHour();
                return true;

            default:
                if (DEBUG)
                    Log.e(TAG, "Not MotionEvent " + ev.toString());
                if (mGestureDetector.onTouchEvent(ev)) {
                    return true;
                }
                return super.onTouchEvent(ev);
        }
    }




    /**
     * Sets mSelectionDay and mSelectionHour based on the (x,y) touch position.
     * If the touch position is not within the displayed grid, then this method
     * returns false.
     * 
     * @param x the x position of the touch
     * @param y the y position of the touch
     * @param keepOldSelection - do not change the selection info (used for
     *            invoking accessibility messages)
     * @return true if the touch position is valid
     */
    private boolean setSelectionFromPosition(int x, final int y, boolean keepOldSelection) {

        EventLayout savedEvent = null;
        int savedDay = 0;
        int savedHour = 0;
        boolean savedAllDay = false;
        if (keepOldSelection) {
            // Store selection info and restore it at the end. This way, we can
            // invoke the
            // right accessibility message without affecting the selection.
            savedEvent = mSelectedEvent;
            savedDay = mSelectionDay;
            savedHour = mSelectionHour;
            savedAllDay = mSelectionAllday;
        }
        if (x < mHoursWidth) {
            x = mHoursWidth;
        }

        int day = (x - mHoursWidth) / (mCellWidth + DAY_GAP);
        if (day >= mNumDays) {
            day = mNumDays - 1;
        }
        day += mFirstJulianDay;
        setSelectedDay(day);

        if (y < mDayViewResources.getDayHeaderHeight(mNumDays)) {
            sendAccessibilityEventAsNeeded(false);
            return false;
        }

        setSelectedHour(mFirstHour); /* First fully visible hour */

        if (y < mFirstCell) {
            mSelectionAllday = true;
        } else {
            // y is now offset from top of the scrollable region
            int adjustedY = y - mFirstCell;

            if (adjustedY < mFirstHourOffset) {
                setSelectedHour(mSelectionHour - 1); /*
                                                      * In the partially visible
                                                      * hour
                                                      */
            } else {
                setSelectedHour(mSelectionHour +
                        (adjustedY - mFirstHourOffset) / (mCellHeight + mDayViewResources.getHourGap()));
            }

            mSelectionAllday = false;
        }

        findSelectedEvent(x, y);

        // Log.i("Cal", "setSelectionFromPosition( " + x + ", " + y + " ) day: "
        // + day + " hour: "
        // + mSelectionHour + " mFirstCell: " + mFirstCell +
        // " mFirstHourOffset: "
        // + mFirstHourOffset);
        // if (mSelectedEvent != null) {
        // Log.i("Cal", "  num events: " + mSelectedEvents.size() + " event: "
        // + mSelectedEvent.title);
        // for (Event ev : mSelectedEvents) {
        // int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL
        // | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
        // String timeRange = formatDateRange(mContext, ev.startMillis,
        // ev.endMillis, flags);
        //
        // Log.i("Cal", "  " + timeRange + " " + ev.title);
        // }
        // }
        sendAccessibilityEventAsNeeded(true);

        // Restore old values
        if (keepOldSelection) {
            mSelectedEvent = savedEvent;
            mSelectionDay = savedDay;
            mSelectionHour = savedHour;
            mSelectionAllday = savedAllDay;
        }
        return true;
    }

    private void findSelectedEvent(int x, int y) {
        int date = mSelectionDay;
        int cellWidth = mCellWidth;
        ArrayList<EventLayout> events = mEvents;
        int numEvents = events.size();
        int left = computeDayLeftPosition(mSelectionDay - mFirstJulianDay);
        int top = 0;
        setSelectedEvent(null);

        mSelectedEvents.clear();
        if (mSelectionAllday) {
            float yDistance;
            float minYdistance = 10000.0f; // any large number
            EventLayout closestEvent = null;
            float drawHeight = mAlldayHeight;
            int yOffset = mDayViewResources.getDayHeaderHeight(mNumDays) + mDayViewResources.getAlldayTopMargin();
            int maxUnexpandedColumn = mMaxUnexpandedAlldayEventCount;
            if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
                // Leave a gap for the 'box +n' text
                maxUnexpandedColumn--;
            }
            events = mAllDayEvents;
            numEvents = events.size();
            for (int i = 0; i < numEvents; i++) {
                EventLayout event = events.get(i);
                if (!event.getEvent().drawAsAllday() ||
                        (!mShowAllAllDayEvents && event.getColumn() >= maxUnexpandedColumn)) {
                    // Don't check non-allday events or events that aren't shown
                    continue;
                }

                if (event.getEvent().getStartDay() <= mSelectionDay && event.getEvent().getEndDay() >= mSelectionDay) {
                    float numRectangles = mShowAllAllDayEvents ? mMaxAlldayEvents
                            : mMaxUnexpandedAlldayEventCount;
                    float height = drawHeight / numRectangles;
                    if (height > mDayViewResources.getMaxHeightOfOneAlldayEvent()) {
                        height = mDayViewResources.getMaxHeightOfOneAlldayEvent();
                    }
                    float eventTop = yOffset + height * event.getColumn();
                    float eventBottom = eventTop + height;
                    if (eventTop < y && eventBottom > y) {
                        // If the touch is inside the event rectangle, then
                        // add the event.
                        mSelectedEvents.add(event);
                        closestEvent = event;
                        break;
                    } else {
                        // Find the closest event
                        if (eventTop >= y) {
                            yDistance = eventTop - y;
                        } else {
                            yDistance = y - eventBottom;
                        }
                        if (yDistance < minYdistance) {
                            minYdistance = yDistance;
                            closestEvent = event;
                        }
                    }
                }
            }
            setSelectedEvent(closestEvent);
            return;
        }

        // Adjust y for the scrollable bitmap
        y += mViewStartY - mFirstCell;

        // Use a region around (x,y) for the selection region
        Rect region = mRect;
        region.left = x - 10;
        region.right = x + 10;
        region.top = y - 10;
        region.bottom = y + 10;

        EventGeometry geometry = mEventGeometry;

        for (int i = 0; i < numEvents; i++) {
            EventLayout event = events.get(i);
            // Compute the event rectangle.
            if (!geometry.computeEventRect(date, left, top, cellWidth, event)) {
                continue;
            }

            // If the event intersects the selection region, then add it to
            // mSelectedEvents.
            if (geometry.eventIntersectsSelection(event, region)) {
                mSelectedEvents.add(event);
            }
        }

        // If there are any events in the selected region, then assign the
        // closest one to mSelectedEvent.
        if (mSelectedEvents.size() > 0) {
            int len = mSelectedEvents.size();
            EventLayout closestEvent = null;
            float minDist = mViewWidth + mViewHeight; // some large distance
            for (int index = 0; index < len; index++) {
                EventLayout ev = mSelectedEvents.get(index);
                float dist = geometry.pointToEvent(x, y, ev);
                if (dist < minDist) {
                    minDist = dist;
                    closestEvent = ev;
                }
            }
            setSelectedEvent(closestEvent);

            // Keep the selected hour and day consistent with the selected
            // event. They could be different if we touched on an empty hour
            // slot very close to an event in the previous hour slot. In
            // that case we will select the nearby event.
            int startDay = mSelectedEvent.getEvent().getStartDay();
            int endDay = mSelectedEvent.getEvent().getEndDay();
            if (mSelectionDay < startDay) {
                setSelectedDay(startDay);
            } else if (mSelectionDay > endDay) {
                setSelectedDay(endDay);
            }

            int startHour = mSelectedEvent.getEvent().getStartTime() / 60;
            int endHour;
            if (mSelectedEvent.getEvent().getStartTime() < mSelectedEvent.getEvent().getEndTime()) {
                endHour = (mSelectedEvent.getEvent().getEndTime() - 1) / 60;
            } else {
                endHour = mSelectedEvent.getEvent().getEndTime() / 60;
            }

            if (mSelectionHour < startHour && mSelectionDay == startDay) {
                setSelectedHour(startHour);
            } else if (mSelectionHour > endHour && mSelectionDay == endDay) {
                setSelectedHour(endHour);
            }
        }
    }

    // Encapsulates the code to continue the scrolling after the
    // finger is lifted. Instead of stopping the scroll immediately,
    // the scroll continues to "free spin" and gradually slows down.
    private class ContinueScroll implements Runnable {
        public void run() {
            mScrolling = mScrolling && mScroller.computeScrollOffset();
            if (!mScrolling || mPaused) {
                resetSelectedHour();
                invalidate();
                return;
            }

            mViewStartY = mScroller.getCurrY();

            if (mCallEdgeEffectOnAbsorb) {
                if (mViewStartY < 0) {
                    mEdgeEffectTop.onAbsorb((int) mLastVelocity);
                    mCallEdgeEffectOnAbsorb = false;
                } else if (mViewStartY > mMaxViewStartY) {
                    mEdgeEffectBottom.onAbsorb((int) mLastVelocity);
                    mCallEdgeEffectOnAbsorb = false;
                }
                mLastVelocity = mScroller.getCurrVelocity();
            }

            if (mScrollStartY == 0 || mScrollStartY == mMaxViewStartY) {
                // Allow overscroll/springback only on a fling,
                // not a pull/fling from the end
                if (mViewStartY < 0) {
                    mViewStartY = 0;
                } else if (mViewStartY > mMaxViewStartY) {
                    mViewStartY = mMaxViewStartY;
                }
            }

            computeFirstHour();
            mHandler.post(this);
            invalidate();
        }
    }

    /**
     * Cleanup the pop-up and timers.
     */
    public void cleanup() {
        // Protect against null-pointer exceptions
        if (mPopup != null) {
            mPopup.dismiss();
        }
        mPaused = true;
        mLastPopupEventID = INVALID_EVENT_ID;
        if (mHandler != null) {
            mHandler.removeCallbacks(mDismissPopup);
            mHandler.removeCallbacks(mUpdateCurrentTime);
        }

        mDependencyFactory.buildPreferencesUtils().setSharedPreference(mContext, KEY_DEFAULT_CELL_HEIGHT,
                mCellHeight);
        // Clear all click animations
        eventClickCleanup();
        // Turn off redraw
        mRemeasure = false;
        // Turn off scrolling to make sure the view is in the correct state if
        // we fling back to it
        mScrolling = false;
    }

    private void eventClickCleanup() {
        this.removeCallbacks(mClearClick);
        this.removeCallbacks(mSetClick);
        mClickedEvent = null;
        mSavedClickedEvent = null;
    }

    void setSelectedEvent(EventLayout e) {
        mSelectedEvent = e;
        mSelectedEventForAccessibility = e;
    }

    int getSelectedHour() {
        return mSelectionHour;
    }
    
    void setSelectedHour(int h) {
        mSelectionHour = h;
        mSelectionHourForAccessibility = h;
    }

    private void setSelectedDay(int d) {
        mSelectionDay = d;
        mSelectionDayForAccessibility = d;
    }

    /**
     * Restart the update timer
     */
    public void restartCurrentTimeUpdates() {
        mPaused = false;
        if (mHandler != null) {
            mHandler.removeCallbacks(mUpdateCurrentTime);
            mHandler.post(mUpdateCurrentTime);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cleanup();
        super.onDetachedFromWindow();
    }

    class DismissPopup implements Runnable {
        public void run() {
            // Protect against null-pointer exceptions
            if (mPopup != null) {
                mPopup.dismiss();
            }
        }
    }
    
    int getSelectionMode() {
        return mSelectionMode;
    }
    
    void setSelectionMode(int selectionMode) {
        mSelectionMode = selectionMode;
    }

    class UpdateCurrentTime implements Runnable {
        public void run() {
            long currentTime = System.currentTimeMillis();
            mCurrentTime.set(currentTime);
            // % causes update to occur on 5 minute marks (11:10, 11:15, 11:20,
            // etc.)
            if (!DayView.this.mPaused) {
                mHandler.postDelayed(mUpdateCurrentTime, UPDATE_CURRENT_TIME_DELAY
                        - (currentTime % UPDATE_CURRENT_TIME_DELAY));
            }
            mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);
            invalidate();
        }
    }

    class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            if (DEBUG)
                Log.e(TAG, "GestureDetector.onSingleTapUp");
            DayView.this.doSingleTapUp(ev);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent ev) {
            if (DEBUG)
                Log.e(TAG, "GestureDetector.onLongPress");
            DayView.this.doLongPress(ev);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (DEBUG)
                Log.e(TAG, "GestureDetector.onScroll");
            eventClickCleanup();
            if (mTouchStartedInAlldayArea) {
                if (Math.abs(distanceX) < Math.abs(distanceY)) {
                    // Make sure that click feedback is gone when you scroll
                    // from the
                    // all day area
                    invalidate();
                    return false;
                }
                // don't scroll vertically if this started in the allday area
                distanceY = 0;
            }
            DayView.this.doScroll(e1, e2, distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (DEBUG)
                Log.e(TAG, "GestureDetector.onFling");

            if (mTouchStartedInAlldayArea) {
                if (Math.abs(velocityX) < Math.abs(velocityY)) {
                    return false;
                }
                // don't fling vertically if this started in the allday area
                velocityY = 0;
            }
            DayView.this.doFling(e1, e2, velocityX, velocityY);
            return true;
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            if (DEBUG)
                Log.e(TAG, "GestureDetector.onDown");
            DayView.this.doDown(ev);
            return true;
        }
    }

    // The rest of this file was borrowed from Launcher2 - PagedView.java
    private static final int MINIMUM_SNAP_VELOCITY = 2200;

    private class ScrollInterpolator implements Interpolator {
        public ScrollInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            t = t * t * t * t * t + 1;

            if ((1 - t) * mAnimationDistance < 1) {
                cancelAnimation();
            }

            return t;
        }
    }

    private long calculateDuration(float delta, float width, float velocity) {
        /*
         * Here we compute a "distance" that will be used in the computation of
         * the overall snap duration. This is a function of the actual distance
         * that needs to be traveled; we keep this value close to half screen
         * size in order to reduce the variance in snap duration as a function
         * of the distance the page needs to travel.
         */
        final float halfScreenSize = width / 2;
        float distanceRatio = delta / width;
        float distanceInfluenceForSnapDuration = distanceInfluenceForSnapDuration(distanceRatio);
        float distance = halfScreenSize + halfScreenSize * distanceInfluenceForSnapDuration;

        velocity = Math.abs(velocity);
        velocity = Math.max(MINIMUM_SNAP_VELOCITY, velocity);

        /*
         * we want the page's snap velocity to approximately match the velocity
         * at which the user flings, so we scale the duration by a value near to
         * the derivative of the scroll interpolator at zero, ie. 5. We use 6 to
         * make it a little slower.
         */
        long duration = 6 * Math.round(1000 * Math.abs(distance / velocity));
        if (DEBUG) {
            Log.e(TAG, "halfScreenSize:" + halfScreenSize + " delta:" + delta + " distanceRatio:"
                    + distanceRatio + " distance:" + distance + " velocity:" + velocity
                    + " duration:" + duration + " distanceInfluenceForSnapDuration:"
                    + distanceInfluenceForSnapDuration);
        }
        return duration;
    }

    /*
     * We want the duration of the page snap animation to be influenced by the
     * distance that the screen has to travel, however, we don't want this
     * duration to be effected in a purely linear fashion. Instead, we use this
     * method to moderate the effect that the distance of travel has on the
     * overall snap duration.
     */
    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    public EventBus getEventBus() {
        return mEventBus;
    }
    
    public void setEventRenderer(EventRenderer r){
    	mEventRenderer = r;
    }
    
    public List<EventLayout> getSelectedEvents() {
        return Collections.unmodifiableList(mSelectedEvents);
    }

    public int getNumDays() {
        return mNumDays;
    }

    public boolean isSelectionAllday() {
        return mSelectionAllday;
    }
    
    public void setSelectionAllday(boolean selectionAllday) {
        mSelectionAllday = selectionAllday;
    }

    public void setScrolling(boolean b) {
        mScrolling = b;
    }
    public boolean isScrolling() {
        return mScrolling;
    }
    
    public int getSelectionDay() {
        return mSelectionDay;
    }
    
    void clearSelectedEvents() {
        mSelectedEvents.clear();
//?        invalidate();
    }

    public int getFirstJulianDay() {
        return mFirstJulianDay;
    }
    
    public int getLastJulianDay() {
        return mLastJulianDay;
    }

    public void increaseSelectedHour(int numHours) {
        setSelectedHour(getSelectedHour() + numHours);
        adjustHourSelection();
        clearSelectedEvents();
        mComputeSelectedEvents = true;
    }

    public void decreaseSelectedHour(int numHours) {
        setSelectedHour(getSelectedHour() - numHours);
        adjustHourSelection();
        clearSelectedEvents();
        mComputeSelectedEvents = true;
        // TODO Auto-generated method stub
        
    }

    
    public class DayViewScrollEventHandler {
        
        @Subscribe
        public void handleHorizontalScrollStarted(HorizontalScrollingStartedEvent event){
            mScrollStartY = mViewStartY;
            initNextView(-event.getDirection().getDirectionConstant());
        }
        
        @Subscribe
        public void handleVerticalScrollStarted(VerticalScrollingStartedEvent event){
            mScrollStartY = mViewStartY;
        }
        
        
        @Subscribe
        public void handleScroll(ScrollEvent e){
            if(mScrollController.isHorizontalScrolling()){
                mViewStartX = (int)e.getCumulativeScrollX();    
            } else if(mScrollController.isVerticalScrolling()){
                // Calculate the top of the visible region in the calendar grid.
                // Increasing/decrease this will scroll the calendar grid up/down.
                mViewStartY = (int) ((mGestureCenterHour * (mCellHeight + DAY_GAP)) - e.getCurrentY() + mDayViewResources.getDayHeaderHeight(mNumDays) + mAlldayHeight);

                // If dragging while already at the end, do a glow
                final int pulledToY = (int) (mScrollStartY + e.getScrollDeltaY());
                if (pulledToY < 0) {
                    mEdgeEffectTop.onPull(e.getScrollDeltaY() / mViewHeight);
                    if (!mEdgeEffectBottom.isFinished()) {
                        mEdgeEffectBottom.onRelease();
                    }
                } else if (pulledToY > mMaxViewStartY) {
                    mEdgeEffectBottom.onPull(e.getScrollDeltaY() / mViewHeight);
                    if (!mEdgeEffectTop.isFinished()) {
                        mEdgeEffectTop.onRelease();
                    }
                }

                if (mViewStartY < 0) {
                    mViewStartY = 0;
                    mRecalCenterHour = true;
                } else if (mViewStartY > mMaxViewStartY) {
                    mViewStartY = mMaxViewStartY;
                    mRecalCenterHour = true;
                }
                if (mRecalCenterHour) {
                    // Calculate the hour that correspond to the average of the Y
                    // touch points
                    mGestureCenterHour = (mViewStartY + e.getCurrentY()
                            - mDayViewResources.getDayHeaderHeight(mNumDays) - mAlldayHeight)
                            / (mCellHeight + DAY_GAP);
                    mRecalCenterHour = false;
                }
                computeFirstHour();
            }
            
        }

    }
    
}
