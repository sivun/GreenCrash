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

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/***
 * 扑捉崩溃信息
 *
 */
public class CrashReporter implements UncaughtExceptionHandler {

    /**
     * 反馈方式
     */
    public enum ReportingInteractionMode {
        SILENT, NOTIFICATION, TOAST;
    }

    private static final String VERSION_NAME_KEY = "VersionName";
    private static final String PACKAGE_NAME_KEY = "PackageName";
    private static final String PHONE_MODEL_KEY = "PhoneModel";
    private static final String ANDROID_VERSION_KEY = "AndroidVersion";
    private static final String BOARD_KEY = "BOARD";
    private static final String BRAND_KEY = "BRAND";
    private static final String DEVICE_KEY = "DEVICE";
    private static final String DISPLAY_KEY = "DISPLAY";
    private static final String FINGERPRINT_KEY = "FINGERPRINT";
    private static final String MODEL_KEY = "MODEL";
    private static final String PRODUCT_KEY = "PRODUCT";
    private static final String TAGS_KEY = "TAGS";
    private static final String TIME_KEY = "TIME";
    private static final String TYPE_KEY = "TYPE";
    private static final String TOTAL_MEM_SIZE_KEY = "TotalMemSize";
    private static final String AVAILABLE_MEM_SIZE_KEY = "AvaliableMemSize";
    private static final String CUSTOM_DATA_KEY = "CustomData";

    private static final String START_APP_TIME = "StartAppTime";
    private static final String CRASH_APP_TIME = "CrashAppTime";

    private static final String STACK_TRACE_KEY = "StackTrace";

    private static final String VERSION_CODE = "VersionCode";

    private static final int MAX_LEFT_FILES = 10;
    /**
     * Bundle key for the icon in the status bar notification.
     */
    public static final String RES_NOTIF_ICON = "RES_NOTIF_ICON";

    /**
     * This is the identifier (value = 666) use for the status bar notification
     * issued when crashes occur.
     */
    public static final int NOTIF_CRASH_ID = 0x28a;
    private Properties mCrashProperties = new Properties();
    private long mStartAppTime = 0;
    private Debug.MemoryInfo mStartMem = null;

    private Map<String, String> mCustomParameters = new HashMap<String, String>();
    static final String REPORT_MODE = "report_mode";

    static final String EXTRA_REPORT_FILE_PATH = "crash_file_path";

    // A reference to the system's previous default UncaughtExceptionHandler
    // kept in order to execute the default exception handling after sending
    // the report.
    private UncaughtExceptionHandler mDfltExceptionHandler;

    // The application context
    private Context mContext;

    // User interaction mode defined by the application developer.
    private ReportingInteractionMode mReportingInteractionMode = ReportingInteractionMode.SILENT;

    // Bundle containing resources to be used in UI elements.
    private Bundle mCrashResources = new Bundle();

    // The Url we have to post the reports to.

    private String mCrashFilePath = null;
    private static final String LOG_DIR_NAME = "CrashLog";

