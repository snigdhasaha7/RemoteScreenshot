1 -  In AndroidManifest.xml
-  Add Android permission :

android.permission.READ_EXTERNAL_STORAGE (Media Projection - screenshot)

android.permission.WRITE_EXTERNAL_STORAGE (Media Projection - screenshot)

android.permission.SYSTEM_ALERT_WINDOW (floating widget " overlay")

android.permission.FOREGROUND_SERVICE (Media Projection - screenshot)

Inside of application block

- add

           <service
             android:name=".ScreenshotService"
             android:exported="true"
             android:foregroundServiceType="mediaProjection"/>

           <meta-data
             android:name="com.google.firebase.ml.vision.DEPENDENCIES"
             android:value="ocr"/>

2- In build.gradle (App level)
Add  implementation 'com.karumi:dexter:5.0.0'


3 - MainActivity.java

- add requestPermission() for requesting android permission
- add checkDrawOverlayPermission(Context context)
- start activity for ScreenCaptureIntent
- In activityResult(), start a service and launch to Wechat

4 - create step_widget_layout.xml

5- create screenshot_layout.xml

6 - create ScreenshotSevice.java

- I used this tutorial to draw an overlay https://www.simplifiedcoding.net/android-floating-widget-tutorial/
- when the info icon is pressed the step icon is created


7 - Create Firebase Project (Follow instruction for Android platform)
