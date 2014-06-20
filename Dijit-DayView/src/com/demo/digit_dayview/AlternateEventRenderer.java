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

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.code.yadview.DayViewDependencyFactory;
import com.google.code.yadview.DayViewResources;
import com.google.code.yadview.Event;
import com.google.code.yadview.EventLayout;
import com.google.code.yadview.EventRenderer;
import com.google.code.yadview.impl.DefaultDayViewResources;
import com.google.gode.yadview_harness.R;

public class AlternateEventRenderer implements EventRenderer {

    private Context mContext;
    private DayViewResources mDayViewResources;
    private View mTemplateView;
    private TextView mEventTitle;
    private View mEventColourPanel;
    
    private Rect r = null;
    private Paint p2;

    public AlternateEventRenderer(Context ctx, DayViewResources dayViewResources, DayViewDependencyFactory utilFactory) {
        mDayViewResources = dayViewResources;
        mContext = ctx;
        
        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mTemplateView = inflater.inflate(R.layout.event_template, null, false);
        
        
        mEventTitle = (TextView)mTemplateView.findViewById(R.id.eventTitle);
        mEventColourPanel = mTemplateView.findViewById(R.id.eventColourPanel);
    }
    
    @Override
    public void drawEvent(EventLayout event, Canvas canvas, Paint p,
            Paint eventTextPaint, int visibleTop, int visibleBot,
            boolean isSelectedEvent, boolean drawSelectedEvent) {
        
        canvas.save();
        canvas.translate(event.getLeft(), event.getTop());


        

        int width = (int)(event.getRight() - event.getLeft());
        int height = (int)(event.getBottom() - event.getTop());
        mTemplateView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        LinearLayout ll = (LinearLayout)mTemplateView;
        
        ll.layout(0,0,0,0);
        ll.setRight(width);
        ll.setBottom(height);
        
        mEventTitle.setText(event.getEvent().getTitle());
        ((GradientDrawable)mEventColourPanel.getBackground()).setColor(0xa0000000 | event.getEvent().getColor());
//        mEventColourPanel.setBackgroundColor(Color.BLACK | event.getEvent().getColor());

        
        mTemplateView.draw(canvas);
        
        
        if(isSelectedEvent){
            //we have already translated
            r = new Rect();
            r.top = 0;
            r.bottom = height;
            r.left = 0;
            r.right = width;
                        
            p2 = new Paint(p);
            int color = mDayViewResources.getClickedColor();
            p2.setStrokeWidth(mDayViewResources.getEventRectStrokeWidth());
            p2.setColor(color);
            p2.setStyle(Style.FILL);
            p2.setAlpha(75);
            canvas.drawRect(r, p2);
            
            // The left bound is added 73 pixels where
            // the rectangle's width starts
            Toast.makeText(mContext, "width: " + r.width() + " height: " + r.height() + " X: " + -(canvas.getClipBounds().left + 73) + " Y: " + -(canvas.getClipBounds().top), Toast.LENGTH_SHORT).show();
        }
        
        canvas.restore();
    }
    
    public static class AlternateRendererDayViewResources extends DefaultDayViewResources {

        public AlternateRendererDayViewResources(Context ctx) {
            super(ctx);
        }

        @Override
        public float getMinEventHeight() {
            return 50;
        }
        
        @Override
        public int getSingleAlldayHeight() {
            return 50;
        }

        @Override
        public int getMAX_UNEXPANDED_ALLDAY_HEIGHT() {
            return getSingleAlldayHeight() * 2;
        }
        
        @Override
        public float getMinUnexpandedAllDayEventHeight() {
            return getSingleAlldayHeight();
        }
        
        
        @Override
        public int getMaxHeightOfOneAlldayEvent() {
            return getSingleAlldayHeight();
        }
        
        
    }



    @Override
    public void prepareForEvents(ArrayList<Event> events) {
        
    }

}
