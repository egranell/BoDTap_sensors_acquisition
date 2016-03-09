/*
 *   BoDTap Signals Acquisition
 *
 *   Copyright (C) 2016, egranell
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Created on: 01/03/2016
 *      Author: egranell
 *
 */


package com.prhlt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static android.media.AudioRecord.RECORDSTATE_RECORDING;

public class mainBoDTap extends Activity implements SensorEventListener {

    private static String tag = "BoD Signals Acquisition";
    Thread recordThread;
    TextView title, info;
    RelativeLayout layout;
    String name = "";
    int iterations = 0;
    PrintStream ps;
    float[] accelerometer = new float[3];
    float[] gravity = new float[3];
    float[] gyroscope = new float[3];
    boolean tap = false;
    int recorderSampleRate = 16000;
    int recorderChannels = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    int recorderEncoding = AudioFormat.ENCODING_PCM_16BIT;
    int recorderMinBufferSize = AudioRecord.getMinBufferSize(recorderSampleRate, recorderChannels, recorderEncoding);
    byte[] buffer = new byte[recorderMinBufferSize];
    File file;
    File rootDir;
    AlertDialog.Builder alertDialog;
    AlertDialog ad;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mGravity;
    private AudioRecord recorder;

    @SuppressLint({"CutPasteId", "NewApi", "InlinedApi"})
    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        rootDir = new File(Environment.getExternalStorageDirectory().getPath() + "/BoD/");
        if (!rootDir.exists()) {
            if (!rootDir.mkdirs()) {
                Log.e(tag, "Cannot create directory: " + rootDir);
            }
        }

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        layout = (RelativeLayout) findViewById(R.id.relative);
        title = (TextView) findViewById(R.id.title);
        info = (TextView) findViewById(R.id.info);

        alertDialog = new AlertDialog.Builder(mainBoDTap.this);
        alertDialog.setTitle("Basic data for the experiment");
        alertDialog.setMessage("Fill the following data ");

        final EditText input = new EditText(mainBoDTap.this);
        input.setHint("Name of the experiment");
        final EditText input2 = new EditText(mainBoDTap.this);
        input2.setHint("Number of iterations");
        input2.setInputType(InputType.TYPE_CLASS_NUMBER);
        input2.setText("10");

        final Spinner hand = new Spinner(mainBoDTap.this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.hand, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hand.setAdapter(adapter);

        final Spinner dominant = new Spinner(mainBoDTap.this);
        ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(this,
                R.array.dominant, android.R.layout.simple_spinner_item);
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dominant.setAdapter(adapter1);

        final Spinner finger = new Spinner(mainBoDTap.this);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this,
                R.array.finger, android.R.layout.simple_spinner_item);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        finger.setAdapter(adapter2);

