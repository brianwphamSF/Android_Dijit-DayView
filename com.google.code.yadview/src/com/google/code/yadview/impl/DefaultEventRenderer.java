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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.code.yadview.DayViewDependencyFactory;
import com.google.code.yadview.DayViewResources;
import com.google.code.yadview.Event;
import com.google.code.yadview.EventLayout;
import com.google.code.yadview.EventRenderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.provider.CalendarContract.Attendees;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.Layout.Alignment;
import android.text.style.StyleSpan;

public class DefaultEventRenderer implements EventRenderer {
	
    private static final int MAX_EVENT_TEXT_LEN = 500;
    private static final Pattern drawTextSanitizerFilter = Pattern.compile("[\t\n],");

	
    private DayViewResources mDayViewResources;
	private Rect mRect = new Rect();
	private DayViewDependencyFactory mDependencyFactory;
	private int mEventsAlpha = 255;
	
	private Map<EventLayout, StaticLayout> mLayoutMap = new HashMap<EventLayout, StaticLayout>();
	

	public DefaultEventRenderer(DayViewResources dayViewResources, DayViewDependencyFactory dependencyFactory) {
    	mDayViewResources = dayViewResources;
    	mDependencyFactory = dependencyFactory;
	}

	@Override
    public void drawEvent(EventLayout event, Canvas canvas, Paint p, Paint eventTextPaint, int visibleTop, int visibleBot, boolean isSelectedEvent, boolean drawSelectedEvent) {
        // Draw the Event Rect
        Rect r = mRect ;
        r.top = Math.max((int) event.getTop() + mDayViewResources.getEventRectTopMargin(), visibleTop);
        r.bottom = Math.min((int) event.getBottom() - mDayViewResources.getEventRectBottomMargin(), visibleBot);
        r.left = (int) event.getLeft() + mDayViewResources.getEventRectLeftMargin();
        r.right = (int) event.getRight();

        drawEventRect(event, canvas, p, visibleTop, visibleBot,
				isSelectedEvent, drawSelectedEvent, r);

        // Setup rect for drawEventText which follows
        r.top = (int) event.getTop() + mDayViewResources.getEventRectTopMargin();
        r.bottom = (int) event.getBottom() - mDayViewResources.getEventRectBottomMargin();
        r.left = (int) event.getLeft() + mDayViewResources.getEventRectLeftMargin();
        r.right = (int) event.getRight() - mDayViewResources.getEventRectRightMargin();
        
        
        
        
        setupTextRect(r);

        
        //optimizations - can reimplement later
        // Don't draw text if it is not visible
//        if (r.top > viewEndY || r.bottom < mViewStartY) {
//            continue;
//        }
        
        StaticLayout layout = getEventLayout(event, eventTextPaint, r);
        // TODO: not sure why we are 4 pixels off
        drawEventText(layout, r, canvas, false);
    }

	
    private void drawEventText(StaticLayout eventLayout, Rect rect, Canvas canvas, boolean center) {
        // drawEmptyRect(canvas, rect, 0xFFFF00FF); // for debugging

        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        // If the rectangle is too small for text, then return
        if (eventLayout == null || width < mDayViewResources.getMinCellWidthForText()) {
            return;
        }

        int totalLineHeight = 0;
        int lineCount = eventLayout.getLineCount();
        for (int i = 0; i < lineCount; i++) {
            int lineBottom = eventLayout.getLineBottom(i);
            if (lineBottom <= height) {
                totalLineHeight = lineBottom;
            } else {
                break;
            }
        }

//        Optimizations..
//        if (totalLineHeight == 0 || rect.top > bottom || rect.top + totalLineHeight < top) {
//            return;
//        }

        // Use a StaticLayout to format the string.
        canvas.save();
        // canvas.translate(rect.left, rect.top + (rect.bottom - rect.top / 2));
        int padding = center ? (rect.bottom - rect.top - totalLineHeight) / 2 : 0;
        canvas.translate(rect.left, rect.top + padding);
        rect.left = 0;
        rect.right = width;
        rect.top = 0;
        rect.bottom = totalLineHeight;

        // There's a bug somewhere. If this rect is outside of a previous
        // cliprect, this becomes a no-op. What happens is that the text draw
        // past the event rect. The current fix is to not draw the staticLayout
        // at all if it is completely out of bound.
        canvas.clipRect(rect);
        eventLayout.draw(canvas);
        canvas.restore();
    }
	
