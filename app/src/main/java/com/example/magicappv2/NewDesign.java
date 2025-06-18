package com.example.magicappv2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.navigation.NavigationView;
import com.google.common.util.concurrent.ListenableFuture;


public class NewDesign extends AppCompatActivity {

    // UI Elements
    private View barNorth, barSouth, barEast, barWest, barNE, barNW, barSE, barSW;
    private TextView statusText;
    private ProgressBar progressBar;

    private DrawerLayout drawerLayout;
    private NavigationView navView;

    // Sensor
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity, geomagnetic;
    private float azimuth = 0f;
    private float smoothedAzimuth = 0f;
    private static final float SMOOTHING_ALPHA = 0.15f;

    private PreviewView cameraPreview;
    private ProcessCameraProvider cameraProvider;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private Location userLocation;

    // Target Coordinates
    private static final double TARGET_LAT = 61.4927794;
    private static final double TARGET_LON = 23.7772398;
    private double simulatedTargetLat = TARGET_LAT;
    private double simulatedTargetLon = TARGET_LON;

    private Vibrator vibrator;
    private double lastVibrationCheckpoint = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_design);

        initUI();
        initSensors();
        initLocation();

        startCamera();
    }

    @SuppressLint("SetTextI18n")
    private void initUI() {
        barNorth = findViewById(R.id.barNorth);
        barSouth = findViewById(R.id.barSouth);
        barEast = findViewById(R.id.barEast);
        barWest = findViewById(R.id.barWest);
        barNE = findViewById(R.id.barNorthEast);
        barNW = findViewById(R.id.barNorthWest);
        barSE = findViewById(R.id.barSouthEast);
        barSW = findViewById(R.id.barSouthWest);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progress_circular);
        cameraPreview = findViewById(R.id.cameraPreview);

        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);

        statusText.setText("Calculations in progress. Thank you for your patience.");

