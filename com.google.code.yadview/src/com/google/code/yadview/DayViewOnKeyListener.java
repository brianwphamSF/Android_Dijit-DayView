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

import com.google.code.yadview.events.DeleteEventEvent;
import com.google.code.yadview.events.ShowDateInCurrentViewEvent;

import android.content.Context;
import android.text.format.Time;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.View.OnKeyListener;

public class DayViewOnKeyListener implements OnKeyListener {

    private DayView mDayView;

    public DayViewOnKeyListener(Context context, DayView dv) {
        mDayView = dv;
    }


    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            return onKeyDown(keyCode, event);
        } else if(event.getAction() == KeyEvent.ACTION_UP){
            return onKeyUp(keyCode, event);
        } else {
            return false;
        }
    }
    
    
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        
        mDayView.setScrolling(false);
        long duration = event.getEventTime() - event.getDownTime();

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mDayView.getSelectionMode() == DayView.SELECTION_HIDDEN) {
                    // Don't do anything unless the selection is visible.
                    break;
                }

                if (mDayView.getSelectionMode() == DayView.SELECTION_PRESSED) {
                    // This was the first press when there was nothing selected.
                    // Change the selection from the "pressed" state to the
                    // the "selected" state. We treat short-press and
                    // long-press the same here because nothing was selected.
                    mDayView.setSelectionMode(DayView.SELECTION_SELECTED);
                    mDayView.invalidate();
                    break;
                }

                // Check the duration to determine if this was a short press
                if (duration < ViewConfiguration.getLongPressTimeout()) {
                    
                    //TODO: This belongs in a shared controller
                    mDayView.switchViews(true /* trackball */);
                } else {
                    mDayView.setSelectionMode(DayView.SELECTION_LONGPRESS);
                    mDayView.invalidate();
                    mDayView.performLongClick();
                }
                break;
        }
        
        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mDayView.getSelectionMode() == DayView.SELECTION_HIDDEN) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Display the selection box but don't move or select it
                // on this key press.
                mDayView.setSelectionMode(DayView.SELECTION_SELECTED);
                mDayView.invalidate();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                // Display the selection box but don't select it
                // on this key press.
                mDayView.setSelectionMode(DayView.SELECTION_PRESSED);
                mDayView.invalidate();
                return true;
            }
        }

        mDayView.setSelectionMode(DayView.SELECTION_SELECTED);
        mDayView.setScrolling(false);
        boolean redraw;
        int selectionDay = mDayView.getSelectionDay();

        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                // Delete the selected event, if any
                Event selectedEvent = mDayView.getSelectedEvent();
                if (selectedEvent == null) {
                    return false;
                }
                
                //TODO: Fix popup handling
                mDayView.mPopup.dismiss();
                mDayView.mLastPopupEventID = DayView.INVALID_EVENT_ID;

                mDayView.getEventBus().post(new DeleteEventEvent(selectedEvent));
                return true;
            case KeyEvent.KEYCODE_ENTER:
                mDayView.switchViews(true /* trackball or keyboard */);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (event.getRepeatCount() == 0) {
                    event.startTracking();
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mDayView.getSelectedEventLayout() != null) {
                    mDayView.setSelectedEvent(mDayView.getSelectedEventLayout().getNextLeft());
                }
                if (mDayView.getSelectedEventLayout() == null) {
                    mDayView.mLastPopupEventID = DayView.INVALID_EVENT_ID;
                    selectionDay -= 1;
                }
                redraw = true;
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mDayView.getSelectedEventLayout() != null) {
                    mDayView.setSelectedEvent(mDayView.getSelectedEventLayout().getNextRight());
                }
                if (mDayView.getSelectedEventLayout() == null) {
                    mDayView.mLastPopupEventID = DayView.INVALID_EVENT_ID;
                    selectionDay += 1;
                }
                redraw = true;
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (mDayView.getSelectedEventLayout() != null) {
                    mDayView.setSelectedEvent(mDayView.getSelectedEventLayout().getNextUp());
                }
                if (mDayView.getSelectedEventLayout() == null) {
                    mDayView.mLastPopupEventID = DayView.INVALID_EVENT_ID;
                    if (!mDayView.isSelectionAllday()) {
                        mDayView.decreaseSelectedHour(1);
                    }
                }
                redraw = true;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mDayView.getSelectedEventLayout() != null) {
                    mDayView.setSelectedEvent(mDayView.getSelectedEventLayout().getNextDown());
                }
                if (mDayView.getSelectedEventLayout() == null) {
                    mDayView.mLastPopupEventID = DayView.INVALID_EVENT_ID;
                    if (mDayView.isSelectionAllday()) {
                        mDayView.setSelectionAllday(false);
                    } else {
                        mDayView.increaseSelectedHour(1);
                    }
                }
                redraw = true;
                break;

            default:
                return false;
        }

        
        if (mDayView.getSelectionDay() != selectionDay) {
            mDayView.switchToDay(selectionDay);
            return true;
        } else if (redraw) {
            //same day, but may have changed
            mDayView.invalidate();
            return true;
        }

        return false;
    }


}
