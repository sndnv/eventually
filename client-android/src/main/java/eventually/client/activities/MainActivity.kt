package eventually.client.activities

import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import eventually.client.R
import eventually.client.activities.fragments.TaskCalendarFragment
import eventually.client.activities.fragments.TaskListFragment
import eventually.client.activities.fragments.TaskListItemAdapter
import eventually.client.activities.fragments.TaskSummaryFragment
import eventually.client.activities.receivers.DismissReceiver
import eventually.client.activities.receivers.PostponeReceiver
import eventually.client.scheduling.NotificationManagerExtensions.createInstanceNotificationChannels
import eventually.client.scheduling.SchedulerService
import eventually.client.settings.Settings
import eventually.client.settings.Settings.getStatsEnabled

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var pager: ViewPager2

    private lateinit var notificationManager: NotificationManager

    private lateinit var dismissReceiver: DismissReceiver
    private lateinit var postponeReceiver: PostponeReceiver

    private lateinit var topAppBar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pager = findViewById(R.id.pager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): Fragment {
                return when (Position(position)) {
                    Position.Summary -> TaskSummaryFragment()
                    Position.List -> TaskListFragment()
                    Position.Calendar -> TaskCalendarFragment()
                }
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this@MainActivity)

        topAppBar = findViewById(R.id.topAppBar)

        topAppBar.subtitle = getString(R.string.main_top_app_bar_subtitle_summary)
        topAppBar.menu.findItem(R.id.task_search).isVisible = false
        topAppBar.menu.findItem(R.id.stats).isVisible = preferences.getStatsEnabled()

        topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.task_search -> true
                R.id.task_add -> {
                    startActivity(Intent(this@MainActivity, NewTaskActivity::class.java))
                    true
                }
                R.id.settings -> {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    true
                }
                R.id.stats -> {
                    startActivity(Intent(this@MainActivity, StatsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        val searchView = (topAppBar.menu.findItem(R.id.task_search).actionView as SearchView)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    val listView = this@MainActivity.findViewById<RecyclerView>(R.id.recycler_view)
                    (listView.adapter as TaskListItemAdapter).filter.filter(query)
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    val listView = this@MainActivity.findViewById<RecyclerView>(R.id.recycler_view)
                    (listView.adapter as TaskListItemAdapter).filter.filter(searchView.query)
                    return false
                }
            }
        )
        searchView.isIconified = false

        val tabs = findViewById<TabLayout>(R.id.tabs)
        TabLayoutMediator(tabs, pager) { tab, position ->
            val icon = when (Position(position)) {
                Position.Summary -> R.drawable.ic_summary
                Position.List -> R.drawable.ic_list
                Position.Calendar -> R.drawable.ic_calendar
            }

            tab.icon = ContextCompat.getDrawable(this, icon)
        }.attach()

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = Position(tab?.position ?: -1)

                if (position == Position.List) {
                    topAppBar.menu.findItem(R.id.task_search).isVisible = true
                } else {
                    topAppBar.menu.findItem(R.id.task_search).isVisible = false
                    topAppBar.collapseActionView()
                }

                if (position == Position.Summary) {
                    val intent = Intent(this@MainActivity, SchedulerService::class.java)
                    intent.action = SchedulerService.ActionEvaluate

                    startService(intent)
                }

                topAppBar.subtitle = getString(
                    when (position) {
                        Position.Summary -> R.string.main_top_app_bar_subtitle_summary
                        Position.List -> R.string.main_top_app_bar_subtitle_list
                        Position.Calendar -> R.string.main_top_app_bar_subtitle_calendar
                    }
                )
            }

            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })


        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createInstanceNotificationChannels(applicationContext)

        dismissReceiver = DismissReceiver()
        postponeReceiver = PostponeReceiver()

        registerReceiver(dismissReceiver, dismissReceiver.intentFilter)
        registerReceiver(postponeReceiver, postponeReceiver.intentFilter)

        startService(Intent(this, SchedulerService::class.java))
    }

    override fun onDestroy() {
        unregisterReceiver(dismissReceiver)
        unregisterReceiver(postponeReceiver)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(updated: SharedPreferences, key: String?) {
        if (key == Settings.Keys.StatsEnabled) {
            topAppBar.menu.findItem(R.id.stats).isVisible = updated.getStatsEnabled()
        }
    }

    private sealed class Position {
        object Summary : Position()
        object List : Position()
        object Calendar : Position()

        companion object {
            operator fun invoke(position: Int): Position =
                when (position) {
                    0 -> Summary
                    1 -> List
                    2 -> Calendar
                    else -> throw IllegalStateException("Unexpected position encountered: [$position]")
                }
        }
    }
}
