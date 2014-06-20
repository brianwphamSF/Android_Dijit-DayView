/*
Copyright 2013 Chris Pope


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

package com.demo.digit_dayview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;

import com.google.code.yadview.DayViewRenderer;
import com.google.code.yadview.DayViewResources;
import com.google.code.yadview.impl.DefaultDayViewRenderer;

public class AlternateDayViewRenderer extends DefaultDayViewRenderer implements DayViewRenderer {

    public AlternateDayViewRenderer(DayViewResources resources) {
        super(resources);
    }
    
    @Override
    public void drawGridLines(Canvas canvas, Paint p, int[] dayLeftEdges, float startY,
            float stopY, int cellHeight) {
        

        super.drawGridLines(canvas, p, dayLeftEdges, startY, stopY, cellHeight);
        

        final float deltaY = cellHeight + mDayViewResources.getHourGap();


        for(int i = 0; i < dayLeftEdges.length - 1; i++){
           int x0 = dayLeftEdges[i];
            int x1 = dayLeftEdges[i + 1];
            
            int y = 0;
            for (int hour = 0; hour <= 24; hour++) {
                int y0 = y;
                y += deltaY;
                int y1 = y;
                
                
                if(hour <= 6){
                    //early morning doesn't count.. just cause 
                    //paint as greyed-out
                    Paint p2 = new Paint();
                    p2.setColor(Color.BLACK);
                    p2.setStyle(Style.FILL);
                    p2.setAlpha(25);

                    canvas.drawRect(new Rect(x0, y0, x1, y1), p2);
                }
            }
        }

        if(deltaY > 70){
            //if enough space, draw dashed lines on the half-hour
            int x0 = dayLeftEdges[0];
            int x1 = dayLeftEdges[dayLeftEdges.length - 1];
            int y = 0;
            
            Paint p2 = new Paint();
            p2.setColor(Color.LTGRAY);
            p2.setStyle(Style.STROKE);
            p2.setPathEffect(new DashPathEffect(new float[] {10, 20}, 0));
                    
            
            for (int hour = 0; hour <= 24; hour++) {
                int y0 = y;
                y += deltaY;
                int y1 = y;
                float halfHour = (y0 + y1) / 2f;
                
                Path path = new Path();
                path.moveTo(x0, halfHour);
                path.lineTo(x1, halfHour);
                path.close();
                canvas.drawPath(path, p2);
                    
            }
        }
        
        

        
        
        
        
    }

}
