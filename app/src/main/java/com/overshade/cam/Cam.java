package com.overshade.cam;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraXConfig;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.common.util.concurrent.ListenableFuture;
import com.overshade.cam.WidgetAnimations.ButtonRotateAnimation;
import com.overshade.cam.WidgetAnimations.ButtonScaleAnimation;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * A simple {@link Fragment} subclass.
 */
public class Cam extends Fragment {
    //Interface
    PreviewView                             pvView;
    AppCompatImageButton                    photoButton;
    AppCompatImageButton                    flipButton;
    AppCompatImageButton                    flashButton;
    AppCompatImageButton                    galleryButton;
    ImageView                               focusSelector;
    //Camera config
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ProcessCameraProvider                   cameraProvider;
    Preview                                 preview;
    ImageCapture                            imgCap;
    CameraXConfig                           cameraConfig;
    CameraInfo                              cameraInfo;
    CameraControl                           cameraControl;
    ScaleGestureDetector                    gestureDetector;
    boolean                                 isZooming;
    Size                                    screen;
    int                                     asp;
    int                                     flMode;
    CameraSelector                          cameraSelector;
    int                                     lensFacing;
    boolean                                 torchMode;
    //Screen fx
    ConstraintLayout                        cameraTook;
    ConstraintLayout                        frontFlash;


    /* Constructor */

    public Cam() {
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
        pvView = view.findViewById(R.id.view_finder);
        photoButton = view.findViewById(R.id.capture_button);
        flipButton = view.findViewById(R.id.flip_button);
        flashButton = view.findViewById(R.id.flash_button);
        focusSelector = view.findViewById(R.id.focus_selector);

        flipButton.setOnClickListener(v -> {
            new ButtonRotateAnimation
                    (flipButton, 180f, 100).start();
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                lensFacing = CameraSelector.LENS_FACING_BACK;
                if (torchMode) {
                    // if the torch is on, turn the camera flash on
                    setCameraFlash(true);
                } else {
                    setCameraFlash(false);
                }
                cameraInfo = cameraProvider.getAvailableCameraInfos().get(0);
            } else {
                lensFacing = CameraSelector.LENS_FACING_FRONT;
                if (torchMode) {
                    // if the torch is on, turn camera flash off
                    flMode = ImageCapture.FLASH_MODE_OFF;
                    //And enable the front flash
                } else {
                    flMode = ImageCapture.FLASH_MODE_OFF;
                }
                cameraInfo = cameraProvider.getAvailableCameraInfos().get(1);
            }
            startCamera();
        });

        flashButton.setOnClickListener(view1 -> {
            torchMode = !torchMode;
            flashButton.setBackgroundResource((torchMode) ?
                    R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                if (torchMode) {
                    setCameraFlash(true);
                } else {
                    setCameraFlash(false);
                }
            } else {
                flMode = ImageCapture.FLASH_MODE_OFF;
            }

        });


        galleryButton = view.findViewById(R.id.gallery_button);

        cameraTook = view.findViewById(R.id.camera_took);
        frontFlash = view.findViewById(R.id.frontal_flash);

        //Camera presets config
        lensFacing = CameraSelector.LENS_FACING_BACK;
        torchMode = false;
        flMode = ImageCapture.FLASH_MODE_OFF;

