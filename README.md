# GreenCrash
面向开发者的奔溃报告，以便开发者非常容易清除奔溃原因，更让你集中精力在开发中，而非查找报错原因。你也可以在上面做修改，把报告上传到你的服务器，以便后续优化修复BUG。

# 接入
  1.本地引入aar包：[greencrath-release.aar下载](https://github.com/sivun/GreenCrash/blob/master/greencrath-release.aar)
  
  2.在你的接入module的 `AndroidManifest.xml `中确保添加权限
  ` <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
`
  3.在您的主module的 `AndroidManifest.xml `中添加activity（用于显示crash异常log日志）
   `<activity
            android:name="com.sivun.greencrath.CrashReportActivity"
            android:screenOrientation="portrait" />`
            
  4.在您的主module对应的 `Application `的 `onCreate `方法中添加初始化代码
  ` new CrashReporter(this).crashResources()
                .putInt(CrashReporter.RES_NOTIF_ICON, R.drawable.ic_launcher)`
   原则上，在任意地方添加都可以，只需要保证在抛出异常前运行该代码即可
 
 # 注意：
   本库的log是存储在SD卡或者内置卡中，目录是程序对应的obb目录，故卸载应用的时候，会跟随应用一起被移除。如需存于其他目录，可以看代码自行修改，但要考虑SD卡存储权限的问题！
