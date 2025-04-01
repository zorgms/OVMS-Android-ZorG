package com.openvehicles.OVMS.ui2.components.quickactions

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.openvehicles.OVMS.R
import com.openvehicles.OVMS.api.ApiService
import com.openvehicles.OVMS.ui.witdet.SwitcherView

/**
 * Quick action handling climate control
 */
class ClimateDaysQuickAction(apiServiceGetter: () -> ApiService?, context: Context? = null) :

    QuickAction(ACTION_ID, R.drawable.ic_action_bookmark_add, apiServiceGetter,
        actionOnTint = R.attr.colorSecondaryContainer,
        actionOffTint = R.color.cardview_dark_background,
        label = context?.getString(R.string.service_notification_title)) {
    companion object {
        const val ACTION_ID = "climatedays"
    }

    @SuppressLint("CutPasteId")
    override fun onAction() {
        val context = context ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dlg_booster_days, null)
        val booster_ds = dialogView.findViewById<View>(R.id.booster_ds) as SwitcherView?
        val booster_de = dialogView.findViewById<View>(R.id.booster_de) as SwitcherView?

        val booster_start = String.format("%s",getCarData()?.car_booster_ds)
        val booster_end = String.format("%s", getCarData()?.car_booster_de?.minus(1))

        booster_ds!!.selected = booster_start.toInt()
        booster_de!!.selected = booster_end.toInt()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lb_booster_day_sel)
            .setView(dialogView)
            .setNegativeButton(R.string.Cancel, null)
            .setPositiveButton(R.string.Set) { _, _ ->

                val booster_start = dialogView.findViewById<SwitcherView>(R.id.booster_ds)
                val booster_end = dialogView.findViewById<SwitcherView>(R.id.booster_de)

                val booster_nds = booster_start.selected
                val booster_nde = booster_end.selected

                val cmd = "7,me set xsq.booster.data 1,1,1,-1,$booster_nds,$booster_nde,-1"
                sendCommand(cmd)
            }
            .show()
    }

    override fun getStateFromCarData(): Boolean {
        return getCarData()?.car_booster_weekly == "on"
    }

    override fun commandsAvailable(): Boolean {
        return getCarData()?.car_type in listOf("SQ")
    }
}