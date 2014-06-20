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


import android.content.Context;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;

import com.google.code.yadview.events.CreateEventEvent;
import com.google.code.yadview.events.DeleteEventEvent;
import com.google.code.yadview.events.EditEventEvent;
import com.google.code.yadview.events.ShowDateInAgendaViewEvent;
import com.google.code.yadview.events.ShowDateInDayViewEvent;
import com.google.code.yadview.events.ViewEventEvent;

public class DayViewOnCreateContextMenuListener implements OnCreateContextMenuListener {

    private DayView mDayView;
    private DayViewDependencyFactory mUtilFactory;
    private Context mContext;
    private DayViewResources mDayViewResources;
    private final ContextMenuHandler mContextMenuHandler;
    private EventResource mEventResource;

    private static final int MENU_AGENDA = 2;
    private static final int MENU_DAY = 3;
    private static final int MENU_EVENT_VIEW = 5;
    private static final int MENU_EVENT_CREATE = 6;
    private static final int MENU_EVENT_EDIT = 7;
    private static final int MENU_EVENT_DELETE = 8;

    
    
    
    public DayViewOnCreateContextMenuListener(Context ctx, DayView dv, DayViewDependencyFactory defaultDayViewFactory, DayViewResources resources, EventResource eventResource) {
        mContext = ctx;
        mDayView = dv;
        mUtilFactory = defaultDayViewFactory;
        mDayViewResources = resources;
        mEventResource = eventResource;
        mContextMenuHandler = new ContextMenuHandler();

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuItem item;

        // If the trackball is held down, then the context menu pops up and
        // we never get onKeyUp() for the long-press. So check for it here
        // and change the selection to the long-press state.
        if (mDayView.getSelectionMode() != DayView.SELECTION_LONGPRESS) {
            mDayView.setSelectionMode(DayView.SELECTION_LONGPRESS);
            mDayView.invalidate();
        }

        final long startMillis = mDayView.getSelectedTimeInMillis();
        int flags = DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_CAP_NOON_MIDNIGHT
                | DateUtils.FORMAT_SHOW_WEEKDAY;
        final String title = mUtilFactory.buildTimezoneUtils().formatDateRange(mContext, startMillis, startMillis, flags);
        menu.setHeaderTitle(title);

        int numEvents = mDayView.getSelectedEvents().size();
        if (mDayView.getNumDays() == 1) {
            // Day view.

            // If there is a selected event, then allow it to be viewed and
            // edited.
            if (numEvents >= 1) {
                item = menu.add(0, MENU_EVENT_VIEW, 0, mDayViewResources.getViewEventMenuItemLabel() );
                        
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_info_details);

                int accessLevel = mEventResource.getEventAccessLevel(mDayView.getSelectedEvent());
                if (accessLevel == EventResource.ACCESS_LEVEL_EDIT) {
                    item = menu.add(0, MENU_EVENT_EDIT, 0, mDayViewResources.getEditEventMenuItemLabel());
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_edit);
                    item.setAlphabeticShortcut('e');
                }

                if (accessLevel >= EventResource.ACCESS_LEVEL_DELETE) {
                    item = menu.add(0, MENU_EVENT_DELETE, 0, mDayViewResources.getDeleteEventMenuItemLabel() );
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_delete);
                }

                item = menu.add(0, MENU_EVENT_CREATE, 0, mDayViewResources.getCreateEventMenuItemLabel());
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_add);
                item.setAlphabeticShortcut('n');
            } else {
                // Otherwise, if the user long-pressed on a blank hour, allow
                // them to create an event. They can also do this by tapping.
                item = menu.add(0, MENU_EVENT_CREATE, 0, mDayViewResources.getCreateEventMenuItemLabel());
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_add);
                item.setAlphabeticShortcut('n');
            }
        } else {
            // Week view.

            // If there is a selected event, then allow it to be viewed and
            // edited.
            if (mDayView.getNumDays() >= 1) {
                item = menu.add(0, MENU_EVENT_VIEW, 0, mDayViewResources.getViewEventMenuItemLabel());
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_info_details);

                int accessLevel = mEventResource.getEventAccessLevel(mDayView.getSelectedEvent());
                if (accessLevel == EventResource.ACCESS_LEVEL_EDIT) {
                    item = menu.add(0, MENU_EVENT_EDIT, 0, mDayViewResources.getEditEventMenuItemLabel());
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_edit);
                    item.setAlphabeticShortcut('e');
                }

                if (accessLevel >= EventResource.ACCESS_LEVEL_DELETE) {
                    item = menu.add(0, MENU_EVENT_DELETE, 0, mDayViewResources.getDeleteEventMenuItemLabel());
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_delete);
                }
            }

            item = menu.add(0, MENU_EVENT_CREATE, 0, mDayViewResources.getCreateEventMenuItemLabel());
            item.setOnMenuItemClickListener(mContextMenuHandler);
            item.setIcon(android.R.drawable.ic_menu_add);
            item.setAlphabeticShortcut('n');

            item = menu.add(0, MENU_DAY, 0, mDayViewResources.getShowDayViewMenuItemLabel());
            item.setOnMenuItemClickListener(mContextMenuHandler);
            item.setIcon(android.R.drawable.ic_menu_day);
            item.setAlphabeticShortcut('d');
        }

        //TODO: There must be a better way
        mDayView.mPopup.dismiss();
    }
    
    
    private class ContextMenuHandler implements MenuItem.OnMenuItemClickListener {
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case MENU_EVENT_VIEW: {
                    if (mDayView.getSelectedEvent() != null) {
                        mDayView.getEventBus().post(new ViewEventEvent(mDayView.getSelectedEvent(), mDayView.getSelectedTimeInMillis()));
                    }
                    break;
                }
                case MENU_EVENT_EDIT: {
                    if (mDayView.getSelectedEvent() != null) {
                        mDayView.getEventBus().post(new EditEventEvent(mDayView.getSelectedEvent()));
                    }
                    break;
                }
                case MENU_DAY: {
                    mDayView.getEventBus().post(new ShowDateInDayViewEvent(mDayView.getSelectedTime()));
                    break;
                }
                case MENU_AGENDA: {
                    mDayView.getEventBus().post(new ShowDateInAgendaViewEvent(mDayView.getSelectedTime()));
                    break;
                }
                case MENU_EVENT_CREATE: {
                    mDayView.getEventBus().post(new CreateEventEvent(mDayView.getSelectedTimeInMillis(), mDayView.isSelectionAllday()));
                    break;
                }
                case MENU_EVENT_DELETE: {
                    if (mDayView.getSelectedEvent() != null) {
                        Event selectedEvent = mDayView.getSelectedEvent();
                        long begin = selectedEvent.getStartMillis();
                        long end = selectedEvent.getEndMillis();
                        long id = selectedEvent.getId();
                        
                        //TODO: Previous behavior meant that this event would only be deleted if SearchActivity had
                        //registered as an eventhandler with a share CalendarController
                        //seems like an unlikely scenario
                        mDayView.getEventBus().post(new DeleteEventEvent(mDayView.getSelectedEvent()));
                    }
                    break;
                }
                default: {
                    return false;
                }
            }
            return true;
        }
    }
    
    


}