//        drawers onClick Listener

        // Set drawer listener (if needed)
        navView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_add_url) {
                    startActivity(new Intent(NewDesign.this, AddUrlActivity.class));
                } else if (id == R.id.nav_send_loc) {
                    startActivity(new Intent(NewDesign.this, UserLocation.class));
                }

                drawerLayout.closeDrawers();
                return true;
            }
        });
    }


    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION); // Fine includes coarse if granted
        }
    }

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startLocationUpdates();
            });

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        LocationRequest request = new LocationRequest.Builder(
                fineGranted ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                3000L)
                .setMinUpdateIntervalMillis(2000L)
                .build();

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            userLocation = locationResult.getLastLocation();
            assert userLocation != null;
            Log.d("LOCATION", "Received: " + userLocation.getLatitude() + "," + userLocation.getLongitude());

            simulateTargetMovement();
        }
    };

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                gravity = event.values.clone();
            else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                geomagnetic = event.values.clone();

            if (gravity != null && geomagnetic != null) {
                float[] R = new float[9], I = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                if (success) {
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    float newAzimuth = (float) Math.toDegrees(orientation[0]);
                    if (newAzimuth < 0) newAzimuth += 360;

                    if (azimuth == 0f) {
                        azimuth = newAzimuth;
                    } else {
                        azimuth = SMOOTHING_ALPHA * newAzimuth + (1 - SMOOTHING_ALPHA) * azimuth;
                    }
                    updateDirection();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void updateDirection() {
        if (userLocation == null) return;

        if (progressBar.getVisibility() == View.VISIBLE) {
            progressBar.setVisibility(View.GONE);
            pikapikaanimation();
            triggerVibration();
        }
        double userLat = userLocation.getLatitude();
        double userLon = userLocation.getLongitude();

        double bearing = calculateBearing(userLat, userLon, simulatedTargetLat, simulatedTargetLon);
        double relativeAngle = (bearing - azimuth + 360) % 360;
        String sector = mapAngleToSector(relativeAngle);
        double distance = calculateDistance(userLat, userLon, simulatedTargetLat, simulatedTargetLon);

        if (distance > 1000) {
            statusText.setText(String.format("Target too far (%.1f m) away to direct correctly!", distance));
        } else if (distance > 50) {
            statusText.setText(String.format("Distance: %.1f m\nDirection: %s", distance, sector));
        } else {
            statusText.setText(String.format("We are getting closer!\nDistance: %.1f m\nDirection: %s", distance, sector));
        }

        Log.d("DIRECTION", "Azimuth: " + azimuth + ", Relative: " + relativeAngle);
        handleVibrationLogic(distance);  // ✅ insert vibration handler here
        showBar(sector);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCamera();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
            Log.d("Camera", "Camera stopped and resources released.");
        }
    }

    private void bindCamera() {
        cameraProvider.unbindAll();
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    private void handleVibrationLogic(double distance) {
        long currentTime = System.currentTimeMillis();
        if (distance > 50) {
            if (currentTime - lastVibrationCheckpoint >= 3000) {
                lastVibrationCheckpoint = currentTime;
                triggerVibration();
            }
        } else {
            if (lastVibrationCheckpoint == -1 || (currentTime - lastVibrationCheckpoint) >= 3000) {
                lastVibrationCheckpoint = currentTime;
                triggerVibration();
            }
        }
    }

    private void triggerVibration() {
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrator.vibrate(generateRandomVibration());
            } else {
                int randomVal = (int)(Math.random() * 1000);
                VibrationEffect.createOneShot(randomVal, VibrationEffect.DEFAULT_AMPLITUDE);
            }
        } else {
            // Fallback for pre-Q devices
            vibrator.vibrate((int)(Math.random() * 1000));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private VibrationEffect generateRandomVibration() {
        int randomVal = (int)(Math.random() * 10);  // 0 to 9

        switch (randomVal) {
            case 1: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
            case 2: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
            case 3: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);
            case 4: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
            case 5: {
                VibrationEffect.Composition composition = null;
                composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f);
                return composition.compose();
            }
            case 6: {
                VibrationEffect.Composition composition = null;
                composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f);

                return composition.compose();
            }
            case 7: {
                VibrationEffect.Composition composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.7f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f);
                return composition.compose();
            }
            case 8: {
                VibrationEffect.Composition composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f);
                return composition.compose();
            }
            case 9: {
                VibrationEffect.Composition composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f);
                return composition.compose();
            }
            default: return VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE);
        }
    }


    private void showBar(String sector) {
        hideAllBars();

        View targetBar = null;
        switch (sector) {
            case "N": targetBar = barNorth; break;
            case "NE": targetBar = barNE; break;
            case "E": targetBar = barEast; break;
            case "SE": targetBar = barSE; break;
            case "S": targetBar = barSouth; break;
            case "SW": targetBar = barSW; break;
            case "W": targetBar = barWest; break;
            case "NW": targetBar = barNW; break;
        }

        if (targetBar != null) {
//            targetBar.setAlpha(0f);
            targetBar.setVisibility(View.VISIBLE);
            targetBar.animate().alpha(1f).setDuration(1500).start();
        }
    }

    private void hideAllBars() {
        View[] bars = {barNorth, barNE, barEast, barSE, barSouth, barSW, barWest, barNW};
        for (View bar : bars) {
            bar.setVisibility(View.INVISIBLE);
        }
    }

    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }

    private String mapAngleToSector(double angle) {
        if (angle >= 337.5 || angle < 22.5) return "N";
        if (angle >= 22.5 && angle < 67.5) return "NE";
        if (angle >= 67.5 && angle < 112.5) return "E";
        if (angle >= 112.5 && angle < 157.5) return "SE";
        if (angle >= 157.5 && angle < 202.5) return "S";
        if (angle >= 202.5 && angle < 247.5) return "SW";
        if (angle >= 247.5 && angle < 292.5) return "W";
        if (angle >= 292.5 && angle < 337.5) return "NW";
        return "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorListener);
    }

    private void pikapikaanimation() {
        ImageView bubble = findViewById(R.id.waterBubble);
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.bubble_pulse);
        bubble.startAnimation(pulse);
    }

    private void simulateTargetMovement() {
        // Max shift: ~0.00002 degrees ≈ 2 meters roughly
        double maxDelta = 0.00002;

        // Random small movement in both directions
        double latShift = (Math.random() - 0.5) * 2 * maxDelta;
        double lonShift = (Math.random() - 0.5) * 2 * maxDelta;

        simulatedTargetLat += latShift;
        simulatedTargetLon += lonShift;

        Log.d("SIMULATED_TARGET", "Lat: " + simulatedTargetLat + " Lon: " + simulatedTargetLon);
    }
}