    /**
     * @param context
     */
    public CrashReporter(Context context) {
        mStartAppTime = System.currentTimeMillis();
        mStartMem = getApplicationMem(context);
        mDfltExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mContext = context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= 11) {
            String obbDir = getObbDir(context);
            final String dir = TextUtils.isEmpty(obbDir) ? SDCARD : obbDir;
            mCrashFilePath = dir + File.separator + LOG_DIR_NAME + File.separator;
        } else {
            mCrashFilePath = Environment.getExternalStorageDirectory() + File.separator + LOG_DIR_NAME + File.separator;
        }
    }

    /**
     * Generates the string which is posted in the single custom data field in
     * the GoogleDocs Form.
     *
     * @return A string with a 'key = value' pair on each line.
     */
    private String createCustomInfoString() {
        String customInfo = "";
        Iterator<String> iterator = mCustomParameters.keySet().iterator();
        while (iterator.hasNext()) {
            String currentKey = iterator.next();
            String currentVal = mCustomParameters.get(currentKey);
            customInfo += currentKey + " = " + currentVal + "\n";
        }
        return customInfo;
    }

    /**
     * Calculates the free memory of the device. This is based on an inspection
     * of the filesystem, which in android devices is stored in RAM.
     *
     * @return Number of bytes available.
     */
    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    /**
     * Calculates the total memory of the device. This is based on an inspection
     * of the filesystem, which in android devices is stored in RAM.
     *
     * @return Total number of bytes.
     */
    public static long getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    /**
     * Collects crash data.
     *
     * @param context The application context.
     */
    private void retrieveCrashData(Context context) {
        try {
            mCrashProperties.clear();
            PackageManager pm = context.getPackageManager();
            PackageInfo pi;
            pi = pm.getPackageInfo(context.getPackageName(), 0);
            if (pi != null) {
                // Application Version
                mCrashProperties.put(VERSION_NAME_KEY, pi.versionName != null ? pi.versionName : "not set");
            } else {
                // Could not retrieve package info...
                mCrashProperties.put(PACKAGE_NAME_KEY, "Package info unavailable");
            }
            // Application Package name
            mCrashProperties.put(PACKAGE_NAME_KEY, context.getPackageName());
            // Device model
            mCrashProperties.put(PHONE_MODEL_KEY, Build.MODEL);
            // Android version
            mCrashProperties.put(ANDROID_VERSION_KEY, Build.VERSION.RELEASE);
//            mCrashProperties.putAll(DyManager.peeepDyMsg(context));
            // Android build data
            mCrashProperties.put(BOARD_KEY, Build.BOARD);
            mCrashProperties.put(BRAND_KEY, Build.BRAND);
            mCrashProperties.put(DEVICE_KEY, Build.DEVICE);
            mCrashProperties.put(DISPLAY_KEY, Build.DISPLAY);
            mCrashProperties.put(FINGERPRINT_KEY, Build.FINGERPRINT);
//            mCrashProperties.put(HOST_KEY, android.os.Build.HOST);
//            mCrashProperties.put(ID_KEY, android.os.Build.ID);
            mCrashProperties.put(MODEL_KEY, Build.MODEL);
            mCrashProperties.put(PRODUCT_KEY, Build.PRODUCT);
            mCrashProperties.put(TAGS_KEY, Build.TAGS);
            mCrashProperties.put(TIME_KEY, "" + Build.TIME);
            mCrashProperties.put(TYPE_KEY, Build.TYPE);
//            mCrashProperties.put(USER_KEY, android.os.Build.USER);
            mCrashProperties.put(START_APP_TIME, time2String(mStartAppTime, "yyyy/MM/dd HH-mm-ss"));
            mCrashProperties.put(CRASH_APP_TIME, time2String(System.currentTimeMillis(), "yyyy/MM/dd HH-mm-ss"));
            // Device Memory
            mCrashProperties.put(TOTAL_MEM_SIZE_KEY, "" + getTotalInternalMemorySize());
            mCrashProperties.put(AVAILABLE_MEM_SIZE_KEY, "" + getAvailableInternalMemorySize());
            mCrashProperties.put(VERSION_CODE, getVersionCode(context));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang
     * .Thread, java.lang.Throwable)
     */
    public void uncaughtException(Thread t, Throwable e) {
        try {
            disable();
            handleNotificationException(e);
        } catch (Exception err) {
        }


        if (mReportingInteractionMode == ReportingInteractionMode.TOAST) {
            try {
                // Wait a bit to let the user read the toast
                Thread.sleep(4000);
            } catch (InterruptedException e1) {
            }
        }

        if (mReportingInteractionMode == ReportingInteractionMode.SILENT) {
            // If using silent mode, let the system default handler do it's job
            // and display the force close diaLoger.
            mDfltExceptionHandler.uncaughtException(t, e);
        } else {
            // If ACRA handles user notifications whit a Toast or a Notification
            // the Force Close dialog is one more notification to the user...
            // We choose to close the process ourselves using the same actions.
            //CharSequence appName = "Application";
            try {
                // PackageManager pm = mContext.getPackageManager();
                // appName = pm.getApplicationInfo(mContext.getPackageName(), 0)
                //        .loadLabel(mContext.getPackageManager());
                //} catch (NameNotFoundException e2) {
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    }

    /**
     * Try to send a report, if an error occurs stores a report file for a later
     * attempt. You can set the {@link ReportingInteractionMode} for this
     * specific report. Use {@link #handleException(Throwable)} to use the
     * Application default interaction mode.
     *
     * @param e                        The Throwable to be reported. If null the report will contain
     *                                 a new Exception("Report requested by developer").
     * @param reportingInteractionMode The desired interaction mode.
     */
    void handleException(Throwable e,
                         ReportingInteractionMode reportingInteractionMode) {
        if (reportingInteractionMode == null) {
            reportingInteractionMode = mReportingInteractionMode;
        }

        if (e == null) {
            e = new Exception("Report requested by developer");
        }

        if (reportingInteractionMode == ReportingInteractionMode.TOAST) {
            new Thread() {

                /*
                 * (non-Javadoc)
                 *
                 * @see java.lang.Thread#run()
                 */
                @Override
                public void run() {
                    Looper.prepare();
                    Toast.makeText(
                            mContext,
                            "catch",
                            Toast.LENGTH_LONG).show();
                    Looper.loop();
                }

            }.start();
        }
        retrieveCrashData(mContext);
        // TODO: add a field in the googledoc form for the crash date.
        // Date CurDate = new Date();
        // Report += "Error Report collected on : " + CurDate.toString();

        // Add custom info, they are all stored in a single field
        mCrashProperties.put(CUSTOM_DATA_KEY, createCustomInfoString());

        // Build stack trace
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        printWriter.append(e.getMessage());
        e.printStackTrace(printWriter);

        e.printStackTrace();
//        Loger.getStackTraceString(e);
        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        Throwable cause = e.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        mCrashProperties.put(STACK_TRACE_KEY, result.toString());

        printWriter.close();

        // Always write the report file
        File file = saveCrashReportFile();

        if (reportingInteractionMode == ReportingInteractionMode.SILENT
                || reportingInteractionMode == ReportingInteractionMode.TOAST) {
            // Send reports now
            //上报服务器后台
            checkAndSendReports(mContext, null);
        } else if (reportingInteractionMode == ReportingInteractionMode.NOTIFICATION) {
            // Send reports when user accepts
            notifySendReport(file);
        }

    }

    /**
     * Send a report for this Throwable.
     *
     * @param e The Throwable to be reported. If null the report will contain
     *          a new Exception("Report requested by developer").
     */
    public void handleException(Throwable e) {
        handleToastException(e);
    }


    public void handleSilentException(Throwable e) {
        mCrashProperties.put(REPORT_MODE, "silent");
        handleException(e, ReportingInteractionMode.SILENT);
    }

    public void handleToastException(Throwable e) {
        mCrashProperties.put(REPORT_MODE, "toast");
        handleException(e, ReportingInteractionMode.TOAST);
    }

    public void handleNotificationException(Throwable e) {
        mCrashProperties.put(REPORT_MODE, "notif");
        handleException(e, ReportingInteractionMode.NOTIFICATION);
    }

    /**
     * Send a status bar notification. The action triggered when the
     * notification is selected is to start the {@link CrashReportActivity}
     * Activity.
     */
    void notifySendReport(File file) {
        NotificationManager notificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);

        int icon = android.R.drawable.stat_notify_error;
        if (mCrashResources
                .containsKey(RES_NOTIF_ICON)) {
            // Use a developer defined icon if available
            icon = mCrashResources
                    .getInt(RES_NOTIF_ICON);
        }

//        CharSequence tickerText = "意外错误，保存报告\n" + file.getName();
//        long when = System.currentTimeMillis();
        CharSequence contentTitle = "意外错误";
        CharSequence contentText = "点击查看错误报告";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
        builder.setSmallIcon(icon);
        builder.setContentTitle(contentTitle);
        builder.setContentText(contentText);

        Intent notificationIntent = new Intent(mContext,
                CrashReportActivity.class);
        notificationIntent.putExtra(EXTRA_REPORT_FILE_PATH, file.getPath());

        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 212,
                notificationIntent, 0);
        builder.setContentIntent(contentIntent);
        Notification notification = builder.build();
        notificationManager.notify(NOTIF_CRASH_ID,
                notification);

    }

    /**
     * When a report can't be sent, it is saved here in a file in the root of
     * the application private directory.
     */
    private File saveCrashReportFile() {
        try {
            String timestamp = time2String(System.currentTimeMillis(), "yyyy-MM-dd-HH-mm-ss");
            String mode = mCrashProperties.getProperty(REPORT_MODE);
            String fileName = createSaveFilePath();
            fileName += (TextUtils.isEmpty(mode) ? "stack" : mode) + "-" + timestamp + ".txt";
            File file = new File(fileName);
            FileOutputStream trace = new FileOutputStream(file, true);
            for (Enumeration<?> it = mCrashProperties.propertyNames(); it.hasMoreElements(); ) {
                String name = (String) it.nextElement();
                trace.write(name.getBytes());
                trace.write("=".getBytes());
                String value = mCrashProperties.getProperty(name);
                trace.write(value.getBytes());
                trace.write("\n".getBytes());
            }
            trace.flush();
            trace.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d("", "请检查XML是否配置有读写SD卡权限,如targetSdkVersion在23以上,请提前申请权限");
        return null;
    }

    private void deleteStkFiles() {
        try {
            String[] reportFilesList = getCrashReportFilesList();
            if (reportFilesList != null && reportFilesList.length > MAX_LEFT_FILES) {
                ArrayList<String> sortList = new ArrayList<String>();
                for (String item : reportFilesList) {
                    sortList.add(item);
                }
                Collections.sort(sortList, new Comparator<String>() {
                    @Override
                    public int compare(String left, String right) {
                        int ret = left.compareTo(right);
                        if (ret != 0) {
                            ret = -ret;
                        }
                        return ret;
                    }
                });

                for (int i = MAX_LEFT_FILES; i < sortList.size(); i++) {
                    String curFileName = sortList.get(i);
                    File curFile = new File(createSaveFilePath(),
                            curFileName);
                    curFile.delete();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String createSaveFilePath() {
        File destDir = new File(mCrashFilePath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        return mCrashFilePath;
    }

    /**
     * Returns an array containing the names of available crash report files.
     *
     * @return an array containing the names of available crash report files.
     */
    String[] getCrashReportFilesList() {
        File dir = new File(createSaveFilePath());


        // Filter for ".stacktrace" files
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".stk");
            }
        };
        return dir.list(filter);
    }

    /**
     * 上报服务器
     */
    void checkAndSendReports(Context context, String userCommentReportFileName) {

    }

    /**
     * 邮件给开发者
     */
    private void sendMail(Context context, String file, String body, String stack) {

    }

    /**
     * Set the wanted user interaction mode for sending reports.
     *
     * @param reportingInteractionMode
     */
    void setReportingInteractionMode(
            ReportingInteractionMode reportingInteractionMode) {
        mReportingInteractionMode = reportingInteractionMode;
    }


    /**
     * Delete all report files stored.
     */
    public void deletePendingReports() {
        String[] filesList = getCrashReportFilesList();
        if (filesList != null) {
            for (String fileName : filesList) {
                new File(mContext.getFilesDir(), fileName).delete();
            }
        }
    }

    public void disable() {
        if (mDfltExceptionHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(mDfltExceptionHandler);
        }
    }

    public Bundle crashResources() {
        return mCrashResources;
    }

    public static String time2String(long time, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat("", Locale.SIMPLIFIED_CHINESE);
        sdf.applyPattern(format);
        return sdf.format(time);
    }

    public static Debug.MemoryInfo getApplicationMem(Context context) {
        Debug.MemoryInfo mem = null;
        if (Build.VERSION.SDK_INT >= 5) {
            try {
                ActivityManager activityManager =
                        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                int pids[] = new int[]{android.os.Process.myPid()};

                Class<?> activityClass = Class.forName("android.app.ActivityManager");
                Method method = activityClass.getDeclaredMethod("getProcessMemoryInfo",
                        int[].class);
                Debug.MemoryInfo[] info = (Debug.MemoryInfo[]) method.invoke(activityManager, pids);
//				Debug.MemoryInfo[] info = activityManager.getProcessMemoryInfo(pids);
                mem = info[0];
            } catch (Exception e) {
            }
        }
        return mem;

    }


    public static String getVersionName(Context context) {
        String version = "unknown";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.GET_ACTIVITIES);
            if (info != null) {
                version = "" + info.versionName;
            }
        } catch (Exception e) {
        }
        return version;
    }

    public static String getVersionCode(Context context) {
        String version = "unknown";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (info != null) {
                version = "" + info.versionCode;
            }
        } catch (Exception e) {
        }
        return version;
    }

    /**
     * restart App
     */
    public static void restartApp(Context context) {
//        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, Intent.FLAG_ACTIVITY_NEW_TASK);
//        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 800, pendingIntent); // 1秒钟后重启应用

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        context.startActivity(launchIntent);
    }


    private static final String SDCARD = Environment.getExternalStorageDirectory().getPath();
    private static String sOBB_DIR = null;

    private static String getObbDir(Context context) {
        if (TextUtils.isEmpty(sOBB_DIR) && Build.VERSION.SDK_INT >= 11) {
            File obbFile = null;
            try {
                obbFile = context.getApplicationContext().getObbDir();
            } catch (Throwable thr) {
                thr.printStackTrace();
            }
            sOBB_DIR = obbFile != null ? obbFile.getAbsolutePath() : null;
        }
        return sOBB_DIR;
    }
}