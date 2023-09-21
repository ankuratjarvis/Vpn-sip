package com.example.myapplication.utils;

import com.example.myapplication.SipActivity;

import org.pjsip.pjsua2.LogEntry;
import org.pjsip.pjsua2.LogWriter;

public class MyLogWriter extends LogWriter
{
    @Override
    public void write(LogEntry entry)
    {
        /**
         * the below line of code threw Swig::DirectorException
         */
        SipActivity.getInstance().log(entry.getMsg());
        System.out.println(entry.getMsg());
    }
}