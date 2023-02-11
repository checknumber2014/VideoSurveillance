/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.java;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.core.util.Consumer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory;
import com.google.android.gms.common.annotation.KeepName;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.demo.CameraXViewModel;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.VisionImageProcessor;
import com.google.mlkit.vision.demo.java.segmenter.SegmenterProcessor;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/** Live preview demo app for ML Kit APIs using CameraX. */
@KeepName
@RequiresApi(VERSION_CODES.LOLLIPOP)
public final class CameraXLivePreviewActivity extends AppCompatActivity
    implements CompoundButton.OnCheckedChangeListener {
  private static final String TAG = "CameraXLivePreview";
  private static final String SELFIE_SEGMENTATION = "Selfie Segmentation";
  private static final String STATE_SELECTED_MODEL = "selected_model";

  private PreviewView previewView;
  private GraphicOverlay graphicOverlay;

  @Nullable
  private ProcessCameraProvider cameraProvider;
  @Nullable
  private Preview previewUseCase;
  @Nullable
  private ImageAnalysis analysisUseCase;
  @Nullable
  private VisionImageProcessor imageProcessor;
  private boolean needUpdateGraphicOverlayImageSourceInfo;

  private String selectedModel = SELFIE_SEGMENTATION;
  private int lensFacing = CameraSelector.LENS_FACING_BACK;
  private CameraSelector cameraSelector;

  //modified 0118
  private ImageCapture imageCapture;

  private Timer timer = new Timer();
  private TimerTask task;
  private VideoCapture<Recorder> videoCapture;
  private Recording recording;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate");

    if (savedInstanceState != null) {
      selectedModel = savedInstanceState.getString(STATE_SELECTED_MODEL, SELFIE_SEGMENTATION);
    }
    //lensFacing: default CameraSelector.LENS_FACING_BACK
    cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

    setContentView(R.layout.activity_vision_camerax_live_preview);
    previewView = findViewById(R.id.preview_view);
    if (previewView == null) {
      Log.d(TAG, "previewView is null");
    }
    //圖形疊加
    graphicOverlay = findViewById(R.id.graphic_overlay);
    if (graphicOverlay == null) {
      Log.d(TAG, "graphicOverlay is null");
    }
    ToggleButton facingSwitch = findViewById(R.id.facing_switch);
    facingSwitch.setOnCheckedChangeListener(this);

    //onCreate: maintain user preference when rotating phones
    new ViewModelProvider(this, AndroidViewModelFactory.getInstance(getApplication()))
            .get(CameraXViewModel.class)
            .getProcessCameraProvider()
            .observe(
                    this,
                    provider -> {
                      cameraProvider = provider;
                      bindAllCameraUseCases();
                    });

    //get user email information
    Intent intent = getIntent();
    senderEmail =  intent.getStringExtra("SenderEmail");
    senderPassword = intent.getStringExtra("SenderKey");
    receiverEmail = intent.getStringExtra("RecvEmail");
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putString(STATE_SELECTED_MODEL, selectedModel);
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    if (cameraProvider == null) {
      return;
    }
    int newLensFacing =
            lensFacing == CameraSelector.LENS_FACING_FRONT
                    ? CameraSelector.LENS_FACING_BACK
                    : CameraSelector.LENS_FACING_FRONT;
    CameraSelector newCameraSelector =
            new CameraSelector.Builder().requireLensFacing(newLensFacing).build();
    try {
      if (cameraProvider.hasCamera(newCameraSelector)) {
        Log.d(TAG, "Set facing to " + newLensFacing);
        lensFacing = newLensFacing;
        cameraSelector = newCameraSelector;
        bindAllCameraUseCases();
        return;
      }
    } catch (CameraInfoUnavailableException e) {
      // Falls through
    }
    Toast.makeText(
                    getApplicationContext(),
                    "This device does not have lens with facing: " + newLensFacing,
                    Toast.LENGTH_SHORT)
            .show();
  }

  @Override
  public void onResume() {
    super.onResume();
    bindAllCameraUseCases();
    //每秒查詢場景監控狀態
    task = new MyTask();
    timer.schedule(task, 1000, 500);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (imageProcessor != null) {
      imageProcessor.stop();
    }
    //如果有正在錄影的session，stop(含儲存)
    Recording curRecording = recording;
    if (curRecording != null) {
      curRecording.stop();
      recording = null;
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (imageProcessor != null) {
      imageProcessor.stop();
    }
    //如果有正在錄影的session，stop(含儲存)
    Recording curRecording = recording;
    if (curRecording != null) {
      curRecording.stop();
      recording = null;
    }
  }

  private void bindAllCameraUseCases() {
    if (cameraProvider != null) {cameraProvider.unbindAll();}
    bindPreviewUseCase();
    bindAnalysisUseCase();
    //bindImageCaptureUseCase();
    bindVideoCaptureUseCase();
  }

  private void bindPreviewUseCase() {
    if (cameraProvider == null) { return; }
//    if (previewUseCase != null) {cameraProvider.unbind(previewUseCase); }
    Preview.Builder builder = new Preview.Builder();
    previewUseCase = builder.build();
    previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
    cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase);
  }

  SegmenterProcessor segmenter;
  private void bindAnalysisUseCase() {
    if (cameraProvider == null) { return; }
    try {
      segmenter = new SegmenterProcessor(this);
      imageProcessor = segmenter;
    } catch (Exception e) {
      Log.e(TAG, "Can not create image processor: " + selectedModel, e);
      return;
    }

    ImageAnalysis.Builder builder = new ImageAnalysis.Builder();
    analysisUseCase = builder.build();
    needUpdateGraphicOverlayImageSourceInfo = true;
    analysisUseCase.setAnalyzer(
            ContextCompat.getMainExecutor(this),
            imageProxy -> {
              if (needUpdateGraphicOverlayImageSourceInfo) {
                boolean isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT;
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                if (rotationDegrees == 0 || rotationDegrees == 180) {
                  graphicOverlay.setImageSourceInfo(
                          imageProxy.getWidth(), imageProxy.getHeight(), isImageFlipped);
                } else {
                  graphicOverlay.setImageSourceInfo(
                          imageProxy.getHeight(), imageProxy.getWidth(), isImageFlipped);
                }
                needUpdateGraphicOverlayImageSourceInfo = false;
              }
              try {
                imageProcessor.processImageProxy(imageProxy, graphicOverlay);
              } catch (MlKitException e) {
                Log.e(TAG, "Failed to process image. Error: " + e.getLocalizedMessage());
                Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT)
                        .show();
              }
            });
    cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, analysisUseCase);
  }

  private void bindImageCaptureUseCase() {
    if (cameraProvider == null) { return; }
    try {
      ImageCapture.Builder builder = new ImageCapture.Builder()
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
              .setJpegQuality(50);
      imageCapture = builder.build();
      cameraProvider.unbindAll();
      cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase, analysisUseCase, imageCapture);
    } catch (Exception e) {
      Log.i(TAG, "Can not create image Capturer: ", e);
    }
  }

  private void bindVideoCaptureUseCase() {
    if (cameraProvider == null)
      return;

    Recorder recorder = new Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build();

    videoCapture = VideoCapture.withOutput(recorder);
    cameraProvider.unbindAll();
    cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase, analysisUseCase, videoCapture);
  }


  protected class MyTask extends TimerTask {
    @Override
    public void run() {
      if (segmenter == null) return;
      ArrayList<Float> ratioList = segmenter.getRatioList();
      int fgLastFrame = -1;
      for (int i = 0; i < ratioList.size(); i++) {
        if (ratioList.get(i) > 0.01) { fgLastFrame = i; }
      }
      if ( fgLastFrame == -1 ) { //no more foreground frame exists
        Recording curRecording = recording;
        if( curRecording != null ){
          curRecording.stop();
          recording = null;
          sendEmail();
        }
        return;
      } else { //fgLastFrame = 0 ~ 9 means that foreground frame exists
        Recording curRecording = recording;
        if( curRecording != null ) {
          return;
        } else {
          takeVideo();
        }
      }
    }
  }

  private void takePhoto(String event) {
    if (imageCapture == null) return;
    String storageDir = "DCIM/" + event;
    File externalStorage = Environment.getExternalStoragePublicDirectory(storageDir);
    String fileName = new SimpleDateFormat("yyyyMMdd_HHmmSSss").format(System.currentTimeMillis());
    File photoFile = new File(externalStorage, fileName + ".jpg");
    ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

    imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageSavedCallback() {
              @Override
              public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
              }
              @Override
              public void onError(@NonNull ImageCaptureException exception) {
              }
            });
  }

  String timestamp;
  private void takeVideo() {
    if (videoCapture == null) return;
    Recording curRecording = recording;
    if (curRecording != null) {
      curRecording.stop();
      recording = null;
      return;
    }
    //for video File storage
    timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis());
    File videoFile = new File(Environment.getExternalStoragePublicDirectory("DCIM/"), timestamp + ".mp4");
    FileOutputOptions fileOutputOptions = new FileOutputOptions.Builder(videoFile).build();
    PendingRecording pendingRecording = videoCapture.getOutput().prepareRecording(this, fileOutputOptions);


    if ( PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) {
      pendingRecording.withAudioEnabled();

      recording = pendingRecording.start(ContextCompat.getMainExecutor(this), new Consumer<VideoRecordEvent>() {
        @Override
        public void accept(VideoRecordEvent videoRecordEvent) {
          if (videoRecordEvent instanceof VideoRecordEvent.Start) { //start recording
          } else if (videoRecordEvent instanceof VideoRecordEvent.Pause) { //Pause Recording
          } else if (videoRecordEvent instanceof VideoRecordEvent.Resume) { //Resume Recording
          } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) { //stop Recording
            VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
            // Handles a finalize event for the active recording, checking Finalize.getError()
            if (!finalizeEvent.hasError()) {
              Log.d("VideoRecord", "Success Stored a Video: "+ ((VideoRecordEvent.Finalize) videoRecordEvent).getOutputResults().getOutputUri());
            } else {
              if (recording != null) {
                recording.close();
                recording = null;
              }
            }
          }
        }
      });
    }
  }

  String senderEmail;
  String senderPassword;
  String receiverEmail;
  private void sendEmail(){
    try{
      //寄件者部分：需要使用者設定Google應用密碼
//      String senderEmail = "foryourtest2023@gmail.com";
//      String senderPassword = "bujzusrxpwxltope";
//      String receiverEmail = "checknumber2013@gmail.com";

      String host = "smtp.gmail.com";
      Properties properties = System.getProperties();
      properties.put("mail.smtp.host", host);
      properties.put("mail.smtp.port", "465");
      properties.put("mail.smtp.ssl.enable", "true");
      properties.put("mail.smtp.auth", "true");

      javax.mail.Session session = Session.getDefaultInstance(properties, new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(senderEmail, senderPassword);
        }
      });

      MimeMessage mimeMessage = new MimeMessage(session);
      mimeMessage.addRecipients(Message.RecipientType.TO, String.valueOf(new InternetAddress(receiverEmail)));
      mimeMessage.setSubject("Subject: Abnormal invasion detected "+ new Date());
      mimeMessage.setText("Detected abnormal invasion event: " + timestamp);

      BodyPart messageBodyPart = new MimeBodyPart();
      messageBodyPart.setText("Detected abnormal invasion event: " + timestamp);

      Multipart multipart = new MimeMultipart();
      multipart.addBodyPart(messageBodyPart);

      messageBodyPart = new MimeBodyPart();
      File videoFile = new File(Environment.getExternalStoragePublicDirectory("DCIM/"), timestamp + ".mp4");
      if( ! videoFile.exists() ) {
        System.out.println("not exist");
      } else {
        DataSource source = new FileDataSource(videoFile.toString());
        messageBodyPart.setDataHandler(new DataHandler(source));
        messageBodyPart.setFileName(timestamp + ".mp4");
        multipart.addBodyPart(messageBodyPart);
        mimeMessage.setContent(multipart);
      }

      //send email in background thread
      Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
          try{
            Transport.send(mimeMessage);
          } catch (MessagingException e ) {
            e.printStackTrace();
          }
        }
      });
      thread.start();
    } catch (Exception e ) {
      e.printStackTrace();
    }
  }
}
