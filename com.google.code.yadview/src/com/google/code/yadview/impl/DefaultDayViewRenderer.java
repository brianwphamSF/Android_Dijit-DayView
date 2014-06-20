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

import android.graphics.Canvas;
import android.graphics.Paint;

import com.google.code.yadview.DayViewRenderer;
import com.google.code.yadview.DayViewResources;

public class DefaultDayViewRenderer implements DayViewRenderer{

    protected DayViewResources mDayViewResources;

    
    public DefaultDayViewRenderer(DayViewResources resources) {
        mDayViewResources = resources;
    }

    @Override
    public void drawGridLines(Canvas canvas, Paint p, int dayLeftEdges[], float startY, float stopY, int cellHeight) {
        float[] lines = new float[25 * 4]; 
        final float deltaY = cellHeight + mDayViewResources.getHourGap();
        
        // Draw the inner horizontal grid lines
        p.setColor(mDayViewResources.getCalendarGridLineInnerHorizontalColor());
        p.setStrokeWidth(mDayViewResources.getGridLineWidth());
        p.setAntiAlias(false);
        int y = 0;
        int linesIndex = 0;
        for (int hour = 0; hour <= 24; hour++) {
            lines[linesIndex++] = mDayViewResources.getGridLineLeftMargin();
            lines[linesIndex++] = y;
            lines[linesIndex++] = dayLeftEdges[dayLeftEdges.length - 1];
            lines[linesIndex++] = y;
            y += deltaY;
        }
        if (mDayViewResources.getCalendarGridLineInnerVerticalColor() != mDayViewResources.getCalendarGridLineInnerHorizontalColor()) {
            canvas.drawLines(lines, 0, linesIndex, p);
            linesIndex = 0;
            p.setColor(mDayViewResources.getCalendarGridLineInnerVerticalColor());
        } else {
            canvas.drawLines(lines, 0, linesIndex, p);
        }

        linesIndex = 0;
        
        // Draw the inner vertical grid lines
        for (int day = 0; day < dayLeftEdges.length; day++) {
            int x = dayLeftEdges[day];
            lines[linesIndex++] = x;
            lines[linesIndex++] = startY;
            lines[linesIndex++] = x;
            lines[linesIndex++] = stopY;
        }
        canvas.drawLines(lines, 0, linesIndex, p);
        
    }
    
    @Override
    public void drawAllDayGridLines(Canvas canvas, Paint p, int dayLeftEdges[], float startY, float stopY) {
        float[] lines = new float[(dayLeftEdges.length + 1) * 4]; 

        
        // Draw the inner vertical grid lines
        p.setColor(mDayViewResources.getCalendarGridLineInnerVerticalColor());
        
        p.setTextSize(mDayViewResources.getNormalFontSize());
        p.setTextAlign(Paint.Align.LEFT);
        p.setStrokeWidth(mDayViewResources.getGridLineWidth());
        
        int linesIndex = 0;
        // Line bounding the top of the all day area
        lines[linesIndex++] = mDayViewResources.getGridLineLeftMargin();
        lines[linesIndex++] = startY;
        lines[linesIndex++] = dayLeftEdges[dayLeftEdges.length - 1];
        lines[linesIndex++] = startY;

        for (int day = 0; day < dayLeftEdges.length; day++) {
            int x = dayLeftEdges[day];
            lines[linesIndex++] = x;
            lines[linesIndex++] = startY;
            lines[linesIndex++] = x;
            lines[linesIndex++] = stopY;
        }
        p.setAntiAlias(false);
        canvas.drawLines(lines, 0, linesIndex, p);
    }
    

}
