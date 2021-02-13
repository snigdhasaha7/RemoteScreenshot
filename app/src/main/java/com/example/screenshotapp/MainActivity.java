package com.example.screenshotapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;

import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;

import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    int view = R.layout.activity_main;
    public final static int REQUEST_CODE = 10;
    private static final int REQUEST_SCREENSHOT=59706;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(view);
        requestPermission();

        //use for drawing overlay and screenshot
        checkDrawOverlayPermission(this);
        MediaProjectionManager mgr =(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(),
                REQUEST_SCREENSHOT);
    }

    private void requestPermission(){
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.FOREGROUND_SERVICE

                )
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            // do you work now
                        }
                        for (int i=0;i<report.getDeniedPermissionResponses().size();i++) {
                            Log.d("dennial permision res", report.getDeniedPermissionResponses().get(i).getPermissionName());
                        }
                        // check for permanent denial of any permission
                        if (report.isAnyPermissionPermanentlyDenied()) {
                            // permission is denied permenantly, navigate user to app settings
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", getPackageName(), null));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    }
                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token)
                    {
                        token.continuePermissionRequest();
                    }
                }).check();



    }

    public void checkDrawOverlayPermission(Context context) {
        /** check if we already  have permission to draw over other apps */
        if (!Settings.canDrawOverlays(context)) {
            /** if not construct intent to request permission */
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            /** request permission via start activity for result */
            startActivityForResult(intent, REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent data) {
        /** check if received result code
         is equal our requested code for draw permission  */
        if (requestCode == REQUEST_CODE) {
       /** if so check once again if we have permission */
            if (Settings.canDrawOverlays(this)) {
                Log.v("App", "Requesting Permission" + Settings.canDrawOverlays(this));
            }
            Log.v("App", "Requesting Permission" + Settings.canDrawOverlays(this));
        }
        else if (requestCode==REQUEST_SCREENSHOT) {
            if (resultCode==RESULT_OK) {
                Intent screenshotService= new Intent(this, ScreenshotService.class)
                                .putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
                                .putExtra(ScreenshotService.EXTRA_RESULT_INTENT, data);

                //launch to Wechat ( com.tencent.mm)
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.tencent.mm");

                if(launchIntent != null)
                    startActivity(launchIntent);
                startService(screenshotService);
            }
        }

        finish();
    }

}


//
//    public void screenShot(final View view) {
//        final Paint p = new Paint();
//        p.setStyle(Paint.Style.STROKE);
//        p.setAntiAlias(true);
//        p.setFilterBitmap(true);
//        p.setDither(true);
//        p.setColor(Color.BLACK);
//        p.setStrokeWidth(10);
//
//        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(),
//                view.getHeight(), Bitmap.Config.ARGB_8888);
//        final Canvas canvas = new Canvas(bitmap);
//        view.draw(canvas);
//        screenShot.setImageBitmap(bitmap);
//        textView.setText("click");
//
//        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
//        FirebaseApp.initializeApp(this);
//        FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
//                .getOnDeviceTextRecognizer();
//
//        Task<FirebaseVisionText> result =
//                detector.processImage(image)
//                        .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
//                            @Override
//                            public void onSuccess(FirebaseVisionText firebaseVisionText) {
//                                String resultText = firebaseVisionText.getText();
//                                for (FirebaseVisionText.TextBlock block: firebaseVisionText.getTextBlocks()) {
//                                    String blockText = block.getText();
//                                    System.out.println("************"+blockText+"************");
//                                    Float blockConfidence = block.getConfidence();
//                                    List<RecognizedLanguage> blockLanguages = block.getRecognizedLanguages();
//                                    Point[] blockCornerPoints = block.getCornerPoints();
//                                    Rect blockFrame = block.getBoundingBox();
//                                    System.out.println("************"+blockFrame.centerX()+","+blockFrame.centerY()+"************");
//                                    for (FirebaseVisionText.Line line: block.getLines()) {
//                                        String lineText = line.getText();
//                                        Float lineConfidence = line.getConfidence();
//                                        List<RecognizedLanguage> lineLanguages = line.getRecognizedLanguages();
//                                        Point[] lineCornerPoints = line.getCornerPoints();
//                                        Rect lineFrame = line.getBoundingBox();
//                                        canvas.drawRect(lineFrame,p);
//                                        view.draw(canvas);
//                                        for (FirebaseVisionText.Element element: line.getElements()) {
//                                            String elementText = element.getText();
//                                            Float elementConfidence = element.getConfidence();
//                                            List<RecognizedLanguage> elementLanguages = element.getRecognizedLanguages();
//                                            Point[] elementCornerPoints = element.getCornerPoints();
//                                            Rect elementFrame = element.getBoundingBox();
//                                            System.out.println("*****"+elementText+" - "+elementFrame.centerX()+","+elementFrame.centerY());
//                                        }
//                                    }
//                                }
//                            }
//                        })
//                        .addOnFailureListener(
//                                new OnFailureListener() {
//                                    @Override
//                                    public void onFailure(@NonNull Exception e) {
//                                        // Task failed with an exception
//                                        // ...
//                                    }
//                                });
//
//
//
//    }



