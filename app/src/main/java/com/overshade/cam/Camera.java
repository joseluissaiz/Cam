package com.overshade.cam;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Rational;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.overshade.cam.WidgetAnimations.ButtonRotateAnimation;
import com.overshade.cam.WidgetAnimations.ButtonScaleAnimation;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A simple {@link Fragment} subclass.
 */
public class Camera extends Fragment {
    //Interface
    TextureView          txView;
    AppCompatImageButton photoButton;
    AppCompatImageButton flipButton;
    AppCompatImageButton galleryButton;
    //Camera config
    CameraX.LensFacing   lensFacing = CameraX.LensFacing.FRONT;


    /* Constructor */

    public Camera() {
        // Required empty public constructor
    }


    /* Starting methods */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_camera, container,
                false);
        //Assign here your view variables
        txView = view.findViewById(R.id.view_finder);
        photoButton = view.findViewById(R.id.capture_button);
        flipButton = view.findViewById(R.id.flip_button);

        flipButton.setOnClickListener(v -> {
            new ButtonRotateAnimation
                    (flipButton, 180f, 100).start();
            if (lensFacing == CameraX.LensFacing.FRONT) {
                lensFacing = CameraX.LensFacing.BACK;
            } else {
                lensFacing = CameraX.LensFacing.FRONT;
            }
            startCamera();
        });

        galleryButton = view.findViewById(R.id.gallery_button);

        startCamera();
        loadGalleryButton();
        return view;
    }


    /* Camera methods */

    public void startCamera() {
        //Make sure there isn't another camera instance running before starting
        CameraX.unbindAll();

        /* Start preview */

        int aspRatioW = txView.getWidth(); //get width of screen
        int aspRatioH = txView.getHeight(); //get height

        Rational asp = aspectRatio(aspRatioW, aspRatioH); //aspect ratio
        Size screen = new Size(aspRatioW, aspRatioH); //size of the screen

        //Config obj for preview/viewfinder thingy.
        PreviewConfig pConfig = new PreviewConfig.Builder()
                .setTargetAspectRatio(asp)
                .setTargetRotation(requireActivity()
                        .getWindowManager().getDefaultDisplay().getRotation())
                .setLensFacing(lensFacing)
                .setTargetResolution(screen)
                .build();
        Preview preview = new Preview(pConfig); //lets build it

        //To update the surface texture we have to destroy it first, then
        // re-add it
        preview.setOnPreviewOutputUpdateListener(
                output -> {
                    ViewGroup parent = (ViewGroup) txView.getParent();
                    parent.removeView(txView);
                    updateTransform();
                    parent.addView(txView, 0);

                    txView.setSurfaceTexture(output.getSurfaceTexture());
                });


        /* Image capture */

        //Config obj, selected capture mode
        ImageCaptureConfig imgCapConfig = new ImageCaptureConfig.Builder()
                .setLensFacing(lensFacing)
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .setTargetRotation(requireActivity().getWindowManager()
                        .getDefaultDisplay().getRotation())
                .setTargetAspectRatio(asp)
                .setTargetResolution(screen)
                .build();
        final ImageCapture imgCap = new ImageCapture(imgCapConfig);

        photoButton.setOnClickListener(v -> {
            new ButtonScaleAnimation(photoButton,
                    100, 100).start();

            SimpleDateFormat sdf =
                    new SimpleDateFormat("yyyyMMdd_HHmmss",
                            Locale.getDefault());
            String fileName = sdf.format(new Date());
            String filePath = Environment.getExternalStorageDirectory() +
                    File.separator + "Cam" + File.separator + fileName +
                    ".jpg";
            File file = new File(filePath);

            imgCap.takePicture(file, new ImageCapture.OnImageSavedListener() {
                @Override
                public void onImageSaved(@NonNull File file) {
                    String msg = "Photo capture succeeded: "
                            + file.getAbsolutePath();
                    Toast.makeText(requireActivity().getBaseContext(), msg,
                            Toast.LENGTH_LONG).show();
                    if (lensFacing == CameraX.LensFacing.FRONT) {
                        flipImage(file);
                    }

                    //Updating android files database
                    Uri imageUri = Uri.parse("file://" + filePath);
                    Intent mediaScanIntent =
                            new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    imageUri);
                    mediaScanIntent.setData(Uri.fromFile(file));
                    requireActivity().sendBroadcast(mediaScanIntent);
                    loadGalleryButton();
                }

                @Override
                public void onError(
                        @NonNull ImageCapture.UseCaseError useCaseError,
                        @NonNull String message, @Nullable Throwable cause) {
                    String msg = "Photo capture failed: " + message;
                    Toast.makeText(requireActivity().getBaseContext(), msg,
                            Toast.LENGTH_LONG).show();
                    if (cause != null) {
                        cause.printStackTrace();
                    }
                }
            });
        });


        /* Image analyser */

        ImageAnalysisConfig imgAConfig = new ImageAnalysisConfig.Builder()
                .setImageReaderMode
                        (ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setLensFacing(lensFacing)
                .build();
        ImageAnalysis analysis = new ImageAnalysis(imgAConfig);
        analysis.setAnalyzer(
                (image, rotationDegrees) -> {
                    //y'all can add code to analyse stuff here idek go wild.
                });

        //bind to lifecycle:
        CameraX.bindToLifecycle(this, analysis, imgCap, preview);
    }

    private void updateTransform() {
        /*
         * compensates the changes in orientation for the viewfinder, bc the
         * rest of the layout stays in portrait mode.
         * methinks :thonk:
         * imgCap does this already, this class can be commented out or be used
         * to optimise the preview
         */
        Matrix mx = new Matrix();
        float w = txView.getMeasuredWidth();
        float h = txView.getMeasuredHeight();

        float centreX = w / 2f; //calc centre of the viewfinder
        float centreY = h / 2f;

        int rotationDgr;
        int rotation = (int) txView.getRotation();
        //cast to int bc switches don't like floats

        switch (rotation) { //correct output to account for display rotation
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, centreX, centreY);
        txView.setTransform(mx); //apply transformations to textureview
    }

    public Rational aspectRatio(int width, int height) {
        double previewRatio =
                (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - (4.0 / 3.0))
                <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return new Rational(3, 4);
        } else {
            return new Rational(9, 16);
        }
    }

    //Fixing selfies orientation and save as showed in preview
    private void flipImage(File file) {
        Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
        // create new matrix for transformation
        Matrix matrix = new Matrix();
        matrix.postScale(-1f, 1f,
                bm.getWidth()/2f, bm.getHeight()/2f);
        matrix.postRotate(90f);
        // bitmap transformed
        Bitmap newBm = Bitmap.createBitmap(bm, 0, 0,
                bm.getWidth(), bm.getHeight(), matrix, true);
        // trying to create a new output and save the photo
        try {
            OutputStream os =
                    new BufferedOutputStream(new FileOutputStream(file));
            newBm.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.close();
            Toast.makeText(requireActivity(), "and flipped successfully",
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(requireActivity(), "but it cannot be flipped",
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

    }


    private void loadGalleryButton() {
        File dir = new File(Environment.getExternalStorageDirectory() +
                File.separator + "Cam" + File.separator);
        // start loop trough files in directory
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                try {
                    Bitmap bm = BitmapFactory.decodeFile(
                            files[files.length-1].getAbsolutePath());
                    Uri uri = Uri.fromFile(files[0]);
                    Context ctxt = requireActivity();
                    Bitmap newBm = rotateImageIfRequired(ctxt, bm, uri);
                    galleryButton.setImageBitmap(newBm);
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }


    //Rotate an image if required.
    private static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage) throws IOException {

        InputStream input = context.getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new ExifInterface(input);
        else
            ei = new ExifInterface(selectedImage.getPath());

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

}