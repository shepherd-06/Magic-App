package com.example.magicappv2;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UserLocation extends AppCompatActivity {

    private Button sendLoc;
    private TextView statusText;

    private boolean isSending = false;
    private Socket socket;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendTask;

    private FusedLocationProviderClient fusedLocationClient;

    private String userId;
    private String baseUrl;

    private static final String TAG = "UserLocation";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_location);

        initUi();
        loadConfig();
        checkLocationPermission();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        sendLoc.setOnClickListener(v -> {
            if (!isSending) {
                startSocket();
            } else {
                stopSocket();
            }
        });
    }

    private void initUi() {
        sendLoc = findViewById(R.id.btnbtn);
        statusText = findViewById(R.id.txtView);
        statusText.setText("Waiting...");
    }

    private void loadConfig() {
        SharedPreferences sharedPref = getSharedPreferences("MagicAppPrefs", Context.MODE_PRIVATE);
        baseUrl = sharedPref.getString("storedUrl", "");
        userId = sharedPref.getString("userId", "");

        if (baseUrl.isEmpty() || userId.isEmpty()) {
            Toast.makeText(this, "Base URL or UserID not set.", Toast.LENGTH_LONG).show();
            sendLoc.setEnabled(false);
        }
    }

    private void startSocket() {
        try {
            Log.d(TAG, "Attempting to connect to socket: " + baseUrl);
            socket = IO.socket(baseUrl);  // sharedPref already stored full URL base

            socket.on(Socket.EVENT_CONNECT, args -> runOnUiThread(() -> {
                Log.d(TAG, "Socket connected");
                statusText.append("\nSocket Connected");
                startSendingLoop();
            }));

            socket.on("ack", args -> runOnUiThread(() -> {
                JSONObject ack = (JSONObject) args[0];
                Log.d(TAG, "Received ack: " + ack.toString());
                statusText.append("\nAck: " + ack.toString());
            }));

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> runOnUiThread(() -> {
                Log.e(TAG, "Connection error: " + args[0].toString());
                statusText.append("\nConnection error: " + args[0].toString());
            }));

            socket.connect();

            isSending = true;
            sendLoc.setText("Stop Sending");

        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid socket URL", e);
            e.printStackTrace();
            Toast.makeText(this, "Invalid socket URL!", Toast.LENGTH_LONG).show();
        }
    }

    private void startSendingLoop() {
        sendTask = new Runnable() {
            @Override
            public void run() {
                sendLocation();
                handler.postDelayed(this, 3000);  // 3 seconds interval
            }
        };
        handler.post(sendTask);
    }

    private void sendLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.append("\nPermission not granted.");
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                JSONObject payload = new JSONObject();
                try {
                    String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    payload.put("userId", userId);
                    payload.put("latitude", location.getLatitude());
                    payload.put("longitude", location.getLongitude());
                    statusText.append("\n"+ currentTime + " Lat: " + location.getLatitude() + " Lon: " + location.getLongitude());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                socket.emit("send_location", payload);
                statusText.append("\nLocation sent.");
            }
        });
    }
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted, proceed
            Log.d(TAG, "Location permission granted");
        } else {
            // Request both permissions together
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "User granted location permission");
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Location permission denied");
                Toast.makeText(this, "Location permission denied. Cannot access location.", Toast.LENGTH_LONG).show();
                sendLoc.setEnabled(false);
            }
        }
    }

    private void stopSocket() {
        handler.removeCallbacks(sendTask);
        if (socket != null) {
            socket.disconnect();
            socket.close();
        }
        isSending = false;
        sendLoc.setText("Start Sending");
        statusText.append("\nSocket Disconnected");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSocket();
    }
}
