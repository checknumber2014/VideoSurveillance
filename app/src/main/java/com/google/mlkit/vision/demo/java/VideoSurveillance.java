package com.google.mlkit.vision.demo.java;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.mlkit.vision.demo.R;

public class VideoSurveillance extends AppCompatActivity {
    EditText etSenderEmail;
    EditText etSenderKey;
    EditText etRecvEmail;
    Button btStartSurv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_surv);
        findViews();

        btStartSurv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                String senderEmail = etSenderEmail.getText().toString();
                String senderKey = etSenderKey.getText().toString();
                String recvEmail = etRecvEmail.getText().toString();
                if ( senderEmail.equals("") || senderKey.equals("") || recvEmail.equals("") ) {
                    Toast.makeText(VideoSurveillance.this, "Input is blank", Toast.LENGTH_LONG).show();
                } else {
                    bundle.putString("SenderEmail", senderEmail);
                    bundle.putString("SenderKey", senderKey);
                    bundle.putString("RecvEmail", recvEmail);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        Intent intent = new Intent(VideoSurveillance.this, CameraXLivePreviewActivity.class);
                        intent.putExtras(bundle);
                        startActivity(intent);
                    }
                }
            }
        });
    }

    private void findViews() {
        etSenderEmail = findViewById(R.id.etSenderEmail);
        etSenderKey = findViewById(R.id.etSenderKey);
        etRecvEmail = findViewById(R.id.etRecvEmail);
        btStartSurv = findViewById(R.id.btStartSurv);
    }
}