package com.hs2t.tinhtinh

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerHeaders: RecyclerView
    private lateinit var recyclerManualPackages: RecyclerView
    private lateinit var webhookUrlInput: TextInputEditText
    private lateinit var headerKeyInput: TextInputEditText
    private lateinit var headerValueInput: TextInputEditText
    private lateinit var manualPackageInput: TextInputEditText
    private lateinit var buttonAddHeader: Button
    private lateinit var buttonAddPackage: Button
    private lateinit var buttonSave: Button
    private lateinit var buttonTestWebhook: Button
    private lateinit var buttonSelectApps: Button
    private lateinit var buttonOpenNotificationAccess: Button
    private lateinit var buttonOpenBatterySettings: Button
    private lateinit var selectedAppsSummary: TextView
    private lateinit var setupWarning: TextView
    private lateinit var notificationAccessStatus: TextView
    private lateinit var batteryOptimizationStatus: TextView
    private lateinit var testResult: TextView
    private lateinit var checkboxDebug: MaterialCheckBox

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
        refreshSetupStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupStatus()
    }

    private fun initViews() {
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
        buttonSelectApps = findViewById(R.id.buttonSelectApps)
        buttonOpenNotificationAccess = findViewById(R.id.buttonOpenNotificationAccess)
        buttonOpenBatterySettings = findViewById(R.id.buttonOpenBatterySettings)
        selectedAppsSummary = findViewById(R.id.selectedAppsSummary)
        setupWarning = findViewById(R.id.setupWarning)
        notificationAccessStatus = findViewById(R.id.notificationAccessStatus)
        batteryOptimizationStatus = findViewById(R.id.batteryOptimizationStatus)
        testResult = findViewById(R.id.testResult)
        checkboxDebug = findViewById(R.id.checkboxDebug)

        appsAdapter = AppsAdapter { packageName, isSelected ->
            if (isSelected) {
                selectedApps.add(packageName)
                manualPackages.remove(packageName)
            } else {
                selectedApps.remove(packageName)
            }
            syncAppSelection(packageName, isSelected)
            manualPackagesAdapter.submitList(manualPackages.toList().sorted())
            updateSelectedAppsSummary()
            saveConfiguration(showToast = false)
        }

        headersAdapter = HeadersAdapter { position ->
            if (position in headers.indices) {
                headers.removeAt(position)
                headersAdapter.notifyItemRemoved(position)
            }
        }
        recyclerHeaders.layoutManager = LinearLayoutManager(this)
        recyclerHeaders.adapter = headersAdapter
        recyclerHeaders.isNestedScrollingEnabled = false

        manualPackagesAdapter = ManualPackagesAdapter(
            onDelete = { packageName ->
                manualPackages.remove(packageName)
                manualPackagesAdapter.submitList(manualPackages.toList().sorted())
                syncAppSelection(packageName, false)
                updateSelectedAppsSummary()
                saveConfiguration(showToast = false)
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
        headersAdapter.notifyDataSetChanged()

        // Load debug enabled
        val debugEnabled = prefs.getBoolean("debug_enabled", false)
        checkboxDebug.isChecked = debugEnabled
    }

    private fun loadInstalledApps() {
        val packageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        allApps.clear()
        apps.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName != this.packageName && allApps.none { it.packageName == packageName }) {
                val appName = resolveInfo.loadLabel(packageManager).toString()
                val appIcon = resolveInfo.loadIcon(packageManager)
                allApps.add(
                    AppInfo(
                        packageName = packageName,
                        name = appName,
                        icon = appIcon,
                        isSelected = selectedApps.contains(packageName) || manualPackages.contains(packageName)
                    )
                )
            }
        }

        allApps.sortBy { it.name.lowercase() }
        appsAdapter.submitList(allApps.toList())
        manualPackagesAdapter.submitList(manualPackages.toList().sorted())
        updateSelectedAppsSummary()
    }

    private fun syncAppSelection(packageName: String, isSelected: Boolean) {
        val appIndex = allApps.indexOfFirst { it.packageName == packageName }
        if (appIndex >= 0) {
            allApps[appIndex] = allApps[appIndex].copy(isSelected = isSelected)
            appsAdapter.submitList(allApps.toList())
        }
    }

    private fun updateSelectedAppsSummary() {
        val selectedNames = allApps
            .filter { selectedApps.contains(it.packageName) }
            .map { it.name }
            .sorted()

        val manualOnlyPackages = manualPackages
            .filter { packageName -> allApps.none { it.packageName == packageName } }
            .sorted()

        val summaryItems = selectedNames + manualOnlyPackages
        selectedAppsSummary.text = if (summaryItems.isEmpty()) {
            "Chưa chọn ứng dụng nào"
        } else {
            "Đã chọn ${summaryItems.size} ứng dụng: ${summaryItems.joinToString(", ")}"
        }
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabledListeners.contains(ComponentName(this, NotificationListener::class.java).flattenToString())
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun refreshSetupStatus() {
        val hasNotificationAccess = isNotificationAccessEnabled()
        val ignoresBatteryOptimization = isIgnoringBatteryOptimizations()

        notificationAccessStatus.text = if (hasNotificationAccess) {
            "Notification Access: Đã cấp quyền"
        } else {
            "Notification Access: Chưa cấp quyền. App sẽ không nhận được notification cho đến khi bạn bật quyền này."
        }

        batteryOptimizationStatus.text = if (ignoresBatteryOptimization) {
            "Battery Optimization: Đã bỏ tối ưu pin hoặc thiết bị không yêu cầu"
        } else {
            "Battery Optimization: Đang bị tối ưu pin. Một số máy có thể chặn app chạy ngầm hoặc làm listener bị ngắt."
        }

        val warnings = mutableListOf<String>()
        if (!hasNotificationAccess) {
            warnings.add("- Chưa cấp Notification Access")
        }
        if (!ignoresBatteryOptimization) {
            warnings.add("- Chưa tắt Battery Optimization cho app")
        }

        setupWarning.text = if (warnings.isEmpty()) {
            "Thiết lập chạy ngầm cơ bản đã đủ. Nếu dùng ROM như Xiaomi/Oppo/Vivo, vẫn nên bật thêm Auto Start thủ công."
        } else {
            "Cần hoàn tất các mục sau để app chạy ngầm ổn định:\n${warnings.joinToString("\n")}"
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun showAppsDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_select_apps)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val recyclerAppsDialog: RecyclerView = dialog.findViewById(R.id.recyclerAppsDialog)
        val buttonCloseAppsDialog: Button = dialog.findViewById(R.id.buttonCloseAppsDialog)

        recyclerAppsDialog.layoutManager = LinearLayoutManager(this)
        recyclerAppsDialog.adapter = appsAdapter
        recyclerAppsDialog.isNestedScrollingEnabled = true
        appsAdapter.submitList(allApps.toList())

        buttonCloseAppsDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupClickListeners() {
        buttonSelectApps.setOnClickListener {
            showAppsDialog()
        }

        buttonOpenNotificationAccess.setOnClickListener {
            openNotificationAccessSettings()
        }

        buttonOpenBatterySettings.setOnClickListener {
            openBatteryOptimizationSettings()
        }

        buttonAddHeader.setOnClickListener {
            val key = headerKeyInput.text?.toString()?.trim()
            val value = headerValueInput.text?.toString()?.trim()

            if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                headers.add(HeaderInfo(key, value))
                headersAdapter.notifyItemInserted(headers.size - 1)
                headerKeyInput.setText("")
                headerValueInput.setText("")
                saveConfiguration(showToast = false)
            }
        }

        buttonAddPackage.setOnClickListener {
            val packageName = manualPackageInput.text?.toString()?.trim()
            if (!packageName.isNullOrBlank()) {
                if (packageName.contains(".") && !selectedApps.contains(packageName) && !manualPackages.contains(packageName)) {
                    manualPackages.add(packageName)
                    manualPackagesAdapter.submitList(manualPackages.toList().sorted())
                    manualPackageInput.setText("")
                    syncAppSelection(packageName, true)
                    updateSelectedAppsSummary()
                    saveConfiguration(showToast = false)
                } else {
                    Toast.makeText(this, "Package name không hợp lệ hoặc đã tồn tại", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonSave.setOnClickListener {
            saveConfiguration()
        }

        buttonTestWebhook.setOnClickListener {
            saveConfiguration(showToast = false)
            testWebhook()
        }

        checkboxDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debug_enabled", isChecked).apply()
        }
    }

    private fun saveConfiguration(showToast: Boolean = true) {
        val webhookUrl = webhookUrlInput.text?.toString()?.trim()

        val selectedJson = JSONArray()
        selectedApps.forEach { selectedJson.put(it) }

        val manualJson = JSONArray()
        manualPackages.forEach { manualJson.put(it) }

        val headersJson = JSONArray()
        headers.forEach {
            val obj = JSONObject()
            obj.put("key", it.key)
            obj.put("value", it.value)
            headersJson.put(obj)
        }

        prefs.edit()
            .putString("selected_apps", selectedJson.toString())
            .putString("manual_packages", manualJson.toString())
            .putString("webhook_url", webhookUrl ?: "")
            .putString("headers", headersJson.toString())
            .putBoolean("debug_enabled", checkboxDebug.isChecked)
            .apply()

        if (showToast) {
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
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

            holder.appCheckBox.setOnCheckedChangeListener(null)
            holder.appCheckBox.isChecked = app.isSelected
            holder.appCheckBox.setOnCheckedChangeListener { _, isChecked ->
                val currentPosition = holder.adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    val currentApp = apps[currentPosition]
                    if (currentApp.isSelected != isChecked) {
                        onCheckedChanged(currentApp.packageName, isChecked)
                    }
                }
            }

            holder.itemView.setOnClickListener {
                holder.appCheckBox.toggle()
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
                val currentPosition = holder.adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onRemove(currentPosition)
                }
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
