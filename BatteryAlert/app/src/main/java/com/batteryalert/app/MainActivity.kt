package com.batteryalert.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.widget.doAfterTextChanged
import com.batteryalert.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AlertSettings
    private lateinit var testPlayer: AlertPlayer
    private var batteryReceiverRegistered = false

    private val presetChips = mapOf(
        R.id.chip10 to 10,
        R.id.chip15 to 15,
        R.id.chip20 to 20,
        R.id.chip25 to 25,
        R.id.chip30 to 30
    )

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showBattery(intent)
        }
    }

    private val soundPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            if (uri != null) {
                settings.soundUri = uri.toString()
                showSound()
            }
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Monitoring works even without the notification permission (the sound still plays).
            startMonitoring()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AlertSettings(this)
        testPlayer = AlertPlayer(this)

        setupThreshold()
        setupSound()
        setupDuration()
        setupSwitch()
        setupBatteryOptimization()
    }

    override fun onStart() {
        super.onStart()
        val sticky = ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        batteryReceiverRegistered = true
        sticky?.let { showBattery(it) }
    }

    override fun onResume() {
        super.onResume()
        binding.switchMonitor.isChecked = settings.enabled
        updateStatus()
        updateBatteryOptimizationCard()
        // OEM task killers may have stopped the service; bring it back whenever the app is opened.
        if (settings.enabled) startService()
    }

    override fun onStop() {
        testPlayer.stop()
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver)
            batteryReceiverRegistered = false
        }
        super.onStop()
    }

    // ---------- Threshold ----------

    private fun setupThreshold() {
        val current = settings.threshold
        val presetId = presetChips.entries.firstOrNull { it.value == current }?.key
        if (presetId != null) {
            binding.chipGroupThreshold.check(presetId)
        } else {
            binding.chipGroupThreshold.check(R.id.chipCustom)
            binding.layoutCustom.visibility = View.VISIBLE
            binding.etCustom.setText(current.toString())
        }

        binding.chipGroupThreshold.setOnCheckedStateChangeListener { _, checkedIds ->
            onThresholdChipChanged(checkedIds.firstOrNull())
        }
        binding.etCustom.doAfterTextChanged { text ->
            if (binding.chipCustom.isChecked) applyCustom(text?.toString())
        }
    }

    private fun onThresholdChipChanged(checkedId: Int?) {
        if (checkedId == null) return
        val preset = presetChips[checkedId]
        if (preset != null) {
            binding.layoutCustom.visibility = View.GONE
            binding.layoutCustom.error = null
            saveThreshold(preset)
        } else {
            binding.layoutCustom.visibility = View.VISIBLE
            val text = binding.etCustom.text?.toString().orEmpty()
            if (text.isEmpty()) {
                binding.etCustom.setText(settings.threshold.toString())
            } else {
                applyCustom(text)
            }
        }
    }

    private fun applyCustom(text: String?) {
        val value = text?.toIntOrNull()
        if (value == null || value !in 1..99) {
            binding.layoutCustom.error = getString(R.string.custom_error)
        } else {
            binding.layoutCustom.error = null
            saveThreshold(value)
        }
    }

    private fun saveThreshold(value: Int) {
        if (value != settings.threshold) {
            settings.threshold = value
            settings.armed = true // New threshold: allow a fresh alert.
        }
        updateStatus()
    }

    // ---------- Sound ----------

    private fun setupSound() {
        showSound()
        binding.btnChooseSound.setOnClickListener {
            val current = settings.soundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.choose_sound))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            }
            try {
                soundPicker.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.picker_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnTest.setOnClickListener {
            if (testPlayer.isPlaying) testPlayer.stop()
            else testPlayer.play(settings.soundUri, settings.durationSeconds)
        }
    }

    private fun showSound() {
        val uri = settings.soundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val title = try {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)
        } catch (e: Exception) {
            null
        }
        binding.tvSound.text = title ?: getString(R.string.default_sound)
    }

    // ---------- Duration ----------

    private fun setupDuration() {
        binding.toggleDuration.check(
            if (settings.durationSeconds == 10) R.id.btnDuration10 else R.id.btnDuration5
        )
        binding.toggleDuration.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) settings.durationSeconds = if (checkedId == R.id.btnDuration10) 10 else 5
        }
    }

    // ---------- Monitoring toggle ----------

    private fun setupSwitch() {
        binding.switchMonitor.isChecked = settings.enabled
        binding.switchMonitor.setOnClickListener {
            if (binding.switchMonitor.isChecked) requestStart() else stopMonitoring()
        }
    }

    private fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        settings.enabled = true
        settings.armed = true
        if (!startService()) {
            settings.enabled = false
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
        binding.switchMonitor.isChecked = settings.enabled
        updateStatus()
    }

    private fun stopMonitoring() {
        settings.enabled = false
        stopService(Intent(this, BatteryMonitorService::class.java))
        updateStatus()
    }

    private fun startService(): Boolean = try {
        ContextCompat.startForegroundService(this, Intent(this, BatteryMonitorService::class.java))
        true
    } catch (e: Exception) {
        false
    }

    private fun updateStatus() {
        binding.tvStatus.text = if (settings.enabled) {
            getString(R.string.status_on, settings.threshold)
        } else {
            getString(R.string.status_off)
        }
    }

    private fun showBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        binding.tvBatteryPercent.text = getString(R.string.percent_format, level * 100 / scale)
        binding.tvBatteryState.setText(if (plugged) R.string.charging else R.string.on_battery)
    }

    // ---------- Battery optimization (OPPO / ColorOS and other aggressive OEMs) ----------

    private fun setupBatteryOptimization() {
        binding.btnOptimization.setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        binding.btnAppSettings.setOnClickListener {
            openSafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBatteryOptimizationCard() {
        val pm = getSystemService(PowerManager::class.java)
        val exempt = pm.isIgnoringBatteryOptimizations(packageName)
        binding.btnOptimization.isEnabled = !exempt
        binding.btnOptimization.setText(if (exempt) R.string.optimization_done else R.string.optimization_button)
    }
}
