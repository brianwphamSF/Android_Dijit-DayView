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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import android.text.format.Time;
import android.util.Log;

import com.google.code.yadview.Event;
import com.google.code.yadview.EventResource;
import com.google.code.yadview.Predicate;
import com.google.common.collect.Lists;

public class MockEventResource implements EventResource {

    int colours[] = new int[] {
            0xffB467B5,
            0xff677DB5,
            0xff67B59F
    };
    
    @Override
    public List<Event> get(int startJulianDay, int numDays, Predicate continueLoading) {
        List<Event> events = Lists.newArrayList();

        for(int i = 0; i < numDays; i++){
            Event e1 = new Event();

            e1.setAllDay(false);
            e1.setEndDay(startJulianDay + i);
            e1.setId(1);
            e1.setEndTime(9 * 60 - 1);
            e1.setStartDay(startJulianDay + i);
            e1.setStartTime(8 * 60);
            e1.setColor(randomColour());
            
            Log.d("start", "" + 9 * 60);
            Log.d("end", "" + 8 * 60);

            Time scratch = new Time();
            scratch.setJulianDay(startJulianDay + i);
            scratch.hour = 8;
            scratch.minute = e1.getStartTime() % 60;
            e1.setStartMillis(scratch.toMillis(false));
            scratch.hour = 9;
            scratch.minute = e1.getEndTime() % 60;
            e1.setEndMillis(scratch.toMillis(false));

            e1.setTitle("testevent");
            events.add(e1);

            if ((startJulianDay + i) % 2 == 0) {
                e1 = new Event();
                e1.setAllDay(true);
                e1.setEndDay(startJulianDay + i);
                e1.setId(2);
                e1.setStartDay(startJulianDay + i);
                e1.setColor(randomColour());
                e1.setTitle("testevent-allday");
                events.add(e1);
            }
            
            
            if ((startJulianDay + i) % 3 == 0) {
                e1 = new Event();
                e1.setAllDay(true);
                e1.setEndDay(startJulianDay + i);
                e1.setId(2);
                e1.setStartDay(startJulianDay + i);
                e1.setColor(randomColour());
                e1.setTitle("testevent2-allday");
                events.add(e1);
            }
            
            if ((startJulianDay + i) % 4 == 0) {
                e1 = new Event();
                e1.setAllDay(true);
                e1.setEndDay(startJulianDay + i);
                e1.setId(2);
                e1.setStartDay(startJulianDay + i);
                e1.setColor(randomColour());
                e1.setTitle("testevent3-allday");
                events.add(e1);
            }

            e1 = new Event();
            e1.setAllDay(false);
            e1.setEndDay(startJulianDay + i);
            e1.setId(3);
            e1.setEndTime(10 * 60 - 1);
            e1.setStartDay(startJulianDay + i);
            e1.setStartTime(7 * 60);
            e1.setColor(randomColour());
            
            scratch.hour = 7;
            scratch.minute = e1.getStartTime() % 60;
            e1.setStartMillis(scratch.toMillis(false));
            scratch.hour = 10;
            scratch.minute = e1.getEndTime() % 60;
            e1.setEndMillis(scratch.toMillis(false));

            e1.setTitle("overlapping");
            events.add(e1);

            e1 = new Event();
            e1.setAllDay(false);
            e1.setEndDay(startJulianDay + i);
            e1.setId(4);
            e1.setEndTime(11 * 60 + 30 - 1);
            e1.setStartDay(startJulianDay + i);
            e1.setStartTime(11 * 60);
            e1.setColor(randomColour());
            
            scratch.hour = 11;
            scratch.minute = e1.getStartTime() % 60;
            e1.setStartMillis(scratch.toMillis(false));
            scratch.hour = 11;
            scratch.minute = e1.getEndTime() % 60;
            e1.setEndMillis(scratch.toMillis(false));
            e1.setColor(randomColour());
            
            e1.setTitle("short");
            events.add(e1);

            e1 = new Event();
            e1.setAllDay(false);
            e1.setEndDay(startJulianDay + i);
            e1.setId(5);
            e1.setEndTime(12 * 60 + 30 - 1);
            e1.setStartDay(startJulianDay + i);
            e1.setStartTime(11 * 60 + 30);
            e1.setColor(randomColour());
            
            scratch.hour = 11;
            scratch.minute = e1.getStartTime() % 60;
            e1.setStartMillis(scratch.toMillis(false));
            scratch.hour = 12;
            scratch.minute = e1.getEndTime() % 60;
            e1.setEndMillis(scratch.toMillis(false));

            e1.setTitle("adjacent");
            events.add(e1);
            
        }
        

        Collections.sort(events, new Comparator<Event>() {

            @Override
            public int compare(Event lhs, Event rhs) {
                long l = lhs.getStartMillis() - rhs.getStartMillis();
                if (l < 0) {
                    return -1;
                } else if (l > 0) {
                    return 1;
                } else
                    return 0;
            }
        });

        return events;

    }

    @Override
    public int getEventAccessLevel(Event e) {
        return ACCESS_LEVEL_DELETE;
        
    }

    public int randomColour() {
        return colours[new Random().nextInt(colours.length)];
    }
}
