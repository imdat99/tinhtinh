package com.hs2t.tinhtinh

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerApps: RecyclerView
    private lateinit var recyclerHeaders: RecyclerView
    private lateinit var recyclerManualPackages: RecyclerView
    private lateinit var webhookUrlInput: TextInputEditText
    private lateinit var headerKeyInput: TextInputEditText
    private lateinit var headerValueInput: TextInputEditText
    private lateinit var manualPackageInput: TextInputEditText
    private lateinit var buttonAddHeader: MaterialButton
    private lateinit var buttonAddPackage: MaterialButton
    private lateinit var buttonSave: MaterialButton
    private lateinit var buttonTestWebhook: MaterialButton
    private lateinit var testResult: TextView

    private val allApps = mutableListOf<AppInfo>()
    private val selectedApps = mutableSetOf<String>()
    private val headers = mutableListOf<HeaderInfo>()
    private val manualPackages = mutableSetOf<String>()

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var headersAdapter: HeadersAdapter
    private lateinit var manualPackagesAdapter: ManualPackagesAdapter

    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSavedData()
        loadInstalledApps()
        setupClickListeners()
    }

    private fun initViews() {
        recyclerApps = findViewById(R.id.recyclerApps)
        recyclerHeaders = findViewById(R.id.recyclerHeaders)
        recyclerManualPackages = findViewById(R.id.recyclerManualPackages)
        webhookUrlInput = findViewById(R.id.webhookUrlInput)
        headerKeyInput = findViewById(R.id.headerKeyInput)
        headerValueInput = findViewById(R.id.headerValueInput)
        manualPackageInput = findViewById(R.id.manualPackageInput)
        buttonAddHeader = findViewById(R.id.buttonAddHeader)
        buttonAddPackage = findViewById(R.id.buttonAddPackage)
        buttonSave = findViewById(R.id.buttonSave)
        buttonTestWebhook = findViewById(R.id.buttonTestWebhook)
        testResult = findViewById(R.id.testResult)

        appsAdapter = AppsAdapter { packageName, isSelected ->
            if (isSelected) {
                selectedApps.add(packageName)
                manualPackages.remove(packageName)
            } else {
                selectedApps.remove(packageName)
            }
            // Update UI state in data list
            val appIndex = allApps.indexOfFirst { it.packageName == packageName }
            if (appIndex >= 0) {
                allApps[appIndex] = allApps[appIndex].copy(isSelected = isSelected)
                appsAdapter.notifyItemChanged(appIndex)
            }
            manualPackagesAdapter.notifyDataSetChanged()
        }
        recyclerApps.layoutManager = LinearLayoutManager(this)
        recyclerApps.adapter = appsAdapter

        headersAdapter = HeadersAdapter { position ->
            headers.removeAt(position)
            headersAdapter.notifyItemRemoved(position)
        }
        recyclerHeaders.layoutManager = LinearLayoutManager(this)
        recyclerHeaders.adapter = headersAdapter
        recyclerHeaders.isNestedScrollingEnabled = false

        manualPackagesAdapter = ManualPackagesAdapter(
            onDelete = { packageName ->
                manualPackages.remove(packageName)
                manualPackagesAdapter.notifyDataSetChanged()
                // Update apps list if needed
                val appIndex = allApps.indexOfFirst { it.packageName == packageName }
                if (appIndex >= 0) {
                    allApps[appIndex] = allApps[appIndex].copy(isSelected = false)
                    appsAdapter.notifyItemChanged(appIndex)
                }
            }
        )
        recyclerManualPackages.layoutManager = LinearLayoutManager(this)
        recyclerManualPackages.adapter = manualPackagesAdapter
        recyclerManualPackages.isNestedScrollingEnabled = false
    }

    private fun loadSavedData() {
        // Load selected apps
        val selectedJson = prefs.getString("selected_apps", null)
        selectedJson?.let {
            val jsonArray = JSONArray(it)
            for (i in 0 until jsonArray.length()) {
                selectedApps.add(jsonArray.getString(i))
            }
        }

        // Load manual packages
        val manualJson = prefs.getString("manual_packages", null)
        manualJson?.let {
            val jsonArray = JSONArray(it)
            for (i in 0 until jsonArray.length()) {
                manualPackages.add(jsonArray.getString(i))
            }
        }

        // Load webhook URL
        val webhookUrl = prefs.getString("webhook_url", null)
        webhookUrl?.let {
            webhookUrlInput.setText(it)
        }

        // Load headers
        val headersJson = prefs.getString("headers", null)
        headersJson?.let {
            val jsonArray = JSONArray(it)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                headers.add(HeaderInfo(obj.getString("key"), obj.getString("value")))
            }
        }
    }

    private fun loadInstalledApps() {
        val packageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        apps.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            // Skip this app itself
            if (packageName != this.packageName) {
                val appName = resolveInfo.loadLabel(packageManager).toString()
                val appIcon = resolveInfo.loadIcon(packageManager)
                allApps.add(AppInfo(packageName, appName, appIcon))
            }
        }

        // Sort by name
        allApps.sortBy { it.name.lowercase() }

        // Update adapter with selected state
        appsAdapter.submitList(allApps.map {
            it.copy(isSelected = selectedApps.contains(it.packageName) || manualPackages.contains(it.packageName))
        })
        manualPackagesAdapter.submitList(manualPackages.toList())
    }

    private fun setupClickListeners() {
        buttonAddHeader.setOnClickListener {
            val key = headerKeyInput.text?.toString()?.trim()
            val value = headerValueInput.text?.toString()?.trim()

            if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                headers.add(HeaderInfo(key, value))
                headersAdapter.notifyItemInserted(headers.size - 1)
                headerKeyInput.setText("")
                headerValueInput.setText("")
            }
        }

        buttonAddPackage.setOnClickListener {
            val packageName = manualPackageInput.text?.toString()?.trim()
            if (!packageName.isNullOrBlank()) {
                // Validate package name format
                if (packageName.contains(".") && !selectedApps.contains(packageName)) {
                    manualPackages.add(packageName)
                    manualPackagesAdapter.notifyItemInserted(manualPackages.size - 1)
                    manualPackageInput.setText("")

                    // Update apps list if this package exists
                    val appIndex = allApps.indexOfFirst { it.packageName == packageName }
                    if (appIndex >= 0) {
                        allApps[appIndex] = allApps[appIndex].copy(isSelected = true)
                        appsAdapter.notifyItemChanged(appIndex)
                    }
                } else {
                    Toast.makeText(this, "Package name không hợp lệ hoặc đã tồn tại", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonSave.setOnClickListener {
            saveConfiguration()
        }

        buttonTestWebhook.setOnClickListener {
            testWebhook()
        }
    }

    private fun saveConfiguration() {
        val webhookUrl = webhookUrlInput.text?.toString()?.trim()

        // Save selected apps
        val selectedJson = JSONArray()
        selectedApps.forEach { selectedJson.put(it) }
        prefs.edit().putString("selected_apps", selectedJson.toString()).apply()

        // Save manual packages
        val manualJson = JSONArray()
        manualPackages.forEach { manualJson.put(it) }
        prefs.edit().putString("manual_packages", manualJson.toString()).apply()

        // Save webhook URL
        prefs.edit().putString("webhook_url", webhookUrl ?: "").apply()

        // Save headers
        val headersJson = JSONArray()
        headers.forEach {
            val obj = JSONObject()
            obj.put("key", it.key)
            obj.put("value", it.value)
            headersJson.put(obj)
        }
        prefs.edit().putString("headers", headersJson.toString()).apply()

        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }

    private fun testWebhook() {
        val webhookUrl = webhookUrlInput.text?.toString()?.trim()
        if (webhookUrl.isNullOrBlank()) {
            Toast.makeText(this, "Vui lòng nhập Webhook URL", Toast.LENGTH_SHORT).show()
            return
        }

        testResult.visibility = View.VISIBLE
        testResult.text = "Đang gửi..."

        Thread {
            try {
                val url = URL(webhookUrl)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")

                    // Add custom headers
                    headers.forEach { header ->
                        connection.setRequestProperty(header.key, header.value)
                    }

                    // Create test payload
                    val payload = JSONObject()
                    payload.put("package", "com.test")
                    payload.put("title", "Test Notification")
                    payload.put("text", "Đây là notification test từ Tinh Tinh app")
                    payload.put("timestamp", System.currentTimeMillis())
                    payload.put("test", true)

                    // Send request
                    val outputStream = OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8)
                    outputStream.write(payload.toString())
                    outputStream.flush()
                    outputStream.close()

                    val responseCode = connection.responseCode
                    runOnUiThread {
                        if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                            testResult.text = "✓ Thành công! Response code: $responseCode"
                            testResult.setTextColor(getColor(android.R.color.holo_green_dark))
                        } else {
                            testResult.text = "✗ Lỗi! Response code: $responseCode"
                            testResult.setTextColor(getColor(android.R.color.holo_red_dark))
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    testResult.text = "✗ Lỗi: ${e.message}"
                    testResult.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }.start()
    }

    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable,
        val isSelected: Boolean = false
    )

    data class HeaderInfo(
        val key: String,
        val value: String
    )

    inner class AppsAdapter(
        private val onCheckedChanged: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

        private val apps = mutableListOf<AppInfo>()

        fun submitList(newApps: List<AppInfo>) {
            apps.clear()
            apps.addAll(newApps)
            notifyDataSetChanged()
        }

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
            val appName: TextView = itemView.findViewById(R.id.appName)
            val appPackage: TextView = itemView.findViewById(R.id.appPackage)
            val appCheckBox: MaterialCheckBox = itemView.findViewById(R.id.appCheckBox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            holder.appIcon.setImageDrawable(app.icon)
            holder.appName.text = app.name
            holder.appPackage.text = app.packageName

            // Use tag to prevent recursive calls
            holder.appCheckBox.tag = position
            holder.appCheckBox.isChecked = app.isSelected
            holder.appCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
                val currentPosition = buttonView.tag as? Int ?: return@setOnCheckedChangeListener
                if (currentPosition == position) {
                    onCheckedChanged(app.packageName, isChecked)
                }
            }
        }

        override fun getItemCount() = apps.size
    }

    inner class HeadersAdapter(
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<HeadersAdapter.HeaderViewHolder>() {

        inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val headerKey: TextView = itemView.findViewById(R.id.headerKey)
            val headerValue: TextView = itemView.findViewById(R.id.headerValue)
            val buttonRemove: ImageView = itemView.findViewById(R.id.buttonRemoveHeader)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_header, parent, false)
            return HeaderViewHolder(view)
        }

        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
            val header = headers[position]
            holder.headerKey.text = header.key
            holder.headerValue.text = header.value
            holder.buttonRemove.setOnClickListener {
                onRemove(position)
            }
        }

        override fun getItemCount() = headers.size
    }

    inner class ManualPackagesAdapter(
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<ManualPackagesAdapter.PackageViewHolder>() {

        private val packages = mutableListOf<String>()

        fun submitList(newPackages: List<String>) {
            packages.clear()
            packages.addAll(newPackages)
            notifyDataSetChanged()
        }

        inner class PackageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val packageText: TextView = itemView.findViewById(R.id.packageText)
            val buttonRemove: ImageView = itemView.findViewById(R.id.buttonRemovePackage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_manual_package, parent, false)
            return PackageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
            val packageName = packages[position]
            holder.packageText.text = packageName
            holder.buttonRemove.setOnClickListener {
                onDelete(packageName)
            }
        }

        override fun getItemCount() = packages.size
    }
}
