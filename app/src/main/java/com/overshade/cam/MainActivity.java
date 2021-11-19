package com.overshade.cam;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentContainerView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_CODE_PERMISSIONS = 10; //arbitrary number
    private final String[] REQUIRED_PERMISSIONS =
            new String[]{"android.permission.CAMERA",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_EXTERNAL_STORAGE"
            };

    FragmentContainerView cameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraView = findViewById(R.id.cameraView);

        // Checking if the user has granted all permissions
        checkPermissions();
        // If permissions are granted, let's check them again and also the
        // main files
        checkRequirementsAndLaunch();

    }

    /* App start requirements */
    private void checkRequirementsAndLaunch() {
        if(allPermissionsGranted() && mainFilesCreated()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.cameraView, new Camera(), "CAMERA").commit();
        } else {
            this.finish();
        }
    }


    /* Permission checkers */

    private void checkPermissions() {
        if(!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        //Start camera when permissions have been granted otherwise exit app
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                getSupportFragmentManager()
                        .beginTransaction().add(new Camera(), "CAMERA")
                        .commit();
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        //Check if required permissions have been granted
        for(String permission : REQUIRED_PERMISSIONS) {
            if(ContextCompat.checkSelfPermission(this,
                    permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    /* App files and folders */

    private boolean mainFilesCreated() {
        File appFolder = new File(
                Environment.getExternalStorageDirectory() +
                        File.separator + "Cam");
        File imagesFolder = new File (
                Environment.getExternalStorageDirectory() +
                        File.separator +
                        Environment.DIRECTORY_PICTURES +
                        File.separator + "Cam");

        int errors = 0;
        if (!appFolder.exists()) {
            if (!appFolder.mkdirs()) {
                errors++;
            }
        }
        if (!imagesFolder.exists()) {
            if (!imagesFolder.mkdirs()) {
                errors++;
            }
        }
        if (errors == 0) {
            return true;
        } else {
            Toast.makeText(this, "Cannot create main folder",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

}