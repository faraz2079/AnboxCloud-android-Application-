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
package com.canonical.anbox.streaming_sdk.enhanced_webview_example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val launchIntent = intent
        val apiToken = launchIntent.getStringExtra("api_token")
        val gatewayURL = launchIntent.getStringExtra("gateway_url")
        val appName = launchIntent.getStringExtra("app_name")
        val useInsecureTLS = launchIntent.getBooleanExtra("use_insecure_tls", false)

        if (!apiToken.isNullOrEmpty()) {
            val apiTokenBox = findViewById<EditText>(R.id.api_token)
            apiTokenBox.setText(apiToken)
        }
        if (!gatewayURL.isNullOrEmpty()) {
            val gatewayURLBox = findViewById<EditText>(R.id.gateway_url)
            gatewayURLBox.setText(gatewayURL)
        }
        if (!appName.isNullOrEmpty()) {
            val appNameBox = findViewById<EditText>(R.id.app_name)
            appNameBox.setText(appName)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> Toast.makeText(this,"volumeDownPressed" , Toast.LENGTH_SHORT).show()
            KeyEvent.KEYCODE_VOLUME_UP -> Toast.makeText(this,"volumeUpPressed" , Toast.LENGTH_SHORT).show()
        }
        Log.d("Faraz", "onKeyDown: $keyCode")
        return super.onKeyDown(keyCode, event)
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun startStreaming(view: View?) {
        val apiTokenBox = findViewById<EditText>(R.id.api_token)
        val gatewayURLBox = findViewById<EditText>(R.id.gateway_url)
        val appNameBox = findViewById<EditText>(R.id.app_name)

        val apiToken = apiTokenBox.text.toString()
        var gatewayURL = gatewayURLBox.text.toString()
        val appName = appNameBox.text.toString()

        if (apiToken.isEmpty() || gatewayURL.isEmpty() || appName.isEmpty()) {
            Toast.makeText(
                this,
                "Missing gateway URL, API token or application name",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // In case of the given URL contains a trailing slash, we get rid of it
        // since it potentially causes IOException when talking to stream gateway.
        if (gatewayURL[gatewayURL.length - 1] == '/') {
            gatewayURL = gatewayURL.substring(0, gatewayURL.length - 1)
        }

        val intent = Intent(this@MainActivity, StreamActivity::class.java)
        intent.putExtra(EXTRA_SIGNALING_URL, gatewayURL)
        intent.putExtra(EXTRA_API_TOKEN, apiToken)
        intent.putExtra(EXTRA_APP_NAME, appName)
        startActivity(intent)
    }

    companion object {
        private val LOG_TAG: String = MainActivity::class.java.simpleName

        const val EXTRA_SIGNALING_URL
                : String = "com.canonical.anbox.streaming.sdk.webview_example.EXTRA_SIGNALING_URL"
        const val EXTRA_API_TOKEN
                : String = "com.canonical.anbox.streaming.sdk.webview_example.EXTRA_API_TOKEN"
        const val EXTRA_APP_NAME
                : String = "com.canonical.anbox.streaming.sdk.webview_example.EXTRA_APP_NAME"
    }
}


