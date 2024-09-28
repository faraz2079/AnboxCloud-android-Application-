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


package com.canonical.anbox.out_of_band_v2


import android.content.DialogInterface
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.canonical.anbox.out_of_band_v2.DataReadTask.DataReadListener
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException


class MainActivity : AppCompatActivity(), DataReadListener {

//    lateinit var data: Parcel
//    lateinit var reply: Parcel

    private val mService: IBinder? by lazy {
        try {
            val method = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
            return@lazy method.invoke(null, ANBOX_WEBRTC_DATA_CHANNEL_SERVICE) as IBinder
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }catch (e: NullPointerException){
            e.printStackTrace()
        }
        null
    }

    private var mDataReadTask: DataReadTask? = null
    private var mFd: ParcelFileDescriptor? = null
    private var mConnectedChannel: String? = null

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sendBtn = findViewById<Button>(R.id.button)
        sendBtn.setOnClickListener { v: View? ->
            if (mFd == null) {
                Log.e(TAG, "Unavailable file descriptor")
                return@setOnClickListener
            }
            val textSend = findViewById<TextView>(R.id.textToSend)
            val text = textSend.text.toString()
            if (text.isEmpty()) return@setOnClickListener
            sendData(text)
        }

        val connectBtn = findViewById<Button>(R.id.connectBtn)
        connectBtn.setOnClickListener { v: View? ->
            val channel = findViewById<TextView>(R.id.channelName)
            val channelName = channel.text.toString().trim { it <= ' ' }
            if (channelName.isEmpty()) {
                Toast.makeText(
                    applicationContext,
                    "Channel name must no be empty",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            readDataFromChannel(channelName)
        }

        if (mService == null) {
            Toast.makeText(this, "Unable to find the service", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Unable to find the Service")
            return
        } else {
            Toast.makeText(this, "Service Found", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Service Found")
        }

        onNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mDataReadTask != null) {
            mDataReadTask!!.terminate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val channelName = intent.getStringExtra("channelName")
        val data = intent.getStringExtra("data")

        // Connect data channel and send data on demand
        if (!channelName.isNullOrEmpty()) {
            readDataFromChannel(channelName)
            if (!data.isNullOrEmpty()) {
                sendData(data)
            } else {
                Log.d(TAG, "onNewIntent: Data of Channel is empty!")
            }
        } else {
            Log.d(TAG, "onNewIntent: ChannelName is empty!")
        }
    }

       private val dataProxyService: IBinder?
            get() {
                // The `android.os.ServiceManager` is a hidden class and can not accessed directly
                // from normal application unless it's an system app. So either of the following two
                // ways can achieve what we want.
                //   1. build the application with AOSP, which enables people to import `android.os.ServiceManager`
                //   2. use reflection APIs as follows if developing the app in Android studio.
                var service: IBinder? = null
                try {
                    val method = Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String::class.java)
                    service = method.invoke(null, ANBOX_WEBRTC_DATA_CHANNEL_SERVICE) as IBinder
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                } catch (e: ClassNotFoundException) {
                    e.printStackTrace()
                }

                return service
            }

    private fun readDataFromChannel(channel: String) {
               val builder = AlertDialog.Builder(this)
                builder.setNegativeButton(
                    "Cancel",
                    (DialogInterface.OnClickListener { dialog: DialogInterface, which: Int -> dialog.dismiss() })
                )

        if (mConnectedChannel != null && mConnectedChannel == channel) return

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(ANBOX_WEBRTC_DATA_CHANNEL_INTERFACE)
            data.writeString(channel)
            mService?.transact(TRANSACTION_connect, data, reply, 0)

            mFd = reply.readFileDescriptor()
            val fileDescriptor = mFd?.fd
            if (fileDescriptor != null && fileDescriptor < 0) {
                Log.e(TAG, "Invalid file descriptor")
                return
            }

            mConnectedChannel = channel

            if (mDataReadTask != null && mDataReadTask!!.status == AsyncTask.Status.RUNNING) {
                mDataReadTask!!.terminate()
                mDataReadTask = null
            }

            Toast.makeText(applicationContext, "Channel '$channel' is connected", Toast.LENGTH_LONG)
                .show()

            mDataReadTask = DataReadTask(mFd, this)
            mDataReadTask?.execute()
        } catch (ex: RemoteException) {
            Log.e(TAG, "Failed to connect data channel '" + channel + "': " + ex.message)
            Toast.makeText(this, ex.localizedMessage, Toast.LENGTH_LONG).show()
        } catch (exception: NullPointerException) {
            Toast.makeText(this, exception.localizedMessage, Toast.LENGTH_LONG).show()
            exception.printStackTrace()
        } catch (exception: Exception) {
            exception.printStackTrace()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // the data we get from the physical device -> text
    override fun onDataRead(readBytes: ByteArray) { //process the data here
        val text = String(readBytes)
        val textView = findViewById<TextView>(R.id.textReceived)
        textView.text = """
            ${textView.text}
            $text
            """.trimIndent()
        Log.i(TAG, "channel: $mConnectedChannel data: $text")
    }


    private fun sendData(text: String) {
        val ostream: OutputStream = FileOutputStream(mFd!!.fileDescriptor)
        try {
            ostream.write(text.toByteArray(), 0, text.length)
        } catch (ex: IOException) {
            Log.i(TAG, "Failed to write data: " + ex.message)
            ex.printStackTrace()
        }
    }


    companion object {
        private const val TAG = "DataChannel"

        private const val ANBOX_WEBRTC_DATA_CHANNEL_INTERFACE =
            "org.anbox.webrtc.IDataProxyService@1.0"
        private const val ANBOX_WEBRTC_DATA_CHANNEL_SERVICE = "org.anbox.webrtc.IDataProxyService"

        private const val TRANSACTION_connect = IBinder.FIRST_CALL_TRANSACTION
    }
}
