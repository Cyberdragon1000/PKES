package com.capstone.pkes;

import android.annotation.SuppressLint;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class timestamp {

//generates a timestamp of format 'daymonthyear-hourminutesecond'
    static String gettimestamp(){
        Calendar c = Calendar.getInstance();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateformat = new SimpleDateFormat("ddMMMyyyy-hhmmss");
        return dateformat.format(c.getTime());

    }
}
