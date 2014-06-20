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

import android.app.Activity;
import android.text.format.Time;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ViewSwitcher;

import com.demo.digit_dayview.AlternateEventRenderer.AlternateRendererDayViewResources;
import com.google.code.yadview.DayView;
import com.google.code.yadview.DayViewOnKeyListener;
import com.google.code.yadview.DayViewRenderer;
import com.google.code.yadview.EventRenderer;
import com.google.code.yadview.EventResource;
import com.google.code.yadview.events.ShowDateInDayViewEvent;
import com.google.code.yadview.impl.DefaultDayViewFactory;
import com.google.common.eventbus.Subscribe;
import com.google.gode.yadview_harness.R;

public class YadviewHarnessDayViewFactory extends DefaultDayViewFactory {

    private Animation mInAnimationForward;
    private Animation mOutAnimationForward;
    private Animation mInAnimationBackward;
    private Animation mOutAnimationBackward;
    private Activity mActivity;

    public YadviewHarnessDayViewFactory(ViewSwitcher vs, EventResource eventResource, Activity context) {
        super(context, vs, eventResource, "yadview_harness.prefs", new AlternateRendererDayViewResources(context));
        
        mActivity = context;
        
        mInAnimationForward = AnimationUtils.loadAnimation(context, R.anim.slide_left_in);
        mOutAnimationForward = AnimationUtils.loadAnimation(context, R.anim.slide_left_out);
        mInAnimationBackward = AnimationUtils.loadAnimation(context, R.anim.slide_right_in);
        mOutAnimationBackward = AnimationUtils.loadAnimation(context, R.anim.slide_right_out);
    }
    
    @Override
    public View makeView() {
        
        DayView dv = new DayView(getContext(),getViewSwitcher(), 1, getEventLoader(), getResources(), this);
        dv.setLayoutParams(new ViewSwitcher.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
//        dv.setOnCreateContextMenuListener(new DayViewOnCreateContextMenuListener(getContext(), dv, utilFactory, resources, getEventResource()));
//        dv.setOnLongClickListener(new DayViewOnLongClickListener(getContext(), dv, resources, utilFactory));
        dv.setOnKeyListener(new DayViewOnKeyListener(getContext(), dv));
        
        dv.getEventBus().register(this);
        
        mActivity.registerForContextMenu(dv);
        
        return dv;
    }
    
//    @Override
//    public DayViewScrollingController buildScrollingController(EventBus eventBus) {
//        return new DayViewScrollingController(eventBus){
//            @Override
//            public void scrolled(float x, float y, float deltaX, float deltaY) {
//                super.scrolled(x, y, 0, deltaY);
//            }
//        };
//    }
    
    @Override
    public EventRenderer buildEventRenderer() {
        return new  AlternateEventRenderer(getContext(), getResources(),this);
    }
    
    @Override
    public DayViewRenderer buildDayViewRenderer() {
        return new AlternateDayViewRenderer(getResources());
    }
    
    /*@Subscribe
    public void handleShowDateEvent(ViewEventEvent e){
    	//e.getEvent().
    	//((AlternateEventRenderer) buildEventRenderer()).getWidth();
    	Toast.makeText(getContext(), "test! " + e.getEvent().getTitleAndLocation().toString() + ((AlternateEventRenderer) buildEventRenderer()).getXCoords(), Toast.LENGTH_SHORT ).show();
    }*/
    
    
//    @Subscribe
//    public void handleShowDateEvent(ShowDateInCurrentViewEvent e){
 //       goTo(e.getShowDate(), e.getShowTime());
  //  }
    
    @Subscribe
    public void handleShowDateEvent(ShowDateInDayViewEvent e){
        goTo(e.getSelectedTime(), e.getSelectedTime());
    }
    
    private void goTo(Time showDate, Time showTime) {
        if (getViewSwitcher() == null) {
            return;
        }

        DayView currentView = (DayView) getViewSwitcher().getCurrentView();

        // How does goTo time compared to what's already displaying?
        int diff = currentView.compareToVisibleTimeRange(showTime);

        if (diff == 0) {
            // In visible range. No need to switch view
            currentView.setSelected(showTime, false, false);
        } else {
            // Figure out which way to animate
            if (diff > 0) {
                getViewSwitcher().setInAnimation(mInAnimationForward);
                getViewSwitcher().setOutAnimation(mOutAnimationForward);
            } else {
                getViewSwitcher().setInAnimation(mInAnimationBackward);
                getViewSwitcher().setOutAnimation(mOutAnimationBackward);
            }

            DayView next = (DayView) getViewSwitcher().getNextView();

            next.setSelected(showTime, false, false);
            next.reloadEvents();
            getViewSwitcher().showNext();
            next.requestFocus();
            next.updateTitle();
            next.restartCurrentTimeUpdates();        
    }

    

    }
    
}
