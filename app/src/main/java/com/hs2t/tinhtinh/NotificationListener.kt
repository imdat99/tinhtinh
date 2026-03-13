package com.hs2t.tinhtinh

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationListener : NotificationListenerService() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationListener", "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        Log.d("NotificationListener", "Notification from: $packageName")

        // Check if this app is in the selected list or manual packages
        val selectedApps = getAllSelectedApps()
        if (!selectedApps.contains(packageName)) {
            Log.d("NotificationListener", "App not selected, ignoring")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()
            ?: extras.getCharSequence("android.subText")?.toString()
            .orEmpty()

        if (title.isBlank() && text.isBlank()) {
            Log.d("NotificationListener", "Notification content is empty, ignoring")
            return
        }

        Log.d("NotificationListener", "Title: $title")
        Log.d("NotificationListener", "Text: $text")

        sendWebhook(packageName, title, text)
    }

    private fun getAllSelectedApps(): Set<String> {
        val result = mutableSetOf<String>()

        // Get selected apps from UI
        val selectedJson = prefs.getString("selected_apps", null)
        selectedJson?.let {
            try {
                val jsonArray = JSONArray(it)
                for (i in 0 until jsonArray.length()) {
                    result.add(jsonArray.getString(i))
                }
            } catch (e: Exception) {
                Log.e("NotificationListener", "Error parsing selected apps", e)
            }
        }

        // Get manual packages
        val manualJson = prefs.getString("manual_packages", null)
        manualJson?.let {
            try {
                val jsonArray = JSONArray(it)
                for (i in 0 until jsonArray.length()) {
                    result.add(jsonArray.getString(i))
                }
            } catch (e: Exception) {
                Log.e("NotificationListener", "Error parsing manual packages", e)
            }
        }

        Log.d("NotificationListener", "Selected apps: $result")
        return result
    }

    private fun sendWebhook(packageName: String, title: String, text: String) {
        val webhookUrl = prefs.getString("webhook_url", "") ?: ""
        if (webhookUrl.isBlank()) {
            Log.d("NotificationListener", "Webhook URL not set")
            return
        }

        try {
            val url = URL(webhookUrl)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")

                // Add custom headers
                val headersJson = prefs.getString("headers", null)
                headersJson?.let {
                    try {
                        val jsonArray = JSONArray(it)
                        for (i in 0 until jsonArray.length()) {
                            val headerObj = jsonArray.getJSONObject(i)
                            val key = headerObj.getString("key")
                            val value = headerObj.getString("value")
                            connection.setRequestProperty(key, value)
                            Log.d("NotificationListener", "Added header: $key")
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationListener", "Error parsing headers", e)
                    }
                }

                // Create JSON payload
                val payload = JSONObject()
                payload.put("package", packageName)
                payload.put("title", title)
                payload.put("text", text)
                payload.put("timestamp", System.currentTimeMillis())

                // Send request
                val outputStream = OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8)
                outputStream.write(payload.toString())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                Log.d("NotificationListener", "Webhook response: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    Log.d("NotificationListener", "Webhook sent successfully")
                } else {
                    Log.e("NotificationListener", "Webhook failed with response code: $responseCode")
                }

            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error sending webhook", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("NotificationListener", "Service destroyed")
    }
}
