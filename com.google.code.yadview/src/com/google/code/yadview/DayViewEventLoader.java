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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import android.os.Handler;
import android.os.Process;
import android.util.Log;


public class DayViewEventLoader {

    private Handler mHandler = new Handler();
    private AtomicInteger mSequenceNumber = new AtomicInteger();

    private LinkedBlockingQueue<LoadRequest> mLoaderQueue;
    private LoaderThread mLoaderThread;
    private EventResource mEventResource;

    private static interface LoadRequest {
        public void processRequest(DayViewEventLoader eventLoader);
        public void skipRequest(DayViewEventLoader eventLoader);
    }

    private static class ShutdownRequest implements LoadRequest {
        public void processRequest(DayViewEventLoader eventLoader) {
        }

        public void skipRequest(DayViewEventLoader eventLoader) {
        }
    }

    /**
     *
     * Code for handling requests to get whether days have an event or not
     * and filling in the eventDays array.
     *
     */
    private static class LoadEventDaysRequest implements LoadRequest {
        public int startDay;
        public int numDays;
        public boolean[] eventDays;
        public Runnable uiCallback;
		private EventResource mResource;

        public LoadEventDaysRequest(int startDay, int numDays, boolean[] eventDays,
                final Runnable uiCallback, EventResource eventResource)
        {
        	//verify: eventdays must be same length as numDays
        	if(eventDays.length < numDays){
        		throw new IllegalArgumentException("Not enough room to mark days as having events");
        	}
        	
            this.startDay = startDay;
            this.numDays = numDays;
            this.eventDays = eventDays;
            this.uiCallback = uiCallback;
            mResource = eventResource;
        }

        @Override
        public void processRequest(DayViewEventLoader eventLoader)
        {
        	
        	//which DAYS have events
        	//not a cancellable request
        	
        	//do this inefficiently for now..
        	for(int i = 0; i < numDays; i++){
        		List<Event> list = mResource.get(startDay + i,1, Predicate.TRUE);
        		eventDays[i] = list.size() > 0;
        	}

            eventLoader.mHandler.post(uiCallback);
        }

        @Override
        public void skipRequest(DayViewEventLoader eventLoader) {
        }
    }

    private static class LoadEventsRequest implements LoadRequest {

        protected int id;
        protected int startDay;
        protected int numDays;
        protected ArrayList<Event> events;
        protected Runnable successCallback;
        protected Runnable cancelCallback;
        private EventResource mEventResource;

        public LoadEventsRequest(int id, int startDay, int numDays, ArrayList<Event> events,
                final Runnable successCallback, final Runnable cancelCallback, EventResource eventResource) {
            this.id = id;
            this.startDay = startDay;
            this.numDays = numDays;
            this.events = events;
            this.successCallback = successCallback;
            this.cancelCallback = cancelCallback;
            this.mEventResource = eventResource;
        }

        public void processRequest(final DayViewEventLoader eventLoader) {
            
            Predicate continueLoadingPredicate = new Predicate() {
                @Override
                public boolean value() {
                    return id == eventLoader.mSequenceNumber.get();
                }
            };
            
            events.clear();
            events.addAll(mEventResource.get(startDay, numDays, continueLoadingPredicate));

            // Check if we are still the most recent request.
            if (continueLoadingPredicate.value()) {
                eventLoader.mHandler.post(successCallback);
            } else {
                eventLoader.mHandler.post(cancelCallback);
            }
        }

        public void skipRequest(DayViewEventLoader eventLoader) {
            eventLoader.mHandler.post(cancelCallback);
        }
    }

    private static class LoaderThread extends Thread {
        LinkedBlockingQueue<LoadRequest> mQueue;
        DayViewEventLoader mEventLoader;

        public LoaderThread(LinkedBlockingQueue<LoadRequest> queue, DayViewEventLoader eventLoader) {
            mQueue = queue;
            mEventLoader = eventLoader;
        }

        public void shutdown() {
            try {
                mQueue.put(new ShutdownRequest());
            } catch (InterruptedException ex) {
                // The put() method fails with InterruptedException if the
                // queue is full. This should never happen because the queue
                // has no limit.
                Log.e("Cal", "LoaderThread.shutdown() interrupted!");
            }
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            while (true) {
                try {
                    // Wait for the next request
                    LoadRequest request = mQueue.take();

                    // If there are a bunch of requests already waiting, then
                    // skip all but the most recent request.
                    while (!mQueue.isEmpty()) {
                        // Let the request know that it was skipped
                        request.skipRequest(mEventLoader);

                        // Skip to the next request
                        request = mQueue.take();
                    }

                    if (request instanceof ShutdownRequest) {
                        return;
                    }
                    request.processRequest(mEventLoader);
                } catch (InterruptedException ex) {
                    Log.e("Cal", "background LoaderThread interrupted!");
                }
            }
        }
    }

    public DayViewEventLoader(EventResource eventResource) {
        mLoaderQueue = new LinkedBlockingQueue<LoadRequest>();
        mEventResource = eventResource;
    }

    /**
     * Call this from the activity's onResume()
     */
    public void startBackgroundThread() {
        mLoaderThread = new LoaderThread(mLoaderQueue, this);
        mLoaderThread.start();
    }

    /**
     * Call this from the activity's onPause()
     */
    public void stopBackgroundThread() {
        mLoaderThread.shutdown();
    }

    /**
     * Loads "numDays" days worth of events, starting at start, into events.
     * Posts uiCallback to the {@link Handler} for this view, which will run in the UI thread.
     * Reuses an existing background thread, if events were already being loaded in the background.
     * NOTE: events and uiCallback are not used if an existing background thread gets reused --
     * the ones that were passed in on the call that results in the background thread getting
     * created are used, and the most recent call's worth of data is loaded into events and posted
     * via the uiCallback.
     */
    public void loadEventsInBackground(final int numDays, final ArrayList<Event> events,
            int startDay, final Runnable successCallback, final Runnable cancelCallback) {

        // Increment the sequence number for requests.  We don't care if the
        // sequence numbers wrap around because we test for equality with the
        // latest one.
        int id = mSequenceNumber.incrementAndGet();

        // Send the load request to the background thread
        LoadEventsRequest request = new LoadEventsRequest(id, startDay, numDays,
                events, successCallback, cancelCallback, mEventResource);

        try {
            mLoaderQueue.put(request);
        } catch (InterruptedException ex) {
            // The put() method fails with InterruptedException if the
            // queue is full. This should never happen because the queue
            // has no limit.
            Log.e("Cal", "loadEventsInBackground() interrupted!");
        }
    }

    /**
     * Sends a request for the days with events to be marked. Loads "numDays"
     * worth of days, starting at start, and fills in eventDays to express which
     * days have events.
     *
     * @param startDay First day to check for events
     * @param numDays Days following the start day to check
     * @param eventDay Whether or not an event exists on that day
     * @param uiCallback What to do when done (log data, redraw screen)
     */
    void loadEventDaysInBackground(int startDay, int numDays, boolean[] eventDays,
        final Runnable uiCallback)
    {
        // Send load request to the background thread
        LoadEventDaysRequest request = new LoadEventDaysRequest(startDay, numDays,
                eventDays, uiCallback, mEventResource);
        try {
            mLoaderQueue.put(request);
        } catch (InterruptedException ex) {
            // The put() method fails with InterruptedException if the
            // queue is full. This should never happen because the queue
            // has no limit.
            Log.e("Cal", "loadEventDaysInBackground() interrupted!");
        }
    }
}
