package com.take2.chainarrator.ui

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.take2.chainarrator.data.Settings
import com.take2.chainarrator.databinding.ActivityListBinding
import com.take2.chainarrator.databinding.ItemAppBinding

/**
 * Lets the user say which app to narrate.
 *
 * The app deliberately does not hard-code Chai's package name. Package names
 * change between builds and regions, and a wrong guess would leave the narrator
 * silent with no explanation. Anything whose name looks like Chai is floated to
 * the top of the list instead, and the user confirms.
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settings = Settings.get(this)
        binding.listHint.text =
            "The narrator listens to this app only. Everything else on your phone is ignored, " +
            "including anything you type."

        val entries = loadLaunchableApps()
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = AppAdapter(entries) { entry ->
            settings.targetPackage = entry.packageName
            settings.targetLabel = entry.label
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val packageManager = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION")
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(launcherIntent, 0)

        val selected = settings.targetPackage

        return resolved
            .asSequence()
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                if (activityInfo.packageName == packageName) return@mapNotNull null

                AppEntry(
                    packageName = activityInfo.packageName,
                    label = info.loadLabel(packageManager).toString(),
                    icon = info.loadIcon(packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<AppEntry> { it.packageName == selected }
                    .thenByDescending { it.looksLikeChai }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }

    data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable?
    ) {
        val looksLikeChai: Boolean
            get() = label.contains("chai", ignoreCase = true) ||
                packageName.contains("chai", ignoreCase = true)
    }

    private inner class AppAdapter(
        private val items: List<AppEntry>,
        private val onPick: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.Holder>() {

        inner class Holder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.binding.appLabel.text = entry.label
            holder.binding.appPackage.text = entry.packageName
            holder.binding.appIcon.setImageDrawable(entry.icon)
            holder.binding.appSelected.visibility =
                if (entry.packageName == settings.targetPackage) android.view.View.VISIBLE
                else android.view.View.GONE
            holder.binding.root.setOnClickListener { onPick(entry) }
        }
    }
}
