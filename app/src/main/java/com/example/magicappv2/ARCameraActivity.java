package com.example.magicappv2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

//sensors
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

public class ARCameraActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 10;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private TextView overlayText;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] gravity;
    private float[] geomagnetic;
    private float azimuth;  // heading in degrees

    // Target coordinate
    private static final double TARGET_LAT = 61.4543633;
    private static final double TARGET_LON = 23.851835;
    private double bearingToTarget = 0;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity = event.values.clone();
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagnetic = event.values.clone();
            }

            if (gravity != null && geomagnetic != null) {
                float R[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                if (success) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    azimuth = (float) Math.toDegrees(orientation[0]);
                    if (azimuth < 0) azimuth += 360;

                    updateDirection();  // Calculate direction on each heading update
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create parent layout (we need a container to hold camera + text)
        FrameLayout layout = new FrameLayout(this);
        layout.setId(R.id.main);
        setContentView(layout);

        // Create and add PreviewView (camera preview)
        previewView = new PreviewView(this);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        layout.addView(previewView);

        // Create and add overlay TextView (for displaying direction)
        overlayText = new TextView(this);
        overlayText.setText("Calculating...");
        overlayText.setTextColor(Color.WHITE);
        overlayText.setTextSize(24);
        overlayText.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        overlayText.setPadding(0, 100, 0, 0);
        layout.addView(overlayText);

        // Camera permissions check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        // Initialize sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        // Bind previewView to camera
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Log.e("Camera", "Permission denied");
            }
        }
    }

    private void updateDirection() {
        // Let's assume user's current location (later we will replace this with real location)
        double userLat = 61.49625;
        double userLon = 23.7769451;

        double bearingToTarget = calculateBearing(userLat, userLon, TARGET_LAT, TARGET_LON);
        double relativeAngle = (bearingToTarget - azimuth + 360) % 360;

        String direction;
        if (relativeAngle < 45 || relativeAngle > 315) {
            direction = "Ahead";
        } else if (relativeAngle < 135) {
            direction = "Right";
        } else if (relativeAngle < 225) {
            direction = "Behind";
        } else {
            direction = "Left";
        }

        Log.d("Direction", "Relative: " + relativeAngle + "°  ->  " + direction);
        @SuppressLint("DefaultLocale") String display = String.format("Heading: %.1f°\nTarget: %.1f°\nDirection: %s", azimuth, bearingToTarget, direction);
        runOnUiThread(() -> overlayText.setText(display));
    }

    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double theta = Math.atan2(y, x);
        double bearing = (Math.toDegrees(theta) + 360) % 360;
        return bearing;
    }
}
