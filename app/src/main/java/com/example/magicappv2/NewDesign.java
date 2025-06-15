package com.example.magicappv2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class NewDesign extends AppCompatActivity {

    // UI Elements
    private View barNorth, barSouth, barEast, barWest, barNE, barNW, barSE, barSW;
    private TextView statusText;

    // Sensor
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity, geomagnetic;
    private float azimuth = 0f;
    private float smoothedAzimuth = 0f;
    private static final float SMOOTHING_ALPHA = 0.15f;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private Location userLocation;

    // Target Coordinates
    private static final double TARGET_LAT = 61.4543633;
    private static final double TARGET_LON = 23.851835;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_design);

        initUI();
        initSensors();
        initLocation();
    }

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
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        Log.d("Pika Paka", "vroom");
    }

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startLocationUpdates();
            });

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3000L)  // interval millis
                .setMinUpdateIntervalMillis(2000L)
                .setWaitForAccurateLocation(true)
                .build();

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) return;
            userLocation = locationResult.getLastLocation();
            assert userLocation != null;
            Log.d("LOCATION", "Received: " + userLocation.getLatitude() + "," + userLocation.getLongitude());

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

    @SuppressLint("SetTextI18n")
    private void updateDirection() {
        if (userLocation == null) return;

        double userLat = userLocation.getLatitude();
        double userLon = userLocation.getLongitude();

        double bearing = calculateBearing(userLat, userLon, TARGET_LAT, TARGET_LON);
        double relativeAngle = (bearing - azimuth + 360) % 360;
        String sector = mapAngleToSector(relativeAngle);
        double distance = calculateDistance(userLat, userLon, TARGET_LAT, TARGET_LON);

        statusText.setText(String.format("Distance: %.1f m\nDirection: %s", distance, sector));
        Log.d("DIRECTION", "Azimuth: " + azimuth + ", Relative: " + relativeAngle);

        showBar(sector);
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
            targetBar.animate().alpha(1f).setDuration(250).start();
            targetBar.setVisibility(View.VISIBLE);
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
}
