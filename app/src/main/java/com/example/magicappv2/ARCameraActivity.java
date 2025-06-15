package com.example.magicappv2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ARCameraActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 10;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;

    private TextView overlayText;
    private View redDot, leftEdgeBar, rightEdgeBar;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] gravity;
    private float[] geomagnetic;
    private float azimuth;  // heading in degrees

    private static final double TARGET_LAT = 61.4543633;
    private static final double TARGET_LON = 23.851835;

    private static final float SMOOTHING_ALPHA = 0.1f;
    private static final double ANGLE_THRESHOLD = 70;
    private static final double ANGLE_HYSTERESIS = 10;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity = event.values.clone();
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagnetic = event.values.clone();
            }

            if (gravity != null && geomagnetic != null) {
                float[] R = new float[9];
                float[] I = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                if (success) {
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    float newAzimuth = (float) Math.toDegrees(orientation[0]);
                    if (newAzimuth < 0) newAzimuth += 360;

                    azimuth = (azimuth == 0f) ? newAzimuth :
                            (SMOOTHING_ALPHA * newAzimuth + (1 - SMOOTHING_ALPHA) * azimuth);

                    updateDirection();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout layout = new FrameLayout(this);
        layout.setId(R.id.main);
        setContentView(layout);

        // Camera PreviewView
        previewView = new PreviewView(this);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        layout.addView(previewView);

        // Text overlay
        overlayText = new TextView(this);
        overlayText.setText("Calculating...");
        overlayText.setTextColor(Color.WHITE);
        overlayText.setTextSize(24);
        overlayText.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        overlayText.setPadding(0, 100, 0, 0);
        layout.addView(overlayText);

        // Red dot
        redDot = new View(this);
        int dotSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dotSize, dotSize);
        redDot.setLayoutParams(dotParams);
        redDot.setBackground(ContextCompat.getDrawable(this, R.drawable.circle_background));
        redDot.setVisibility(View.INVISIBLE);
        layout.addView(redDot);

        // Edge bars
        leftEdgeBar = new View(this);
        rightEdgeBar = new View(this);
        leftEdgeBar.setBackgroundColor(Color.parseColor("#FF5722"));
        rightEdgeBar.setBackgroundColor(Color.parseColor("#FF5722"));
        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(32, ViewGroup.LayoutParams.MATCH_PARENT);
        leftParams.gravity = Gravity.START;
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(32, ViewGroup.LayoutParams.MATCH_PARENT);
        rightParams.gravity = Gravity.END;
        layout.addView(leftEdgeBar, leftParams);
        layout.addView(rightEdgeBar, rightParams);
        leftEdgeBar.setVisibility(View.INVISIBLE);
        rightEdgeBar.setVisibility(View.INVISIBLE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

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
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    private void updateDirection() {
        double userLat = 61.49625;
        double userLon = 23.7769451;
        double bearingToTarget = calculateBearing(userLat, userLon, TARGET_LAT, TARGET_LON);
        double relativeAngle = (bearingToTarget - azimuth + 360) % 360;

        double angleOffset = relativeAngle;
        if (angleOffset > 180) angleOffset -= 360;
        angleOffset = Math.max(-90, Math.min(90, angleOffset));

        double screenCenterX = previewView.getWidth() / 2.0;
        double maxOffsetX = screenCenterX - 100;
        double offsetX = screenCenterX + (angleOffset / 90.0) * maxOffsetX;

        double finalAngleOffset = angleOffset;
        runOnUiThread(() -> {
            overlayText.setText(String.format("Heading: %.0f°\nTarget: %.0f°\nDelta: %.0f°",
                    azimuth, bearingToTarget, relativeAngle));

            if (Math.abs(finalAngleOffset) > 160) {
                overlayText.setText("🔄 Target behind you");
                redDot.setVisibility(View.INVISIBLE);
                leftEdgeBar.setVisibility(View.INVISIBLE);
                rightEdgeBar.setVisibility(View.INVISIBLE);
                return;
            }

            if (Math.abs(finalAngleOffset) < ANGLE_THRESHOLD) {
                // Target visible
                redDot.setX((float) offsetX - redDot.getWidth() / 2f);
                redDot.setY(previewView.getHeight() / 2f - redDot.getHeight() / 2f);
                redDot.setVisibility(View.VISIBLE);
                leftEdgeBar.setVisibility(View.INVISIBLE);
                rightEdgeBar.setVisibility(View.INVISIBLE);
            } else {
                redDot.setVisibility(View.INVISIBLE);
                if (finalAngleOffset > 0) {
                    rightEdgeBar.setVisibility(View.VISIBLE);
                    leftEdgeBar.setVisibility(View.INVISIBLE);
                } else {
                    leftEdgeBar.setVisibility(View.VISIBLE);
                    rightEdgeBar.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double theta = Math.atan2(y, x);
        return (Math.toDegrees(theta) + 360) % 360;
    }
}
