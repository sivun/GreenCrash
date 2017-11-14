package com.sivun.demo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.TextView
import com.sivun.greencrath.CrashReporter

/**
 * Created by xiexingwu on 2017/11/14.
 */
open class DemoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.demo_main)
        findViewById<TextView>(R.id.bt).setOnClickListener {
            var i = 0
            var k = 13 / i
        }
    }
}

open class DemodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter(this).crashResources()
                .putInt(CrashReporter.RES_NOTIF_ICON, R.drawable.ic_launcher)
    }
}