/*
 * This file is part of Anbox Cloud Streaming SDK
 *
 * Copyright 2022 Canonical Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.canonical.anbox.out_of_band_v2;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity implements DataReadTask.DataReadListener {
    private static final String TAG = "DataChannel";

    private static final String ANBOX_WEBRTC_DATA_CHANNEL_INTERFACE = "org.anbox.webrtc.IDataProxyService@1.0";
    private static final String ANBOX_WEBRTC_DATA_CHANNEL_SERVICE = "org.anbox.webrtc.IDataProxyService";

    private static final int TRANSACTION_connect = IBinder.FIRST_CALL_TRANSACTION;

    private IBinder mService = null;
    private DataReadTask mDataReadTask = null;
    private ParcelFileDescriptor mFd = null;

    private String mConnectedChannel;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button sendBtn = findViewById(R.id.button);
        sendBtn.setOnClickListener(v -> {
            if (mFd == null) {
                Log.e(TAG, "Unavailable file descriptor");
                return;
            }

            TextView textSend = findViewById(R.id.textToSend);
            String text = textSend.getText().toString();
            if (text.length() == 0)
                return;

            sendData(text);
        });

        Button connectBtn = findViewById(R.id.connectBtn);
        connectBtn.setOnClickListener(v -> {
            TextView channel = findViewById(R.id.channelName);
            String channelName = channel.getText().toString().trim();
            if (channelName.length() == 0) {
                Toast.makeText(getApplicationContext(), "Channel name must no be empty", Toast.LENGTH_LONG).show();
                return;
            }
            readDataFromChannel(channelName);
        });

        mService = getDataProxyService();
        if (mService == null) {
            Toast.makeText(this, "Unable to find the service", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Unable to find the Service");
            return;
//            throw new IllegalStateException("Unable to find the service");
        } else {
            Log.i(TAG, "Service Found");
        }
        onNewIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDataReadTask != null) {
            mDataReadTask.terminate();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent != null) {
            String channelName = intent.getStringExtra("channelName");
            String data = intent.getStringExtra("data");

            // Connect data channel and send data on demand
            if (channelName != null && channelName.length() != 0) {
                readDataFromChannel(channelName);
                if (data != null && data.length() != 0) {
                    sendData(data);
                }
            }
        }
    }

    IBinder getDataProxyService() {
        // The `android.os.ServiceManager` is a hidden class and can not accessed directly
        // from normal application unless it's an system app. So either of the following two
        // ways can achieve what we want.
        //   1. build the application with AOSP, which enables people to import `android.os.ServiceManager`
        //   2. use reflection APIs as follows if developing the app in Android studio.
        IBinder service = null;
        try {
            Method method = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            service = (IBinder) method.invoke(null, ANBOX_WEBRTC_DATA_CHANNEL_SERVICE);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return service;
    }

    void readDataFromChannel(String channel) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton("Cancel", ((dialog, which) -> dialog.dismiss()));

        if (mConnectedChannel != null && mConnectedChannel.equals(channel))
            return;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ANBOX_WEBRTC_DATA_CHANNEL_INTERFACE);
            data.writeString(channel);
            mService.transact(TRANSACTION_connect, data, reply, 0);
            mFd = reply.readFileDescriptor();
            if (mFd.getFd() < 0) {
                Log.e(TAG, "Invalid file descriptor");
                return;
            }

            mConnectedChannel = channel;

            if (mDataReadTask != null && mDataReadTask.getStatus() == AsyncTask.Status.RUNNING) {
                mDataReadTask.terminate();
                mDataReadTask = null;
            }

            Toast.makeText(getApplicationContext(), "Channel '" + channel + "' is connected" , Toast.LENGTH_LONG).show();

            mDataReadTask = new DataReadTask(mFd, this);
            mDataReadTask.execute();
        } catch (RemoteException ex) { ///////
            Log.e(TAG, "Failed to connect data channel '" +  channel + "': " + ex.getMessage());
//            Toast.makeText(this, ex.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, ex.getLocalizedMessage());
//            showAlertDialog(ex.getLocalizedMessage());
//            showSnackbar(ex.getLocalizedMessage());
        } catch(NullPointerException exception) {
//            Toast.makeText(this, exception.getLocalizedMessage(), Toast.LENGTH_LONG).show();
//            showAlertDialog(exception.getLocalizedMessage());
//            showSnackbar(exception.getLocalizedMessage());
            Log.e(TAG, exception.getLocalizedMessage());
        } catch (Exception exception) {
            exception.printStackTrace();

        }
        finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void showAlertDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(message);
        builder.setMessage("An error occurred. Please try again.");

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showSnackbar(String message) {
        // Make sure you have a view with an ID to anchor the Snackbar. Typically, it could be a CoordinatorLayout or any other view in your layout.
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);

        // Add the Cancel action button
        snackbar.setAction("Cancel", v -> snackbar.dismiss());

        // Show the Snackbar
        snackbar.show();
    }

    @Override
    public void onDataRead(byte[] readBytes) {
        String text = new String(readBytes);
        TextView textView = findViewById(R.id.textReceived);
        textView.setText(textView.getText() + "\n"+ text);
        Log.i(TAG, "channel: "  + mConnectedChannel + " data: "  + text);
    }

    void sendData(String text) {
        OutputStream ostream = new FileOutputStream(mFd.getFileDescriptor());
        try {
            ostream.write(text.getBytes(), 0, text.length());
        } catch (IOException ex) {
            Log.i(TAG, "Failed to write data: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