	private void drawEventRect(EventLayout event, Canvas canvas, Paint p,
			int visibleTop, int visibleBot, boolean isSelectedEvent,
			boolean drawSelectedEvent, Rect r) {
		int color;
        if (isSelectedEvent) {
            color = mDayViewResources.getClickedColor();
        } else {
            color = event.getEvent().getColor();
        }

        switch (event.getEvent().getSelfAttendeeStatus()) {
            case Attendees.ATTENDEE_STATUS_INVITED:
                if (!isSelectedEvent) {
                    p.setStyle(Style.STROKE);
                }
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                if (!isSelectedEvent) {
                    color = mDependencyFactory.buildRenderingUtils().getDeclinedColorFromColor(color);
                }
            case Attendees.ATTENDEE_STATUS_NONE: // Your own events
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
            case Attendees.ATTENDEE_STATUS_TENTATIVE:
            default:
                p.setStyle(Style.FILL_AND_STROKE);
                break;
        }

        p.setAntiAlias(false);

        int floorHalfStroke = (int) Math.floor(mDayViewResources.getEventRectStrokeWidth() / 2.0f);
        int ceilHalfStroke = (int) Math.ceil(mDayViewResources.getEventRectStrokeWidth() / 2.0f);
        r.top = Math.max((int) event.getTop() + mDayViewResources.getEventRectTopMargin() + floorHalfStroke, visibleTop);
        r.bottom = Math.min((int) event.getBottom() - mDayViewResources.getEventRectBottomMargin() - ceilHalfStroke,
                visibleBot);
        r.left += floorHalfStroke;
        r.right -= ceilHalfStroke;
        p.setStrokeWidth(mDayViewResources.getEventRectStrokeWidth());
        p.setColor(color);
        int alpha = p.getAlpha();
        p.setAlpha(mEventsAlpha);
        canvas.drawRect(r, p);
        p.setAlpha(alpha);
        p.setStyle(Style.FILL);

        // If this event is selected, then use the selection color
        if (isSelectedEvent) {
            if (drawSelectedEvent) {
                color = mDayViewResources.getPressedColor();
                p.setColor(color);
                canvas.drawRect(r, p);
            }
            p.setAntiAlias(true);
        }
	}
	
	
    private void setupTextRect(Rect r) {
        if (r.bottom <= r.top || r.right <= r.left) {
            r.bottom = r.top;
            r.right = r.left;
            return;
        }

        if (r.bottom - r.top > mDayViewResources.getEventTextTopMargin()
                + mDayViewResources.getEventTextBottomMargin()) {
            r.top += mDayViewResources.getEventTextTopMargin();
            r.bottom -= mDayViewResources.getEventTextBottomMargin();
        }
        if (r.right - r.left > mDayViewResources.getEventTextLeftMargin()
                + mDayViewResources.getEventTextRightMargin()) {
            r.left += mDayViewResources.getEventTextLeftMargin();
            r.right -= mDayViewResources.getEventTextRightMargin();
        }
    }
    
    /**
     * Return the layout for a numbered event. Create it if not already existing
     */
    private StaticLayout getEventLayout(EventLayout event, Paint paint, Rect r) {
    	
    	StaticLayout layout = mLayoutMap.get(event);
    	
        // Check if we have already initialized the StaticLayout and that
        // the width hasn't changed (due to vertical resizing which causes
        // re-layout of events at min height)
        if (layout == null || r.width() != layout.getWidth()) {
            SpannableStringBuilder bob = new SpannableStringBuilder();
            if (event.getEvent().getTitle() != null) {
                // MAX - 1 since we add a space
                bob.append(drawTextSanitizer(event.getEvent().getTitle().toString(), MAX_EVENT_TEXT_LEN - 1));
                bob.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, bob.length(), 0);
                bob.append(' ');
            }
            if (event.getEvent().getLocation() != null) {
                bob.append(drawTextSanitizer(event.getEvent().getLocation().toString(),
                        MAX_EVENT_TEXT_LEN - bob.length()));
            }

            switch (event.getEvent().getSelfAttendeeStatus()) {
                case Attendees.ATTENDEE_STATUS_INVITED:
                    paint.setColor(event.getEvent().getColor());
                    break;
                case Attendees.ATTENDEE_STATUS_DECLINED:
                    paint.setColor(mDayViewResources.getEventTextColor());
                    paint.setAlpha(mDependencyFactory.buildRenderingUtils().getDeclinedEventTextAlpha());
                    break;
                case Attendees.ATTENDEE_STATUS_NONE: // Your own events
                case Attendees.ATTENDEE_STATUS_ACCEPTED:
                case Attendees.ATTENDEE_STATUS_TENTATIVE:
                default:
                    paint.setColor(mDayViewResources.getEventTextColor());
                    break;
            }

            // Leave a one pixel boundary on the left and right of the rectangle
            // for the event
            layout = new StaticLayout(bob, 0, bob.length(), new TextPaint(paint), r.width(),
                    Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true, null, r.width());

            mLayoutMap.put(event, layout);
        }
        layout.getPaint().setAlpha(mEventsAlpha);
        return layout;
    }
    


    // Sanitize a string before passing it to drawText or else we get little
    // squares. For newlines and tabs before a comma, delete the character.
    // Otherwise, just replace them with a space.
    private String drawTextSanitizer(String string, int maxEventTextLen) {
        Matcher m = drawTextSanitizerFilter.matcher(string);
        string = m.replaceAll(",");

        int len = string.length();
        if (maxEventTextLen <= 0) {
            string = "";
            len = 0;
        } else if (len > maxEventTextLen) {
            string = string.substring(0, maxEventTextLen);
            len = maxEventTextLen;
        }

        return string.replace('\n', ' ');
    }
    
	public void setEventsAlpha(int alpha) {
		mEventsAlpha = alpha;
		
	}

	@Override
    public void prepareForEvents(ArrayList<Event> events) {
		mLayoutMap.clear();
	}

}
