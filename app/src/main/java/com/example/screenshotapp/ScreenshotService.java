package com.example.screenshotapp;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ToneGenerator;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.BuildConfig;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;


public class ScreenshotService extends Service implements View.OnClickListener{

    private WindowManager mWindowManager;
    private View infoView;

    private static final String CHANNEL_WHATEVER="channel_whatever";
    private static final int NOTIFY_ID=9906;
    static final String EXTRA_RESULT_CODE="resultCode";
    static final String EXTRA_RESULT_INTENT="resultIntent";
    static final String ACTION_RECORD=
            BuildConfig.APPLICATION_ID+".RECORD";
    static final String ACTION_SHUTDOWN=
            BuildConfig.APPLICATION_ID+".SHUTDOWN";
    static final int VIRT_DISPLAY_FLAGS=
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private MediaProjection projection;
    private VirtualDisplay vdisplay;
    final private HandlerThread handlerThread=
            new HandlerThread(getClass().getSimpleName(),
                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Handler handler;
    private MediaProjectionManager mgr;

    private ImageTransmogrifier imageTrans;
    private int resultCode;
    private Intent resultData;
    final private ToneGenerator beeper=
            new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);


    private View stepView;
    private View stepCollapsedView;
    private View stepExpandedView;

    final WindowManager.LayoutParams stepIconParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

