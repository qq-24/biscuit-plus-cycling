package net.telent.biscuit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import net.telent.biscuit.BiscuitService.LocalBinder
import net.telent.biscuit.ui.home.HomeFragment
import net.telent.biscuit.ui.history.HistoryFragment
import net.telent.biscuit.ui.sensors.SensorsFragment
import net.telent.biscuit.ui.sensors.SensorsViewModel
import net.telent.biscuit.ui.settings.SettingsFragment
import net.telent.biscuit.ui.track.TrackFragment
import net.telent.biscuit.ui.track.TrackViewModel
import org.osmdroid.config.Configuration


class BikeActivity : AppCompatActivity() {
    private var receiver: MainActivityReceiver? = null
    private var serviceIsBound = false
    private var mService: BiscuitService? = null
    private val mServiceIntent by lazy {
        Intent(applicationContext, BiscuitService::class.java)
    }

    // Fragment hide/show management
    private val fragments = mutableMapOf<Int, Fragment>()
    private var activeFragment: Fragment? = null
    
    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d(TAG, "Background location permission granted")
        } else {
            Log.w(TAG, "Background location permission denied")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocation || coarseLocation) {
            Log.d(TAG, "Location permission granted")
            // Only start service if recording is active
            val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_recording", false)) {
                ensureServiceRunning(mServiceIntent)
            }
            // Request background location separately for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            Log.w(TAG, "Location permission denied")
        }
    }

    override fun onCreateOptionsMenu(menu : Menu) :Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.action_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                startService(Intent(this, BiscuitService::class.java).putExtra("refresh_sensors", 1))
                Log.d(TAG, "request poll sensors again")
                true
            }
            R.id.action_power_off -> {
                val stopServiceIntent = Intent(this, BiscuitService::class.java).putExtra("stop_service", 1)
                Log.d(TAG, ""+stopServiceIntent)
                startService(stopServiceIntent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(R.layout.activity_bike)
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Start with theme default status bar color (initial fragment is HomeFragment, not map)
        applyThemeStatusBarColor()
        val containerView: View = findViewById(R.id.container)
        ViewCompat.setOnApplyWindowInsetsListener(containerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Top padding depends on active fragment: TrackFragment extends under status bar, others add top padding
            val topPadding = if (activeFragment is TrackFragment) 0 else systemBars.top
            v.setPadding(systemBars.left, topPadding, systemBars.right, systemBars.bottom)
            Log.d(TAG, "applyWindowInsets left=${systemBars.left} top=$topPadding right=${systemBars.right} bottom=${systemBars.bottom}")
            WindowInsetsCompat.CONSUMED
        }

        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        // Set up Fragment hide/show navigation
        navView.setOnItemSelectedListener { item ->
            switchFragment(item.itemId)
            true
        }

        // On process death + restore, Android re-creates old fragments automatically.
        // Clear them to avoid duplicates / stale visible fragments.
        if (savedInstanceState != null) {
            supportFragmentManager.fragments.forEach { frag ->
                supportFragmentManager.beginTransaction().remove(frag).commitNowAllowingStateLoss()
            }
        }

        // Show initial fragment (Home)
        switchFragment(R.id.navigation_home)

        this.applicationContext.let {
            Configuration.getInstance().load(it, PreferenceManager.getDefaultSharedPreferences(it))
        }

        receiver = MainActivityReceiver()
        registerReceiver(receiver, IntentFilter(BiscuitService.INTENT_NAME), Context.RECEIVER_NOT_EXPORTED)
        
        checkAndRequestPermissions()

        // 使用 OnBackPressedCallback 替代已废弃的 onBackPressed()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    // 恢复之前活跃的底部导航 Fragment
                    val navView: BottomNavigationView = findViewById(R.id.nav_view)
                    val currentMenuId = navView.selectedItemId
                    val tag = "frag_$currentMenuId"
                    val fragment = supportFragmentManager.findFragmentByTag(tag)
                    if (fragment != null) {
                        supportFragmentManager.beginTransaction().show(fragment).commit()
                        activeFragment = fragment
                    }
                } else {
                    // 无返回栈条目时，委托给系统默认返回行为
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }
    
    private fun switchFragment(menuItemId: Int) {
        val tag = "frag_$menuItemId"
        val transaction = supportFragmentManager.beginTransaction()

        // Hide current Fragment
        activeFragment?.let { transaction.hide(it) }

        var fragment = supportFragmentManager.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = createFragment(menuItemId)
            transaction.add(R.id.fragment_container, fragment, tag)
        } else {
            transaction.show(fragment)
        }

        activeFragment = fragment
        fragments[menuItemId] = fragment
        transaction.commit()

        // Switch status bar based on target Fragment type
        if (fragment is TrackFragment) {
            window.statusBarColor = Color.TRANSPARENT
        } else {
            applyThemeStatusBarColor()
        }
        // Re-apply window insets so top padding is recalculated
        findViewById<View>(R.id.container).requestApplyInsets()
    }

    private fun createFragment(menuItemId: Int): Fragment {
        return when (menuItemId) {
            R.id.navigation_home -> HomeFragment()
            R.id.navigation_track -> TrackFragment()
            R.id.navigation_sensors -> SensorsFragment()
            R.id.navigation_history -> HistoryFragment()
            R.id.navigation_settings -> SettingsFragment()
            else -> HomeFragment()
        }
    }

    /**
     * Navigate to HistoryDetailFragment from HistoryFragment.
     * Uses regular fragment transaction (add on top + back stack) since
     * we no longer use Navigation Component for bottom-nav tabs.
     */
    fun navigateToHistoryDetail(sessionStartMillis: Long) {
        val detailFragment = net.telent.biscuit.ui.history.HistoryDetailFragment().apply {
            arguments = Bundle().apply {
                putLong("session_start", sessionStartMillis)
            }
        }
        supportFragmentManager.beginTransaction()
            .hide(activeFragment!!)
            .add(R.id.fragment_container, detailFragment, "history_detail")
            .addToBackStack("history_detail")
            .commit()
        activeFragment = detailFragment
    }



    private fun applyTheme() {
        val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "dark")) {
            "light" -> setTheme(R.style.AppTheme_Light)
            "dark" -> setTheme(R.style.AppTheme_Dark)
            "oled" -> setTheme(R.style.AppTheme_OLED)
            else -> setTheme(R.style.AppTheme_Dark)
        }
    }

    /** Restore status bar to the theme's colorPrimaryDark */
    private fun applyThemeStatusBarColor() {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
        window.statusBarColor = typedValue.data
    }

    private fun checkAndRequestPermissions() {
        // Request notification permission for Android 13+ (required for foreground service notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        
        if (fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Already have location permission")
            // Only start service if recording is active
            val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_recording", false)) {
                ensureServiceRunning(mServiceIntent)
            }
            // Request background location separately for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            Log.d(TAG, "Requesting location permissions")
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun ensureServiceRunning(mServiceIntent: Intent) {
        if (!serviceIsBound) {
            Log.d(TAG, "Starting Service")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(mServiceIntent)
            else
                startService(mServiceIntent)

            if (!bindService(mServiceIntent, connection, Context.BIND_AUTO_CREATE)) {
                Log.d(TAG, "Failed to bind to service")
            } else {
                serviceIsBound = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_recording", false)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                ensureServiceRunning(mServiceIntent)
            }
        }
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            serviceIsBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceIsBound = false
        }
    }

    private fun unbindService() {
        if (serviceIsBound) {
            unbindService(connection)
            serviceIsBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        unbindService()
    }

    private inner class MainActivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive: action=${intent.action}, extras=${intent.extras?.keySet()}")
            
            val trackpoint: Trackpoint? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("trackpoint", Trackpoint::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("trackpoint")
            }
            
            val sensors: SensorSummaries? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("sensor_state", SensorSummaries::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("sensor_state")
            }
            
            Log.d(TAG, "received trackpoint=$trackpoint, sensors=$sensors")
            
            if (trackpoint != null) {
                val vm: TrackViewModel by viewModels()
                vm.move(trackpoint)
                vm.setPaused(intent.getBooleanExtra("is_paused", false))
                vm.setManualPaused(intent.getBooleanExtra("manual_paused", false))
                vm.setLowSpeedAlert(intent.getBooleanExtra("low_speed_alert", false))
                Log.d(TAG, "trackpoint applied to ViewModel: $trackpoint")
            } else {
                Log.w(TAG, "trackpoint is null!")
            }
            if (sensors != null) {
                val vm: SensorsViewModel by viewModels()
                vm.update(sensors)
                Log.d(TAG, "received new sensor state $sensors")
            } else {
                Log.w(TAG, "sensors is null!")
            }
        }
    }
    
    companion object {
        private val TAG = BikeActivity::class.java.simpleName
    }
}
