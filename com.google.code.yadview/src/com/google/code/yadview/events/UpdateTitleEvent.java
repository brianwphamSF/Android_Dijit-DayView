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


package com.google.code.yadview.events;

import android.text.format.Time;

public class UpdateTitleEvent {

    private final Time mStartTime;
    private final Time mEndTime;
    private final int mNumDaysDisplayed;

    public UpdateTitleEvent(Time start, Time end, int numDays) {
        mStartTime = start;
        mEndTime = end;
        mNumDaysDisplayed = numDays;
        
    }
    
    public Time getEndTime() {
        return mEndTime;
    }
    
    public Time getStartTime() {
        return mStartTime;
    }
    
    public int getNumDaysDisplayed() {
        return mNumDaysDisplayed;
    }
    

}
