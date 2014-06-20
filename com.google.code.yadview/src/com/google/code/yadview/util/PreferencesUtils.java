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

package com.google.code.yadview.util;

import java.util.LinkedHashSet;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesUtils {

    
    private String mPrefName;

    public PreferencesUtils(String prefName) {
        mPrefName = prefName;
    }
    
    
    public void setSharedPreference(Context context, String key, int value) {
        SharedPreferences prefs = getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(key, value);
        editor.apply();
    }
    
    public void setSharedPreference(Context context, String key, String value) {
        SharedPreferences prefs = getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();

    }
    
    
    public void setSharedPreference(Context context, String key, String[] values) {
        SharedPreferences prefs = getSharedPreferences(context);
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        for (String value : values) {
            set.add(value);
        }
        prefs.edit().putStringSet(key, set).apply();
    }

    public void setSharedPreference(Context context, String key, boolean value) {
        SharedPreferences prefs = getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    

    public String[] getSharedPreference(Context context, String key, String defaultValue[]) {
        SharedPreferences prefs = getSharedPreferences(context);
        Set<String> ss = prefs.getStringSet(key, null);
        if (ss != null) {
            String strings[] = new String[ss.size()];
            return ss.toArray(strings);
        }
        return defaultValue;
    }

    public String getSharedPreference(Context context, String key, String defaultValue) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }

    public int getSharedPreference(Context context, String key, int defaultValue) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getInt(key, defaultValue);
    }

    public boolean getSharedPreference(Context context, String key, boolean defaultValue) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getBoolean(key, defaultValue);

    }


    public SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);

    }






}
