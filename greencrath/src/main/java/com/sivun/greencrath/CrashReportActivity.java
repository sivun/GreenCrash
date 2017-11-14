/*
 *  Copyright 2010 Emmanuel Astier & Kevin Gaudin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.sivun.greencrath;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 报错日志显示界面（面向开发者）
 */
public class CrashReportActivity extends Activity {
    /**
     * Default left title icon.
     */

    private String mReportFileName = null;
    private String mReportFilePath = null;
    private String mContent = null;
    private TextView mPathView, mFileNameView, mContentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.crash_layout);
        mPathView = (TextView) findViewById(R.id.path);
        mFileNameView = (TextView) findViewById(R.id.file_name);
        mContentView = (TextView) findViewById(R.id.content);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null) {
            return;
        }
        mReportFilePath = i.getStringExtra(CrashReporter.EXTRA_REPORT_FILE_PATH);
        if (!TextUtils.isEmpty(mReportFilePath)) {
            //内部
            cancelNotification();
        } else {
            //外部
            Uri uri = i.getData();
            mReportFilePath = uri != null ? uri.getPath() : null;
        }
        if (!TextUtils.isEmpty(mReportFilePath)) {
            mReportFileName = new File(mReportFilePath).getName();
            mContent = readFile(mReportFilePath);
        }
        mReportFilePath = TextUtils.isEmpty(mReportFilePath) ? "unknow" : mReportFilePath;
        mReportFileName = TextUtils.isEmpty(mReportFileName) ? "unknow" : mReportFileName;
        mContent = TextUtils.isEmpty(mContent) ? "unknow" : mContent;
        mPathView.setText("LogPath:" + mReportFilePath);
        mFileNameView.setText(mReportFileName);
        mContentView.setText(mContent);
    }

    private String readFile(String mReportFilePath) {
        StringBuffer sb = new StringBuffer();
        try {
            FileInputStream fis = new FileInputStream(mReportFilePath);
            String lins;
            BufferedReader bfr = new BufferedReader(new InputStreamReader(fis));
            lins = bfr.readLine();
            while (lins != null) {
                sb.append(lins);
                sb.append("\n");
                lins = bfr.readLine();
            }
            bfr.close();
            fis.close();
            return sb.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Disable the notification in the Status Bar.
     */
    protected void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(CrashReporter.NOTIF_CRASH_ID);
    }


    private void onYes() {
        try {
            // 重启应用
            CrashReporter.restartApp(getApplicationContext());
//			发送邮件
//        	ReportsSenderWorker worker = err.new ReportsSenderWorker();
//        	worker.setCommentReportFileName(mReportFileName);
//        	worker.setReportStack(mReportStack);
//        	worker.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finish();
    }

    private void onNo() {
        finish();
//    	System.exit(0);不中断线程，或是，判断当前线程是否在运行
    }


    //在助手中显示异常日志
    private void showLogInGGHelper() {
        try {
//    		Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage("com.jb.helper");
//    		LaunchIntent.putExtra("action", "open_newest_log");
//    		LaunchIntent.putExtra("log_filename", mReportFileName);
//    		startActivity(LaunchIntent);
//    		finish();
        } catch (Exception e) {

        }
    }
}