        final Spinner taps = new Spinner(mainBoDTap.this);
        ArrayAdapter<CharSequence> adapter3 = ArrayAdapter.createFromResource(this,
                R.array.taps, android.R.layout.simple_spinner_item);
        adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        taps.setAdapter(adapter3);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(hand);
        layout.addView(dominant);
        layout.addView(finger);
        layout.addView(taps);
        layout.addView(input2);
        alertDialog.setView(layout);
        alertDialog.setPositiveButton("Ok",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (taps.getSelectedItemId() != 0 &&
                                hand.getSelectedItemPosition() != 0 &&
                                dominant.getSelectedItemPosition() != 0 &&
                                finger.getSelectedItemPosition() != 0 &&
                                !input2.getText().toString().matches("")) {
                            name = (taps.getSelectedItemId()) + "tap-" +
                                    (hand.getSelectedItemPosition() == 1 ? "right" : "left") +
                                    "-" + (hand.getSelectedItemPosition() == dominant.getSelectedItemPosition() ? "d" : "n") +
                                    "-f" + (finger.getSelectedItemPosition());

                            iterations = Integer.valueOf(input2.getText().toString());
                            file = new File(rootDir.getAbsolutePath() + "/BoD_" + name.replaceAll(" ", "_") + "_" + iterations + "_" + System.currentTimeMillis() + ".txt");
                            try {
                                FileOutputStream fos = new FileOutputStream(file, true);
                                ps = new PrintStream(fos);

                                if (!file.exists()) {
                                    file.createNewFile();
                                }

                                ps.println("Time\tTap\tMic\tAccX\tAccY\tAccZ\tGraX\tGraY\tGraZ\tGyrX\tGyrY\tGyrZ");
                                ps.flush();
                            } catch (IOException e) {
                                Log.e(tag, "File write failed: " + e.toString());
                            }
                            
                            recorder = new AudioRecord(AudioSource.MIC, recorderSampleRate, recorderChannels, recorderEncoding, recorderMinBufferSize * 10);
                            recorder.startRecording();
                            startRecording();

                            new CountDownTimer(5000 * iterations, 100) {
                                public void onTick(long millisUntilFinished) {
                                    info.setText(
                                            String.format("Time: %d\nIterations left: %d\n", System.currentTimeMillis(), millisUntilFinished / 5000 + 1));
                                    if ((millisUntilFinished % 5000) > 1000) {
                                        title.setText(String.format("%.1f", (float) (millisUntilFinished % 5000 - 1000) / 1000));
                                        tap = false;
                                    } else {
                                        title.setText("Tap!");
                                        tap = true;
                                    }
                                }

                                public void onFinish() {
                                    ps.close();

                                    AlertDialog.Builder dialog = new AlertDialog.Builder(mainBoDTap.this);
                                    dialog.setTitle("Adquisition finished");
                                    dialog.setMessage("Do you want to do another adquisition?");
                                    dialog.setPositiveButton("Yes",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    ad.dismiss();
                                                    LinearLayout layout = new LinearLayout(getApplicationContext());
                                                    layout.setOrientation(LinearLayout.VERTICAL);

                                                    ((ViewGroup) hand.getParent()).removeAllViews();
                                                    taps.setSelection(0);
                                                    hand.setSelection(0);
                                                    dominant.setSelection(0);
                                                    finger.setSelection(0);

                                                    taps.setBackgroundColor(Color.TRANSPARENT);
                                                    hand.setBackgroundColor(Color.TRANSPARENT);
                                                    dominant.setBackgroundColor(Color.TRANSPARENT);
                                                    finger.setBackgroundColor(Color.TRANSPARENT);

                                                    layout.addView(hand);
                                                    layout.addView(dominant);
                                                    layout.addView(finger);
                                                    layout.addView(taps);
                                                    layout.addView(input2);
                                                    alertDialog.setView(layout);
                                                    ad = alertDialog.create();
                                                    ad.show();
                                                }
                                            });
                                    dialog.setNegativeButton("Not",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    finish();
                                                }
                                            });
                                    dialog.show();
                                }
                            }.start();


                        } else {
                            Toast.makeText(getApplicationContext(), "Please fill the data correctly", Toast.LENGTH_LONG).show();
                            ad.dismiss();
                            LinearLayout layout = new LinearLayout(getApplicationContext());
                            layout.setOrientation(LinearLayout.VERTICAL);

                            ((ViewGroup) hand.getParent()).removeAllViews();
                            if (taps.getSelectedItemId() == 0)
                                taps.setBackgroundColor(Color.RED);
                            else
                                taps.setBackgroundColor(Color.TRANSPARENT);
                            if (hand.getSelectedItemPosition() == 0)
                                hand.setBackgroundColor(Color.RED);
                            else
                                hand.setBackgroundColor(Color.TRANSPARENT);
                            if (dominant.getSelectedItemPosition() == 0)
                                dominant.setBackgroundColor(Color.RED);
                            else
                                dominant.setBackgroundColor(Color.TRANSPARENT);
                            if (finger.getSelectedItemPosition() == 0)
                                finger.setBackgroundColor(Color.RED);
                            else
                                finger.setBackgroundColor(Color.TRANSPARENT);

                            layout.addView(hand);
                            layout.addView(dominant);
                            layout.addView(finger);
                            layout.addView(taps);
                            layout.addView(input2);
                            alertDialog.setView(layout);
                            ad = alertDialog.create();
                            ad.show();
                        }
                    }
                });

        alertDialog.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        ad = alertDialog.create();
        ad.show();
    }

    private void startRecording() {
        recordThread = new Thread(new Runnable() {
            public void run() {
                Looper.prepare();
                while (recorder.getRecordingState() == RECORDSTATE_RECORDING)
                    recorder.read(buffer, 0, buffer.length);
            }
        });
        recordThread.start();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometer = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscope = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravity = event.values;
        }
        if (file != null && file.exists()) {
            double amplitude = 0;

            for (int i = 0; i < buffer.length / 2; i++) {
                amplitude = amplitude + Math.pow(getShort(buffer[i * 2], buffer[i * 2 + 1]), 2);
            }

            ps.println(System.currentTimeMillis() + "\t"
                    + ((tap) ? 1 : 0) + "\t"
                    + Math.log(amplitude) + "\t"
                    + accelerometer[0] + "\t" + accelerometer[1] + "\t" + accelerometer[2] + "\t"
                    + gravity[0] + "\t" + gravity[1] + "\t" + gravity[2] + "\t"
                    + gyroscope[0] + "\t" + gyroscope[1] + "\t" + gyroscope[2]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ps != null) ps.close();
        if (recorder != null) {
            if (recorder.getRecordingState() == RECORDSTATE_RECORDING) {
                recorder.stop();
            }
            recorder.release();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if (recorder != null) {
            if (recorder.getRecordingState() == RECORDSTATE_RECORDING) {
                recorder.stop();
            }
            recorder.release();
        }
    }

    private short getShort(byte argB1, byte argB2) {
        return (short) (argB1 | (argB2 << 8));
    }
}