    @Override
    public void onCreate() {
        super.onCreate();

        mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        mWindowManager=(WindowManager)getSystemService(WINDOW_SERVICE);

        handlerThread.start();
        handler=new Handler(handlerThread.getLooper());
        final Context context = this;

        //====================================info (screenshot) icon ==================================================
        infoView = LayoutInflater.from(context).inflate(R.layout.screenshot_layout, null);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;

        WindowManager.LayoutParams infoIconParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        //Specify the info icon position
        infoIconParams.gravity = Gravity.TOP | Gravity.LEFT;
        infoIconParams.x = width -100;
        infoIconParams.y = height-300;

        mWindowManager.addView(infoView, infoIconParams);

        //adding click listener to close button and expanded view
        infoView.findViewById(R.id.close).setOnClickListener(this);
//        expandedView.setOnClickListener(this);
        infoView.findViewById(R.id.app_info).setOnClickListener(this);

        //=================================================step icon======================================
        stepView = LayoutInflater.from(this).inflate(R.layout.step_widget_layout, null);
        stepCollapsedView = stepView.findViewById(R.id.layoutCollapsed);
        stepExpandedView = stepView.findViewById(R.id.layoutExpanded);

        stepIconParams.gravity = Gravity.TOP | Gravity.LEFT;

        stepView.findViewById(R.id.close_btn).setOnClickListener(this);
        stepExpandedView.setOnClickListener(this);

        //adding an touchlistener to make drag movement of the floating widget
        stepView.findViewById(R.id.relativeLayoutParent).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = stepIconParams.x;
                        initialY = stepIconParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        //when the drag is ended switching the state of the widget
                        stepCollapsedView.setVisibility(View.GONE);
                        stepExpandedView.setVisibility(View.VISIBLE);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        //this code is helping the widget to move around the screen with fingers
                        stepIconParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        stepIconParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        System.out.println(stepIconParams.x+ ",  "+ stepIconParams.y);
                        mWindowManager.updateViewLayout(stepView, stepIconParams);
                        return true;
                }
                return false;
            }
        });

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.layoutExpanded:
                //switching views
                stepCollapsedView.setVisibility(View.VISIBLE);
                stepExpandedView.setVisibility(View.GONE);
                break;

            case R.id.close_btn:
            case R.id.close:
                //closing the widget
                stopSelf();
                break;

            case R.id.app_info:
                startCapture();
                if(stepView.getWindowToken() != null){
                    mWindowManager.removeView(stepView);
                }
                mWindowManager.addView(stepView, stepIconParams);
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (infoView != null)
            mWindowManager.removeView(infoView);
        if (stepView != null)
            mWindowManager.removeView(stepView);
        stopCapture();
        imageTrans.close();

    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (i.getAction()==null) {
            resultCode=i.getIntExtra(EXTRA_RESULT_CODE, 1337);
            resultData=i.getParcelableExtra(EXTRA_RESULT_INTENT);
            foregroundify();
        }
        else if (ACTION_RECORD.equals(i.getAction())) {
            if (resultData!=null) {
//                System.out.println("******start capture*****");
//                startCapture();

            }
            else {
                Intent ui=
                        new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                startActivity(ui);
            }
        }
        else if (ACTION_SHUTDOWN.equals(i.getAction())) {
            beeper.startTone(ToneGenerator.TONE_PROP_NACK);
            stopForeground(true);
            stopSelf();
        }

        return(START_NOT_STICKY);
    }



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new IllegalStateException("Binding not supported. Go away.");
    }

    WindowManager getWindowManager() {
        return(mWindowManager);
    }

    Handler getHandler() {
        return(handler);
    }

    void processImage(final byte[] png) {
        beeper.startTone(ToneGenerator.TONE_PROP_ACK);
        stopCapture();

    }

    private void stopCapture() {
        if (projection!=null) {
            projection.stop();
            vdisplay.release();
            projection=null;
        }
    }

    private void startCapture() {
        projection=mgr.getMediaProjection(resultCode, resultData);

        imageTrans = new ImageTransmogrifier(this);
        vdisplay = projection.createVirtualDisplay("screenshot",
                imageTrans.getWidth(), imageTrans.getHeight(),
                getResources().getDisplayMetrics().densityDpi,
                VIRT_DISPLAY_FLAGS, imageTrans.getSurface(), null, handler);

        MediaProjection.Callback cb=new MediaProjection.Callback() {
            @Override
            public void onStop() {
                vdisplay.release();
            }
        };

        projection.registerCallback(cb, handler);

    }


    private void foregroundify() {
        NotificationManager mgr=
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O &&
                mgr.getNotificationChannel(CHANNEL_WHATEVER)==null) {
            mgr.createNotificationChannel(new NotificationChannel(CHANNEL_WHATEVER,
                    "Default", NotificationManager.IMPORTANCE_DEFAULT));
        }

        NotificationCompat.Builder b=
                new NotificationCompat.Builder(this, CHANNEL_WHATEVER);

        b.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);

        b.setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(getString(R.string.app_name));

        b.addAction(R.drawable.ic_record_white_24dp, "notify_record",
                buildPendingIntent(ACTION_RECORD));

        b.addAction(R.drawable.ic_eject_white_24dp,
                "notify_shutdown",
                buildPendingIntent(ACTION_SHUTDOWN));

        startForeground(NOTIFY_ID, b.build());
    }

    private PendingIntent buildPendingIntent(String action) {
        Intent i=new Intent(this, getClass());

        i.setAction(action);

        return(PendingIntent.getService(this, 0, i, 0));
    }

    void updateIconPosition(int x, int y){
        System.out.println("update position:" + x +","+ y);
        stepIconParams.x =  x *2;
        stepIconParams.y = y*2 -100 ;
        mWindowManager.updateViewLayout(stepView, stepIconParams);
    }



    public class ImageTransmogrifier implements ImageReader.OnImageAvailableListener {
        private final int width;
        private final int height;
        private final ImageReader imageReader;
        private final ScreenshotService svc;
        private Bitmap latestBitmap=null;


        ImageTransmogrifier(ScreenshotService svc) {
            this.svc=svc;

            Display display=svc.getWindowManager().getDefaultDisplay();
            Point size=new Point();

            display.getRealSize(size);

            int width=size.x;
            int height=size.y;

            while (width*height > (2<<19)) {
                width=width>>1;
                height=height>>1;
            }

            this.width=width;
            this.height=height;

            imageReader=ImageReader.newInstance(width, height,
                    PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(this, svc.getHandler());
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            final Image image=imageReader.acquireLatestImage();

            if (image!=null) {
                Image.Plane[] planes=image.getPlanes();
                ByteBuffer buffer=planes[0].getBuffer();
                int pixelStride=planes[0].getPixelStride();
                int rowStride=planes[0].getRowStride();
                int rowPadding=rowStride - pixelStride * width;
                int bitmapWidth=width + rowPadding / pixelStride;

                if (latestBitmap == null ||
                        latestBitmap.getWidth() != bitmapWidth ||
                        latestBitmap.getHeight() != height) {
                    if (latestBitmap != null) {
                        latestBitmap.recycle();
                    }

                    latestBitmap=Bitmap.createBitmap(bitmapWidth,
                            height, Bitmap.Config.ARGB_8888);

                    processText();

                }

                latestBitmap.copyPixelsFromBuffer(buffer);
                image.close();

                ByteArrayOutputStream baos=new ByteArrayOutputStream();
                Bitmap cropped=Bitmap.createBitmap(latestBitmap, 0, 0,
                        width, height);

                cropped.compress(Bitmap.CompressFormat.PNG, 100, baos);

                byte[] newPng=baos.toByteArray();

                svc.processImage(newPng);
            }
        }

        private void processText() {

            final TextView textStep = stepView.findViewById(R.id.step);
            final TextView textExpandedStep = stepView.findViewById(R.id.stepExpanded);
            FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(latestBitmap);
            FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
                    .getOnDeviceTextRecognizer();

            Task<FirebaseVisionText> result =
                    detector.processImage(firebaseVisionImage)
                            .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                                @Override
                                public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                    String resultText = firebaseVisionText.getText();

                                    for (FirebaseVisionText.TextBlock block : firebaseVisionText.getTextBlocks()) {
                                        String blockText = block.getText();
                                        Rect blockFrame = block.getBoundingBox();

                                        for (FirebaseVisionText.Line line : block.getLines()) {
                                            String lineText = line.getText();
//                                        Float lineConfidence = line.getConfidence();
//                                        List<RecognizedLanguage> lineLanguages = line.getRecognizedLanguages();
                                            Point[] lineCornerPoints = line.getCornerPoints();
                                            Rect lineFrame = line.getBoundingBox();
                                            System.out.println(lineText + " - pos:(" + lineFrame.right + "," +lineFrame.top + ")");
                                            if (lineText.equalsIgnoreCase("my posts")) {
                                                updateIconPosition(Math.round(lineFrame.right), Math.round(lineFrame.top));
                                                textStep.setText("my posts");
                                                textExpandedStep.setText("my posts");
                                            }
                                            else if (lineText.equalsIgnoreCase("wechat out")) {
                                                updateIconPosition(Math.round(lineFrame.right), Math.round(lineFrame.top));
                                                textStep.setText("wechat out");
                                                textExpandedStep.setText("wechat out");
                                            }
                                            else if (lineText.equalsIgnoreCase("new friends")) {
                                                updateIconPosition(Math.round(lineFrame.right), Math.round(lineFrame.top));
                                                textStep.setText("new friends");
                                                textExpandedStep.setText("new friends");
                                            }
                                            for (FirebaseVisionText.Element element : line.getElements()) {
                                                String elementText = element.getText();
                                                Rect elementFrame = element.getBoundingBox();
//                                                    System.out.println(elementText+" - pos:("+elementFrame.centerX()+","+elementFrame.centerY()+")");
                                            }
                                        }
                                    }
                                }
                            })
                            .addOnFailureListener(
                                    new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            // Task failed with an exception
                                            // ...
                                        }
                                    });
        }

        Surface getSurface() {
            return(imageReader.getSurface());
        }

        int getWidth() {
            return(width);
        }

        int getHeight() {
            return(height);
        }

        void close() {
            imageReader.close();
        }
    }
}
