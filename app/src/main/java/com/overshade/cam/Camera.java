package com.overshade.cam;

import static java.lang.Thread.sleep;

import android.content.ContentResolver;
import android.content.ContentValues;
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
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Rational;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
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
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 */
public class Camera extends Fragment {
    //Interface
    TextureView          txView;
    AppCompatImageButton photoButton;
    AppCompatImageButton flipButton;
    AppCompatImageButton flashButton;
    AppCompatImageButton galleryButton;
    //Camera config
    Preview              preview;
    ImageCapture         imgCap;
    ImageCaptureConfig   imgCapConfig;
    ImageAnalysis        analysis;
    Size                 screen;
    Rational             asp;
    FlashMode            flMode;
    CameraX.LensFacing   lensFacing;
    boolean              torchMode;
    //Screen fx
    ConstraintLayout     cameraTook;
    ConstraintLayout     frontFlash;


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
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        // setting fragment in fullscreen
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        int currentApiVersion = Build.VERSION.SDK_INT;
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (currentApiVersion >= Build.VERSION_CODES.KITKAT) {
            requireActivity().getWindow().getDecorView()
                    .setSystemUiVisibility(flags);
            final View decorView = getActivity().getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(flags);
                }
            });
        }
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_camera, container,
                false);
        //Assign here your view variables
        txView = view.findViewById(R.id.view_finder);
        photoButton = view.findViewById(R.id.capture_button);
        flipButton = view.findViewById(R.id.flip_button);
        flashButton = view.findViewById(R.id.flash_button);

        flipButton.setOnClickListener(v -> {
            new ButtonRotateAnimation
                    (flipButton, 180f, 100).start();
            if (lensFacing == CameraX.LensFacing.FRONT) {
                lensFacing = CameraX.LensFacing.BACK;
                if (torchMode) {
                    // if the torch is on, turn the camera flash on
                    setCameraFlash(true);
                } else {
                    setCameraFlash(false);
                }
            } else {
                lensFacing = CameraX.LensFacing.FRONT;
                if (torchMode) {
                    // if the torch is on, turn camera flash off
                    flMode = FlashMode.OFF;
                    //And enable the front flash
                } else {
                    flMode = FlashMode.OFF;
                }
            }
            startCamera();
        });

        flashButton.setOnClickListener(view1 -> {
            torchMode = !torchMode;
            flashButton.setBackgroundResource((torchMode) ?
                    R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            if (lensFacing == CameraX.LensFacing.BACK) {
                if (torchMode) {
                    setCameraFlash(true);
                } else {
                    setCameraFlash(false);
                }
            } else {
                flMode = FlashMode.OFF;
            }

        });


        galleryButton = view.findViewById(R.id.gallery_button);

        cameraTook = view.findViewById(R.id.camera_took);
        frontFlash = view.findViewById(R.id.frontal_flash);

        //Camera presets config
        lensFacing = CameraX.LensFacing.BACK;
        torchMode = false;
        flMode = FlashMode.OFF;

        //Start camera
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

        asp = aspectRatio(aspRatioW, aspRatioH); //aspect ratio
        screen = new Size(aspRatioW, aspRatioH); //size of the screen

        //Config obj for preview/viewfinder thingy.
        PreviewConfig pConfig = new PreviewConfig.Builder()
                .setTargetAspectRatio(asp)
                .setTargetRotation(requireActivity()
                        .getWindowManager().getDefaultDisplay().getRotation())
                .setLensFacing(lensFacing)
                .setTargetResolution(screen)
                .build();
        preview = new Preview(pConfig); //lets build it

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
        imgCapConfig = new ImageCaptureConfig.Builder()
                .setLensFacing(lensFacing)
                .setFlashMode(flMode)
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .setTargetRotation(requireActivity().getWindowManager()
                        .getDefaultDisplay().getRotation())
                .setTargetAspectRatio(asp)
                .setTargetResolution(screen)
                .build();

        imgCap = new ImageCapture(imgCapConfig);

        photoButton.setOnClickListener(v -> {
            new ButtonScaleAnimation(photoButton,
                    100, 100).start();
            if (torchMode && lensFacing == CameraX.LensFacing.FRONT) {
                //Set the flash countdown and take photo
                takeFrontFlashPhoto();
            } else {
                takePhoto();
            }
        });


        /* Image analyser */

        ImageAnalysisConfig imgAConfig = new ImageAnalysisConfig.Builder()
                .setImageReaderMode
                        (ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setLensFacing(lensFacing)
                .build();
        analysis = new ImageAnalysis(imgAConfig);
        analysis.setAnalyzer(
                (image, rotationDegrees) -> {
                    //y'all can add code to analyse stuff here idek go wild.
                });

        //bind to lifecycle:
        CameraX.bindToLifecycle(this, analysis, imgCap, preview);
    }

    private void takePhoto() {
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
                preview.enableTorch(false);
                makeTookPhotoFx();
                frontFlash.setVisibility(View.GONE);
                //String msg = "Photo capture succeeded: "
                //        + file.getAbsolutePath();
                //Toast.makeText(requireActivity().getBaseContext(), msg,
                //        Toast.LENGTH_LONG).show();

                //different rotation depending of lensFcing
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
                frontFlash.setVisibility(View.GONE);
                String msg = "Photo capture failed: " + message;
                Toast.makeText(requireActivity().getBaseContext(), msg,
                        Toast.LENGTH_LONG).show();
                if (cause != null) {
                    cause.printStackTrace();
                }
            }
        });
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
                bm.getWidth() / 2f, bm.getHeight() / 2f);
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
            //Toast.makeText(requireActivity(), "and flipped successfully",
            //        Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            //Toast.makeText(requireActivity(), "but it cannot be flipped",
            //        Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

    }


    private void loadGalleryButton() {
        File dir = new File(Environment.getExternalStorageDirectory() +
                File.separator + "Cam" + File.separator);
        // start loop trough files in directory
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null && files.length > 0) {
                Uri uri = Uri.fromFile(files[files.length - 1]);
                loadImage(uri);
            }
        }
    }

    private void loadImage(Uri uri) {
        Glide.with(this)
                .asBitmap()
                .load(uri) // image url
                .placeholder(R.drawable.ic_image) // any placeholder to load at start
                .error(R.drawable.ic_image)  // any image in case of error
                .override(200, 200) // resizing
                .transform(new CenterCrop(), new RoundedCorners(35))
                .into(galleryButton);  // imageview object
    }

    private void makeTookPhotoFx() {
        requireActivity().runOnUiThread(() -> {
            cameraTook.setVisibility(View.VISIBLE);

            Handler h = new Handler();
            h.postDelayed(() -> {
                cameraTook.setVisibility(View.GONE);
            }, 100);

        });
    }

    private void setCameraFlash(boolean activated) {
        if (activated) {
            flMode = FlashMode.ON;
        } else {
            flMode = FlashMode.OFF;
        }
        imgCapConfig = new ImageCaptureConfig.Builder()
                .setLensFacing(lensFacing)
                .setFlashMode(flMode)
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .setTargetRotation(requireActivity().getWindowManager()
                        .getDefaultDisplay().getRotation())
                .setTargetAspectRatio(asp)
                .setTargetResolution(screen)
                .build();
        CameraX.unbind(imgCap);
        imgCap = new ImageCapture(imgCapConfig);
        CameraX.bindToLifecycle(this, imgCap);
    }

    private void takeFrontFlashPhoto() {
        AlphaAnimation flashAnim = new AlphaAnimation(0f,1f);
        flashAnim.setDuration(1500);
        requireActivity().runOnUiThread(() -> {
            frontFlash.setVisibility(View.VISIBLE);
            frontFlash.startAnimation(flashAnim);

            Handler h = new Handler();
            h.postDelayed(() -> {
                takePhoto();
            }, 2000);

        });
    }

}