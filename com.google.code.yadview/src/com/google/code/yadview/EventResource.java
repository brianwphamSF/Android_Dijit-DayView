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


package com.google.code.yadview;


import java.util.List;

public interface EventResource {
    
    public static final int ACCESS_LEVEL_NONE = 0;
    public static final int ACCESS_LEVEL_DELETE = 1;
    public static final int ACCESS_LEVEL_EDIT = 2;

    
    List<Event> get(int startJulianDay, int numDays,Predicate continueLoading);

    public int getEventAccessLevel(Event e);
    
    
    

    
}