        //Start camera
        startCamera();
        loadGalleryButton();
        return view;
    }

    /* Camera methods */

    @SuppressLint("ClickableViewAccessibility")
    public void startCamera() {
        //Make sure there isn't another camera instance running before starting
        cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireActivity());

        cameraProviderFuture.addListener(() -> {
            /* Start preview */

            try {
                cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();

                int aspRatioW = pvView.getWidth(); //get width of screen
                int aspRatioH = pvView.getHeight(); //get height

                asp = aspectRatio(aspRatioW, aspRatioH); //aspect ratio
                screen = new Size(aspRatioW, aspRatioH); //size of the screen

                //Config obj for preview/viewfinder thingy.
                preview = new Preview.Builder()
                        .setTargetRotation(requireActivity()
                                .getWindowManager().getDefaultDisplay().getRotation())
                        .setTargetResolution(screen)
                        .build();


                /* Image capture */

                //Config obj, selected capture mode
                imgCap = new ImageCapture.Builder()
                        .setFlashMode(flMode)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(requireActivity().getWindowManager()
                                .getDefaultDisplay().getRotation())
                        .setTargetAspectRatio(asp)
                        //.setTargetResolution(screen)
                        .build();


                /* Camera selector */
                cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                photoButton.setOnClickListener(v -> {
                    new ButtonScaleAnimation(photoButton,
                            100, 100).start();
                    if (torchMode && lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        //Set the flash countdown and take photo
                        takeFrontFlashPhoto();
                    } else {
                        takePhoto();
                    }
                });

                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    cameraInfo =
                            cameraProvider.getAvailableCameraInfos().get(1);
                } else {
                    cameraInfo =
                            cameraProvider.getAvailableCameraInfos().get(0);

                }

                //bind to lifecycle:
                Camera camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imgCap);

                preview.setSurfaceProvider(pvView.getSurfaceProvider());

                cameraControl = camera.getCameraControl();
                ScaleGestureDetector.SimpleOnScaleGestureListener listener =
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        isZooming = true;
                        System.out.println("calculeddd");
                        float scale = Objects.requireNonNull(
                                cameraInfo.getZoomState().getValue())
                                .getZoomRatio() * detector.getScaleFactor();
                        cameraControl.setZoomRatio(scale);
                        return true;
                    }
                };
                gestureDetector = new ScaleGestureDetector(requireActivity(),
                        listener);

                pvView.setOnTouchListener((view, motionEvent) -> {
                    gestureDetector.onTouchEvent(motionEvent);
                    if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        if (!isZooming) {
                            startFocusAnimation(motionEvent.getX(),
                                    motionEvent.getY());
                            MeteringPointFactory factory =
                                    pvView.getMeteringPointFactory();
                            MeteringPoint point = factory.createPoint(
                                    motionEvent.getX(), motionEvent.getY());
                            FocusMeteringAction action =
                                    new FocusMeteringAction.Builder(point).build();
                            cameraControl.startFocusAndMetering(action);
                        }
                        isZooming = false;
                    }
                    System.out.println("Toching");
                    return true;
                });


            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireActivity()));


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


        imgCap.takePicture(Executors.newSingleThreadExecutor(),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        super.onCaptureSuccess(image);
                        makeTookPhotoFx();

                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        Bitmap bm = BitmapFactory.decodeByteArray(bytes,
                                0, bytes.length);

                        try (FileOutputStream out =
                                     new FileOutputStream(file)) {
                            bm.compress(Bitmap.CompressFormat.JPEG,
                                    100, out);
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                flipImage(file, 90, true, false);
                            } else {
                                flipImage(file, 270, true, true);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        String msg = "Photo capture succeeded: "
                                + file.getAbsolutePath();
                        Looper.prepare();
                        Toast.makeText(requireActivity().getBaseContext(), msg,
                                Toast.LENGTH_LONG).show();

                        //different rotation depending of lensFcing
                        //if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        //    flipImage();
                        //}
                        requireActivity().runOnUiThread(() ->
                                frontFlash.setVisibility(View.GONE));
                        //Updating android files database
                        Uri imageUri = Uri.parse("file://" + filePath);
                        Intent mediaScanIntent =
                                new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                        imageUri);
                        mediaScanIntent.setData(Uri.fromFile(file));
                        requireActivity().sendBroadcast(mediaScanIntent);
                        loadGalleryButton();
                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                        frontFlash.setVisibility(View.GONE);
                        String msg = "Photo capture failed: " + exception.getMessage();
                        Toast.makeText(requireActivity().getBaseContext(), msg,
                                Toast.LENGTH_LONG).show();
                        if (exception.getCause() != null) {
                            exception.getCause().printStackTrace();
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
        float w = pvView.getMeasuredWidth();
        float h = pvView.getMeasuredHeight();

        float centreX = w / 2f; //calc centre of the viewfinder
        float centreY = h / 2f;

        int rotationDgr;
        //int rotation = (int) txView.getRotation();
        //cast to int bc switches don't like floats

        switch (requireActivity().getWindowManager().getDefaultDisplay().getRotation()) {
            //correct output to account for display rotation
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
        //pvView.setTransform(mx); //apply transformations to textureview
    }

    public int aspectRatio(int width, int height) {
        double previewRatio =
                (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - (4.0 / 3.0))
                <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3;
        } else {
            return AspectRatio.RATIO_16_9;
        }
    }

    //Fixing selfies orientation and save as showed in preview
    private void flipImage(File file, float degrees, boolean flipVertically,
                           boolean flipHorizontally) {
        Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
        // create new matrix for transformation
        Matrix matrix = new Matrix();
        float sx;
        float sy;
        if (flipHorizontally) {
            sy = -1f;
        } else {
            sy = 1f;
        }
        if (flipVertically) {
            sx = -1f;
        } else {
            sx = 1f;
        }
        if (flipVertically) {
            matrix.postScale(sx, sy,
                    bm.getWidth() / 2f, bm.getHeight() / 2f);
        }
        matrix.postRotate(degrees);
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
        requireActivity().runOnUiThread(() -> {
            Glide.with(requireActivity())
                    .asBitmap()
                    .load(uri) // image url
                    .placeholder(R.drawable.ic_image) // any placeholder to load at start
                    .error(R.drawable.ic_image)  // any image in case of error
                    .override(200, 200) // resizing
                    .transform(new CenterCrop(), new RoundedCorners(35))
                    .into(galleryButton);  // imageview object
        });

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
            flMode = ImageCapture.FLASH_MODE_ON;
        } else {
            flMode = ImageCapture.FLASH_MODE_OFF;
        }
        imgCap = new ImageCapture.Builder()
                .setFlashMode(flMode)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(requireActivity().getWindowManager()
                        .getDefaultDisplay().getRotation())
                .setTargetAspectRatio(asp)
                .build();
        //cameraProvider.unbind(imgCap);s
        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imgCap);
        cameraControl = camera.getCameraControl();
    }

    private void takeFrontFlashPhoto() {
        AlphaAnimation flashAnim = new AlphaAnimation(0f, 1f);
        flashAnim.setDuration(1500);
        requireActivity().runOnUiThread(() -> {
            frontFlash.setVisibility(View.VISIBLE);
            frontFlash.startAnimation(flashAnim);

            Handler h = new Handler();
            h.postDelayed(this::takePhoto, 2000);

        });
    }

    private void startFocusAnimation(float x, float y) {
        RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams) focusSelector.getLayoutParams();
        params.topMargin = (int) (y-100f);
        params.leftMargin = (int) (x-100f);
        focusSelector.setLayoutParams(params);
        focusSelector.setVisibility(View.VISIBLE);
        ScaleAnimation endAnimation = new ScaleAnimation(
                1f,0.0f,1f,0.0f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        );
        ScaleAnimation startAnimation = new ScaleAnimation(
                0.0f,1f,0.0f,1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        );
        startAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                focusSelector.startAnimation(endAnimation);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        endAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                focusSelector.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        startAnimation.setDuration(1000);
        endAnimation.setDuration(500);

        focusSelector.startAnimation(startAnimation);
    }

}