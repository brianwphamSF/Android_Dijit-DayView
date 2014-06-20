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
import android.os.Bundle;
import android.text.format.Time;
import android.view.Menu;
import android.widget.ViewSwitcher;

import com.google.code.yadview.DayView;
import com.google.code.yadview.EventResource;
import com.google.gode.yadview_harness.R;

public class MainActivity extends Activity  {

    private EventResource mEventResource;
    private YadviewHarnessDayViewFactory mViewFactory;

	public MainActivity() {
		//mEventResource = new MockEventResource();
	    mEventResource = new EventResourceFromJson();
//	    mEventResource = new DefaultEventResource(this, new DefaultUtilFactory("yadview_harness.prefs"));
		
		
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		ViewSwitcher vs = (ViewSwitcher)findViewById(R.id.view_switcher);
		mViewFactory = new YadviewHarnessDayViewFactory(vs, mEventResource, this);
		vs.setFactory(mViewFactory);
		DayView dv = (DayView)vs.getCurrentView();
        Time today = new Time();
        today.setToNow();
        dv.setSelected(today, false, false);
        dv.clearCachedEvents();
        dv.reloadEvents();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mViewFactory.getEventLoader().startBackgroundThread();
		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		mViewFactory.getEventLoader().stopBackgroundThread();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	


}
