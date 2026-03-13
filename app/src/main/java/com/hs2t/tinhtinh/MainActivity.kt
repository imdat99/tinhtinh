package com.hs2t.tinhtinh

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerApps: RecyclerView
    private lateinit var recyclerHeaders: RecyclerView
    private lateinit var webhookUrlInput: TextInputEditText
    private lateinit var headerKeyInput: TextInputEditText
    private lateinit var headerValueInput: TextInputEditText
    private lateinit var buttonAddHeader: MaterialButton
    private lateinit var buttonSave: MaterialButton

    private val allApps = mutableListOf<AppInfo>()
    private val selectedApps = mutableSetOf<String>()
    private val headers = mutableListOf<HeaderInfo>()

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var headersAdapter: HeadersAdapter

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
        webhookUrlInput = findViewById(R.id.webhookUrlInput)
        headerKeyInput = findViewById(R.id.headerKeyInput)
        headerValueInput = findViewById(R.id.headerValueInput)
        buttonAddHeader = findViewById(R.id.buttonAddHeader)
        buttonSave = findViewById(R.id.buttonSave)

        appsAdapter = AppsAdapter { packageName, isSelected ->
            if (isSelected) {
                selectedApps.add(packageName)
            } else {
                selectedApps.remove(packageName)
            }
        }
        recyclerApps.layoutManager = LinearLayoutManager(this)
        recyclerApps.adapter = appsAdapter

        headersAdapter = HeadersAdapter { position ->
            headers.removeAt(position)
            headersAdapter.notifyItemRemoved(position)
        }
        recyclerHeaders.layoutManager = LinearLayoutManager(this)
        recyclerHeaders.adapter = headersAdapter
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
        val apps = packageManager.queryIntentActivities(intent, 0)

        apps.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            // Skip this app itself
            if (packageName != packageName) {
                val appName = resolveInfo.loadLabel(packageManager).toString()
                val appIcon = resolveInfo.loadIcon(packageManager)
                allApps.add(AppInfo(packageName, appName, appIcon))
            }
        }

        // Sort by name
        allApps.sortBy { it.name.lowercase() }

        // Update adapter with selected state
        appsAdapter.submitList(allApps.map { it.copy(isSelected = selectedApps.contains(it.packageName)) })
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

        buttonSave.setOnClickListener {
            saveConfiguration()
        }
    }

    private fun saveConfiguration() {
        val webhookUrl = webhookUrlInput.text?.toString()?.trim()

        // Save selected apps
        val selectedJson = JSONArray()
        selectedApps.forEach { selectedJson.put(it) }
        prefs.edit().putString("selected_apps", selectedJson.toString()).apply()

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

        private var apps: List<AppInfo> = emptyList()

        fun submitList(newApps: List<AppInfo>) {
            apps = newApps
            notifyDataSetChanged()
        }

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
            val appName: TextView = itemView.findViewById(R.id.appName)
            val appPackage: TextView = itemView.findViewById(R.id.appPackage)
            val appCheckBox: CheckBox = itemView.findViewById(R.id.appCheckBox)
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
            holder.appCheckBox.isChecked = app.isSelected
            holder.appCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChanged(app.packageName, isChecked)
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
}
