/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devrel.vrviewapp;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.sensors.HeadTracker;
import com.google.vr.sdk.widgets.video.VrVideoEventListener;
import com.google.vr.sdk.widgets.video.VrVideoView;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_IS_PAUSED = "isPaused";
    private static final String STATE_VIDEO_DURATION = "videoDuration";
    private static final String STATE_PROGRESS_TIME = "progressTime";


    /**
     * The video view and its custom UI elements.
     */
    private VrVideoView videoWidgetView;

    /**
     * By default, the video will start playing as soon as it is loaded.
     */
    private boolean isPaused = false;

    private SensorManager mSensorManager;
    private Sensor sensorAccelerometer;
    private Sensor sensorMagnetometer;

    HeadTracker track;
    float [] angles = new float[3];


    public SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER || event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                //Check rotation
                check(videoWidgetView.getCurrentPosition());
            }
        }

        class Pair{

            float l, r;
            public Pair(float _l, float _r)
            {
                this.l = _l;
                this.r = _r;
            }

        }


        private final Pair DIRECTION_LEFT = new Pair(1.4f, 1.6f);
        private final Pair DIRECTION_RIGHT = new Pair(-1.6f, -1.4f);

        public boolean needStop = true;
        public boolean needSkip = true;

        public void check(long curPos){
            if (curPos < 1000){
                needStop = true;
                needSkip = true;
            }

            //First fork
            if (needSkip && curPos >= 11800){
                if (checkDirection(DIRECTION_RIGHT)){
                    videoWidgetView.seekTo(27800);
                    videoWidgetView.playVideo();
                    needStop = false;
                    needSkip = false;
                } else
                if (checkDirection(DIRECTION_LEFT)){
                    videoWidgetView.playVideo();
                    needStop = false;
                    needSkip = false;
                } else
                {
                    videoWidgetView.pauseVideo();
                }
            }

            //Second fork
            if (needStop && curPos >= 20000){
                if (!checkDirection(DIRECTION_RIGHT)) {
                    videoWidgetView.pauseVideo();
                } else {
                    needStop = false;
                    videoWidgetView.playVideo();
                }
            }
        }

        public boolean checkDirection(Pair direction){
            HeadTransform trans = new HeadTransform();
            track.getLastHeadView(trans.getHeadView(), 0);
            trans.getEulerAngles(angles, 0);

            float _Y = angles[1];

            return  (_Y > direction.l) && (_Y < direction.r);
        }


        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        track = HeadTracker.createFromContext(this);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        sensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this.sensorListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        sensorMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this.sensorListener, sensorMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);

        videoWidgetView = (VrVideoView) findViewById(R.id.video_view);

// initialize based on the saved state
        if (savedInstanceState != null) {
            long progressTime = savedInstanceState.getLong(STATE_PROGRESS_TIME);
            videoWidgetView.seekTo(progressTime);

            isPaused = savedInstanceState.getBoolean(STATE_IS_PAUSED);
            if (isPaused) {
                videoWidgetView.pauseVideo();
            }
        }


// initialize the video listener
        videoWidgetView.setEventListener(new VrVideoEventListener() {
            /**
             * Called by video widget on the UI thread when it's done loading the video.
             */
            @Override
            public void onLoadSuccess() {
                Log.i("", "Successfully loaded video " + videoWidgetView.getDuration());
            }

            /**
             * Called by video widget on the UI thread on any asynchronous error.
             */
            @Override
            public void onLoadError(String errorMessage) {
                Log.e("", "Error loading video: " + errorMessage);
            }

            @Override
            public void onClick() {
                if (isPaused) {
                    videoWidgetView.playVideo();
                } else {
                    videoWidgetView.pauseVideo();
                }

                isPaused = !isPaused;
            }

            /**
             * Update the UI every frame.
             */
            @Override
            public void onNewFrame() {
            }

            /**
             * Make the video play in a loop. This method could also be used to move to the next video in
             * a playlist.
             */
            @Override
            public void onCompletion() {
                videoWidgetView.seekTo(0);
            }
        });

        videoWidgetView.setDisplayMode(VrVideoView.DisplayMode.FULLSCREEN_MONO);

        play();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putLong(STATE_PROGRESS_TIME, videoWidgetView.getCurrentPosition());
        savedInstanceState.putLong(STATE_VIDEO_DURATION, videoWidgetView.getDuration());
        savedInstanceState.putBoolean(STATE_IS_PAUSED, isPaused);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        track.stopTracking();
        // Prevent the view from rendering continuously when in the background.
        videoWidgetView.pauseRendering();
        // If the video was playing when onPause() is called, the default behavior will be to pause
        // the video and keep it paused when onResume() is called.
        isPaused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        track.startTracking();
        play();
        // Resume the 3D rendering.
        videoWidgetView.resumeRendering();
    }

    @Override
    public void onDestroy() {
        // Destroy the widget and free memory.
        videoWidgetView.shutdown();
        super.onDestroy();
    }

    public void play() {
        try {
            if (videoWidgetView.getDuration() <= 0) {
                videoWidgetView.loadVideoFromAsset("tmp.mp4", null);
            }
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
        }
    }
}
