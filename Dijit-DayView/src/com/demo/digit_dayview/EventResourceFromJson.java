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

-----

Edit by Brian Pham: This portion of the code originates from
	MockEventResource.java which is also included in the source.
	The change is to read from JSON files / URLs and parse its data
	to be read.
 
 */

package com.demo.digit_dayview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.text.format.Time;
import android.util.Log;

import com.google.code.yadview.Event;
import com.google.code.yadview.EventResource;
import com.google.code.yadview.Predicate;
import com.google.common.collect.Lists;

public class EventResourceFromJson implements EventResource {

	// Initialize our list of strings
	List<String> event_name_list = new ArrayList<String>();
	List<String> start_time_list = new ArrayList<String>();
	List<String> end_time_list = new ArrayList<String>();

	// Process our JSON in a separate thread
	public class RetrieveFromJSON extends AsyncTask<Void, Void, Void> {

		// JSON data URL
		private static final String url = "https://raw.githubusercontent.com/brianwphamSF/test_json_files/master/dijit.json";

		// JSON node names
		private static final String TAG_EVENT_NAME = "event_name";
		private static final String TAG_START_TIME = "start_time";
		private static final String TAG_END_TIME = "end_time";

		// Create new instance of JSONParser
		JSONParser jParser = new JSONParser();

		// Parse into a JSON array from JSON data URL
		JSONArray jsonArray = jParser.getJSONArrayFromUrl(url);

		@Override
		protected Void doInBackground(Void... params) {
			// TODO Auto-generated method stub
			try {

				for (int i = 0; i < jsonArray.length(); i++) {
					
					// Get current instance of JSON object from array.
					JSONObject jObj = jsonArray.getJSONObject(i);

					// Populate the lists accordingly.
					event_name_list.add(jObj.getString(TAG_EVENT_NAME));
					start_time_list.add(jObj.getString(TAG_START_TIME));
					end_time_list.add(jObj.getString(TAG_END_TIME));
				}

			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

	}

	int colours[] = new int[] { 0xffB467B5, 0xff677DB5, 0xff67B59F };

	@Override
	public List<Event> get(int startJulianDay, int numDays,
			Predicate continueLoading) {
		
		// Get the data from our separate thread
		try {
			new RetrieveFromJSON().execute().get();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// Debug statements
		/*
		Log.d("event array", event_name_list.toString());
		Log.d("start times", start_time_list.toString());
		Log.d("end times", end_time_list.toString());

		Log.d("test", event_name_list.get(0));
		Log.d("test2", start_time_list.get(2));

		Log.d("sizeof array", "size: " + event_name_list.size());
		*/
		
		List<Event> events = Lists.newArrayList();
		
		int startHours, endHours, startMinutes, endMinutes;

		for (int i = 0; i < numDays; i++) {
			
			// Make new time for the day
			Time scratch = new Time();
			Event e;
			
			// Start adding the list of events from JSON
			for (int j = 0; j < event_name_list.size(); j++) {
				
				// Create the event
				e = new Event();
				
				e.setAllDay(false);
				e.setEndDay(startJulianDay + i);
				e.setId(j + 1L);
				
				// Calculating the height of event by time
				Double endTime = Double.parseDouble(end_time_list.get(j));
				Double startTime = Double.parseDouble(start_time_list.get(j));

				// Check whether or not there is a dot character
				// first for end time, then for start time
				if (end_time_list.get(j).contains(".")) {
					String[] time = end_time_list.get(j).split("\\.");
					
					// Make into hours and minutes from the time array
					endHours = Integer.parseInt(time[0]);
					endMinutes = Integer.parseInt(time[1]);
					
					// Append . in minutes
					String myMinute = "." + endMinutes;
					
					// Parse the minute string
					Double calculatedMinute = Double.parseDouble(myMinute) * 60;
					//Log.d("the end time", "j:" + j + " " + (int) (endHours * 60 + calculatedMinute));
					
					// Set the end time from the minutes and hours
					e.setEndTime((int) (endHours * 60 + calculatedMinute));
				} else {
					endHours = Integer.parseInt(end_time_list.get(j));
					e.setEndTime(endHours * 60 - 1);
					//Log.d("the end time without min", "j:" + j + " " + (endHours * 60));
				}

				e.setStartDay(startJulianDay + i);

				if (start_time_list.get(j).contains(".")) {
					// Same idea as above in check list
					String[] time = start_time_list.get(j).split("\\.");
					
					startHours = Integer.parseInt(time[0]);
					startMinutes = Integer.parseInt(time[1]);
					
					String myMinute = "." + startMinutes;
					
					Double calculatedMinute = Double.parseDouble(myMinute) * 60;
					
					e.setStartTime((int) (startHours * 60 + calculatedMinute));
					//Log.d("the start time (Y-coord)", "j:" + j + " " + (int) (startHours * 60 + calculatedMinute));
				} else {
					startHours = Integer.parseInt(start_time_list.get(j));
					e.setStartTime(startHours * 60);
					//Log.d("the start time without min (Y-coord)", "j:" + j + " " + (startHours * 60));
				}
				
				Log.d("Y coordinate in time", start_time_list.get(j));

				e.setColor(randomColour());
				
				// At the first event of the day, start the day
				if (j == 0)
					scratch.setJulianDay(startJulianDay + i);
				
				// Allocate the times
				scratch.hour = startHours;
				scratch.minute = e.getStartTime() % 60;
				e.setStartMillis(scratch.toMillis(false));
				scratch.hour = endHours;
				scratch.minute = e.getEndTime() % 60;
				e.setEndMillis(scratch.toMillis(false));
				
				Log.d("height in time", ("" + (endTime - startTime)));

				e.setTitle(event_name_list.get(j));
				//Log.d("event name", event_name_list.get(j));
				events.add(e);
				
			}
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
