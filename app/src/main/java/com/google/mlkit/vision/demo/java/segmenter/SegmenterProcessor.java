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

package com.google.mlkit.vision.demo.java.segmenter;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.demo.preference.PreferenceUtils;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/** A processor to run Segmenter. */
public class SegmenterProcessor extends VisionProcessorBase<SegmentationMask> {

  private static final String TAG = "SegmenterProcessor";

  private final Segmenter segmenter;

  public SegmenterProcessor(Context context) {
    this(context, /* isStreamMode= */ true);
  }

  public SegmenterProcessor(Context context, boolean isStreamMode) {
    super(context);
    SelfieSegmenterOptions.Builder optionsBuilder = new SelfieSegmenterOptions.Builder();
    optionsBuilder.setDetectorMode(
      isStreamMode ? SelfieSegmenterOptions.STREAM_MODE : SelfieSegmenterOptions.SINGLE_IMAGE_MODE);
    if (PreferenceUtils.shouldSegmentationEnableRawSizeMask(context)) {
      optionsBuilder.enableRawSizeMask();
    }

    SelfieSegmenterOptions options = optionsBuilder.build();
    segmenter = Segmentation.getClient(options);
    Log.d(TAG, "SegmenterProcessor created with option: " + options);
  }

  @Override
  protected Task<SegmentationMask> detectInImage(InputImage image) {
    return segmenter.process(image);
  }



  @Override
  protected void onSuccess(
      @NonNull SegmentationMask segmentationMask, @NonNull GraphicOverlay graphicOverlay) {
//    graphicOverlay.add(new SegmentationGraphic(graphicOverlay, segmentationMask));
    computeRatio(segmentationMask);
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Segmentation failed: " + e);
  }

  //modified 0119
  static float ratio = 0;
  static ArrayList<Float> ratioList = new ArrayList<>();
  public ArrayList<Float> getRatioList() {
    return ratioList;
  }
  protected void computeRatio(@NonNull SegmentationMask segmentationMask) {
    ByteBuffer mask = segmentationMask.getBuffer();
    int maskWidth = segmentationMask.getWidth();
    int maskHeight = segmentationMask.getHeight();
    int fgAmount = 0;
    for( int i = 0; i < maskWidth * maskHeight; i++) {
      float backgroundLikelihood = 1 - mask.getFloat();
      if ( backgroundLikelihood < 0.2 ) {
        fgAmount++;
      }
    }
    mask.rewind();
    ratio = (float) fgAmount / ( maskWidth*maskHeight );
    //the larger the ratioList is, the more past predicted results will be referenced.
    //capture image based on the detection results of the last 10 frames.
    if (ratioList.size() >= 10 ) {
      ratioList.remove(0);
    }
    ratioList.add(ratio);
  }
}
