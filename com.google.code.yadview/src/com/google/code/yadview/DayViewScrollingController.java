package com.google.code.yadview;

import com.google.common.eventbus.EventBus;

public class DayViewScrollingController {

    //touch mode:
    //down -> we know user is touching screen, don't know which way they are scrolling
    //horizonal -> we know they are doing horizontal scroll
    //vertical ->we know they are vertical scrolling
    //initial-state: they are doing nothing
    
    /**
     * The initial state of the touch mode when we enter this view.
     */
    private static final int TOUCH_MODE_INITIAL_STATE = 0;

    /**
     * Indicates the touch gesture is a vertical scroll
     */
    private static final int TOUCH_MODE_VSCROLL = 0x20;

    /**
     * Indicates the touch gesture is a horizontal scroll
     */
    private static final int TOUCH_MODE_HSCROLL = 0x40;

    
    private int mTouchMode = TOUCH_MODE_INITIAL_STATE;
    
    
    private final EventBus mEventBus;

    private HorizontalScrollDirection mHorizontalScrollDirection;

    private float mCumulativeScrollX;

    private float mCumulativeScrollY;
    
    public enum HorizontalScrollDirection {
        
        LEFT_TO_RIGHT(1),
        RIGHT_TO_LEFT(-1);
        
        private int mDirectionConstant;

        private HorizontalScrollDirection(int directionConstant){
            mDirectionConstant = directionConstant;
        }
        
        public int getDirectionConstant() {
            return mDirectionConstant;
        }
        
        public static HorizontalScrollDirection resolveForDistance(int distance){
            if(distance > 0){
                return LEFT_TO_RIGHT;
            } else if (distance < 0){
                return RIGHT_TO_LEFT;
            } else {
                return null;
            }
        }
    }
    

    public DayViewScrollingController(EventBus eventBus) {
        mEventBus = eventBus;
        mHorizontalScrollDirection = null;
    }

    public void reset(){
        mTouchMode = TOUCH_MODE_INITIAL_STATE;
        mHorizontalScrollDirection = null;
        mCumulativeScrollX = 0;
        mCumulativeScrollY = 0;
    }
    
    public boolean isHorizontalScrolling(){
        return (mTouchMode & TOUCH_MODE_HSCROLL) != 0;
    }
    
    public boolean isVerticalScrolling() {
        return (mTouchMode & TOUCH_MODE_VSCROLL) != 0;
    }
    
    public void startedHorizontalScrolling(HorizontalScrollDirection horizontalScrollDirection) {
        mTouchMode = TOUCH_MODE_HSCROLL;
        mHorizontalScrollDirection = horizontalScrollDirection;
        mEventBus.post(new HorizontalScrollingStartedEvent(horizontalScrollDirection));
    }
    
    public HorizontalScrollDirection getHorizontalScrollDirection() {
        return mHorizontalScrollDirection;
    }
    
    public void startedVerticalScrolling() {
        mTouchMode = TOUCH_MODE_VSCROLL;
    }

    
    public void scrolled(float x, float y, float deltaX, float deltaY) {
        mCumulativeScrollX += deltaX;
        mCumulativeScrollY += deltaY;

        mEventBus.post(new ScrollEvent(x, y, deltaX, deltaY, mCumulativeScrollX, mCumulativeScrollY));
    }
    
    public float getCumulativeScrollX() {
        return mCumulativeScrollX;
    }
    
    public float getCumulativeScrollY() {
        return mCumulativeScrollY;
    }


    public static class HorizontalScrollingStartedEvent {
        private final HorizontalScrollDirection mDirection;

        public HorizontalScrollingStartedEvent(HorizontalScrollDirection direction) {
            mDirection = direction;
        }
        
        public HorizontalScrollDirection getDirection() {
            return mDirection;
        }
    }

    public static class ScrollEvent {
        private float mCurrentX;
        private float mCurrentY;
        private float mScrollDeltaX;
        private float mScrollDeltaY;
        private float mCumulativeScrollX;
        private float mCumulativeScrollY;

        public ScrollEvent(float x, float y, float deltaX, float deltaY, float cumulativeScrollX, float cumulativeScrollY) {
            mCurrentX = x;
            mCurrentY = y;
            mScrollDeltaX = deltaX;
            mScrollDeltaY = deltaY;
            mCumulativeScrollX = cumulativeScrollX;
            mCumulativeScrollY = cumulativeScrollY;
        }

        public float getCumulativeScrollX() {
            return mCumulativeScrollX;
        }
        public float getCumulativeScrollY() {
            return mCumulativeScrollY;
        }
        public float getCurrentX() {
            return mCurrentX;
        }
        public float getCurrentY() {
            return mCurrentY;
        }
        public float getScrollDeltaX() {
            return mScrollDeltaX;
        }
        public float getScrollDeltaY() {
            return mScrollDeltaY;
        }
        
        
    }
    
    public static class VerticalScrollingStartedEvent {
        
    }
    
    
}
