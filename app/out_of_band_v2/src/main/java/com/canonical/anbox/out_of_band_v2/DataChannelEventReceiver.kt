package com.canonical.anbox.out_of_band_v2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log


class DataChannelEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras
        val event = extras!!.getString("event")
        val names = extras.getStringArray("data-channel-names")
        Log.i(
            TAG, "Faraz channels: [" + TextUtils.join(
                ",",
                names!!
            ) + "] event type: " + event
        )
    }

    companion object {
        private const val TAG = "EventReceiver"
    }
}