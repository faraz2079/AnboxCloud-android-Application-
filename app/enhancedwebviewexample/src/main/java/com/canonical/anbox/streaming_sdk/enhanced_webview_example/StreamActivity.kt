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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.canonical.anbox.streaming_sdk.AnboxWebView
import java.io.BufferedInputStream
import java.io.IOException
import java.util.Objects

class StreamActivity : AppCompatActivity() {
    private val TAG = "AnboxEnhancedWebViewStreaming"

    private var mWebView: AnboxWebView? = null
    private val mRequestHideIME = false

    private inner class WebAppInterface(
        var mContext: Context, //getGetewayURL
        @get:JavascriptInterface var getewayURL: String?,
        @get:JavascriptInterface var aPIToken: String?,
        @get:JavascriptInterface var appName: String?
    )

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream)

        val intent = intent
        val gatewayURL = intent.getStringExtra(MainActivity.EXTRA_SIGNALING_URL)
        val apiToken = intent.getStringExtra(MainActivity.EXTRA_API_TOKEN)
        val appName = intent.getStringExtra(MainActivity.EXTRA_APP_NAME)

        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        requestRuntimePermissions(permissions)


        mWebView = findViewById<View>(R.id.webview) as AnboxWebView
        mWebView!!.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }
        }

        mWebView!!.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        val webSettings = mWebView!!.settings
        webSettings.javaScriptEnabled = true
//        webSettings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
        webSettings.loadWithOverviewMode = true

        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.mediaPlaybackRequiresUserGesture = false

        WebView.setWebContentsDebuggingEnabled(true)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(this))
            .build()

        mWebView!!.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (!request.isForMainFrame && Objects.requireNonNull(request.url.path)
                        !!.endsWith(".js")
                ) {
                    Log.d(TAG, " js file request need to set mime/type " + request.url.path)
                    try {
                        return WebResourceResponse(
                            "application/javascript", null,
                            BufferedInputStream(
                                view.context.assets.open(
                                    request.url.path!!.replace("/assets/", "")
                                )
                            )
                        )
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                return assetLoader.shouldInterceptRequest(request.url)
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                Log.d(TAG, "error: " + request.url)
            }

            override fun onReceivedSslError(
                view: WebView, handler: SslErrorHandler,
                error: SslError
            ) {
                handler.proceed()
                Log.d(TAG, "Ssl error: $error") //getPrimaryError()
            }
        }

        /* To Handle Javascript dialog, also grant permission to access camera or microphone if needed*/
        mWebView!!.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val requestedResources = request.resources
                val permissions = ArrayList<String>()
                val grantedPermissions = ArrayList<String>()
                for (i in requestedResources.indices) {
                    if (requestedResources[i] == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        permissions.add(Manifest.permission.RECORD_AUDIO)
                    } else if (requestedResources[i] == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        permissions.add(Manifest.permission.CAMERA)
                    }
                }

                for (i in permissions.indices) {
                    if (ContextCompat.checkSelfPermission(
                            applicationContext,
                            permissions[i]
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        continue
                    }
                    if (permissions[i] == Manifest.permission.RECORD_AUDIO) {
                        grantedPermissions.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    } else if (permissions[i] == Manifest.permission.CAMERA) {
                        grantedPermissions.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    }
                }

                if (grantedPermissions.isEmpty()) {
                    request.deny()
                } else {
                    var grantedPermissionsArray: Array<String?>? =
                        arrayOfNulls(grantedPermissions.size)
                    grantedPermissionsArray = grantedPermissions.toArray(grantedPermissionsArray)
                    request.grant(grantedPermissionsArray)
                }
            }
        }

        mWebView!!.addJavascriptInterface(
            WebAppInterface(this, gatewayURL, apiToken, appName),
            "Android"
        )
        mWebView!!.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        // 172.22.174.247
        // https://appassets.androidplatform.net/assets/index.html
    }

    override fun onBackPressed() {
        if (mWebView!!.canGoBack()) {
            mWebView!!.goBack()
        } else {
            super.onBackPressed()
        }
    }

    fun requestRuntimePermissions(permissions: Array<String>) {
        val permissionList: MutableList<String> = ArrayList()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionList.add(permission)
            }
        }
        if (!permissionList.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionList.toTypedArray<String>(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE: Int = 1
    }
}
