package com.example.magicappv2;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class UserLocation extends AppCompatActivity {

    private Button sendLoc;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_location);

        initUi();

    }

    private void initUi() {
        sendLoc = findViewById(R.id.btnbtn);
        statusText = findViewById(R.id.statusText);
    }


}