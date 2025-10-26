package com.openvehicles.OVMS.appwidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.openvehicles.OVMS.R
import com.openvehicles.OVMS.entities.StoredCommand
import com.openvehicles.OVMS.ui.utils.Database

/**
 * Configuration activity for ScheduledCommandWidget.
 * Allows user to select:
 * - Time (hour:minute) for daily execution
 * - Command from stored commands
 */
class ScheduledCommandWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var timePicker: TimePicker
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    
    private lateinit var database: Database
    private var storedCommands: List<StoredCommand> = emptyList()
    private var selectedCommand: StoredCommand? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set result to CANCELED in case user backs out
        setResult(Activity.RESULT_CANCELED)
        
        setContentView(R.layout.activity_scheduled_command_widget_config)
        supportActionBar?.title = getString(R.string.configure_scheduled_widget)
        
        // Get widget ID from intent
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        database = Database(this)
        
        timePicker = findViewById(R.id.time_picker)
        timePicker.setIs24HourView(true)
        
        recyclerView = findViewById(R.id.command_list)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        
        loadStoredCommands()
        setupRecyclerView()
        setupButtons()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun loadStoredCommands() {
        storedCommands = database.getStoredCommands()
    }

    private fun setupRecyclerView() {
        val adapter = CommandSelectionAdapter(storedCommands) { command ->
            selectedCommand = command
            Toast.makeText(
                this,
                getString(R.string.command_selected, command.title),
                Toast.LENGTH_SHORT
            ).show()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        btnSave.setOnClickListener {
            saveConfiguration()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveConfiguration() {
        if (selectedCommand == null) {
            Toast.makeText(
                this,
                R.string.please_select_command,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        val hour = timePicker.hour
        val minute = timePicker.minute
        
        try {
            // Save widget configuration
            ScheduledCommandWidget.saveWidgetConfig(
                this,
                appWidgetId,
                selectedCommand!!.command,
                selectedCommand!!.title,
                hour,
                minute
            )
            
            // Directly update the widget
            val appWidgetManager = AppWidgetManager.getInstance(this)
            ScheduledCommandWidget().apply {
                onUpdate(this@ScheduledCommandWidgetConfigActivity, appWidgetManager, intArrayOf(appWidgetId))
            }
            
            // Return success
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultValue)
            
            Toast.makeText(
                this,
                getString(R.string.widget_configured_successfully),
                Toast.LENGTH_SHORT
            ).show()
            
            finish()
        } catch (e: Exception) {
            android.util.Log.e("ScheduledWidget", "Error saving configuration", e)
            Toast.makeText(
                this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Adapter for displaying stored commands in RecyclerView
     */
    private inner class CommandSelectionAdapter(
        private val commands: List<StoredCommand>,
        private val onCommandSelected: (StoredCommand) -> Unit
    ) : RecyclerView.Adapter<CommandSelectionAdapter.ViewHolder>() {

        private var selectedPosition = RecyclerView.NO_POSITION

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val radioButton: android.widget.RadioButton = view.findViewById(R.id.radio_button)
            val textTitle: android.widget.TextView = view.findViewById(R.id.text_title)
            val textCommand: android.widget.TextView = view.findViewById(R.id.text_command)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_command_selection, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val command = commands[position]
            holder.textTitle.text = command.title
            holder.textCommand.text = command.command
            holder.radioButton.isChecked = position == selectedPosition
            
            holder.itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = holder.adapterPosition
                
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                
                onCommandSelected(command)
            }
            
            holder.radioButton.setOnClickListener {
                holder.itemView.performClick()
            }
        }

        override fun getItemCount() = commands.size
    }
}
