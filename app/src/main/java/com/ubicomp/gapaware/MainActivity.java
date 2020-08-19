package com.ubicomp.gapaware;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {
    private static final int[] youSoundIds = new int [] { R.raw.beep, R.raw.speech};
    private static final int[] otherSoundIds = new int [] {R.raw.frontleft, R.raw.front, R.raw.frontright, R.raw.backright, R.raw.back, R.raw.backleft };

    private static final String BEEP = "p";
    private static final String SPEECH = "h";

    private static final String FRONT = "f";
    private static final String FRONT_LEFT = "d";
    private static final String FRONT_RIGHT = "g";
    private static final String BACK = "b";
    private static final String BACK_LEFT = "v";
    private static final String BACK_RIGHT = "n";

    private static String ACTION_USB_PERMISSION = "com.ubicomp.gapaware.USB_PERMISSION";

    TextView text;

    MediaPlayer[] youMps;
    MediaPlayer[] otherMps;

    HashMap<String, Integer> youCodeToId = new HashMap<String, Integer>();
    HashMap<String, Integer> otherCodeToId = new HashMap<String, Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        text = (TextView) MainActivity.this.findViewById(R.id.textView);

        youMps = setupMediaPlayers(youSoundIds, false);
        otherMps = setupMediaPlayers(otherSoundIds, false);

        youCodeToId.put(BEEP, 0);
        youCodeToId.put(SPEECH, 1);

        otherCodeToId.put(FRONT_LEFT, 0);
        otherCodeToId.put(FRONT, 1);
        otherCodeToId.put(FRONT_RIGHT, 2);
        otherCodeToId.put(BACK_RIGHT, 3);
        otherCodeToId.put(BACK, 4);
        otherCodeToId.put(BACK_LEFT, 5);

        text.setText("Connecting serial!");
        connectSerial();
    }

    private MediaPlayer[] setupMediaPlayers(int[] soundIds, boolean looping) {
        MediaPlayer[] mps = new MediaPlayer[soundIds.length];
        for (int i = 0; i < soundIds.length; i++) {
            mps[i] = MediaPlayer.create(this, soundIds[i]);
            mps[i].setLooping(looping);
        }

        return mps;
    }

    private void connectSerial() {
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            text.setText("No drivers!");
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            text.setText("No connection!");
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(
                    ACTION_USB_PERMISSION), 0);
            manager.requestPermission(driver.getDevice(), pi);
            if (!manager.hasPermission(driver.getDevice())) {
                return;
            }
        }

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        text.setText("Connecting...");
        try {
            port.open(connection);
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            text.setText("Connected!");
            SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
            Executors.newSingleThreadExecutor().submit(usbIoManager);
            text.setText("Listeners set up!");
        } catch (IOException e) {
            text.setText("Exception!");
        }

    }

    private void stopSound(MediaPlayer mp)
    {
        if(mp != null && mp.isPlaying())
        {
            mp.stop();
            try {
                mp.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopSounds(MediaPlayer[] mps, int excludeId) {
        for (int i = 0; i < mps.length; i++) {
            if (i == excludeId) {
                continue;
            }

            stopSound(mps[i]);
        }
    }

    private void startSound(MediaPlayer mp) {
        if (mp.isPlaying()) {
            return;
        }

        mp.seekTo(0);
        mp.start();
    }

    @Override
    public void onNewData(final byte[] newData) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String data = new String(newData);
                data = data.trim();
                switch (data) {
                    case BEEP: case SPEECH:
                        int youId = youCodeToId.get(data);
                        stopSounds(youMps, youId);
                        stopSounds(otherMps, -1);
                        startSound(youMps[youId]);
                        break;
                    case FRONT: case BACK: case FRONT_RIGHT: case BACK_RIGHT: case FRONT_LEFT: case BACK_LEFT:
                        int otherId = otherCodeToId.get(data);
                        stopSounds(otherMps, otherId);
                        stopSounds(youMps, -1);
                        startSound(otherMps[otherId]);
                        break;
                    default:
                        data = "UNRECOGNIZED:";
                }

                for (byte b : newData) {
                    data += " " + b;
                }

                text.setText(data);
            }
        });
    }

    @Override
    public void onRunError(Exception e) {
        text.setText("Exception onRunError");
    }
}