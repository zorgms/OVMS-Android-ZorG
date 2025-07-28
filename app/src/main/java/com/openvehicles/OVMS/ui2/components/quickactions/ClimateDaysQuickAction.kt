@file:Suppress("NAME_SHADOWING")

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

    QuickAction(ACTION_ID, R.drawable.calendar, apiServiceGetter,
        actionOnTint = R.color.colorDarkgreen,// R.attr.colorSecondaryContainer,
        actionOffTint = R.attr.colorSecondaryContainer, // R.color.cardview_dark_background,
        label = context?.getString(R.string.lb_booster_time_weekly)) {
    companion object {
        const val ACTION_ID = "climatedays"
    }

    @SuppressLint("CutPasteId")
    override fun onAction() {
        val context = context ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dlg_booster_days, null)
        val booster_ds: SwitcherView = dialogView.findViewById<View>(R.id.booster_ds) as? SwitcherView ?: return
        val booster_de: SwitcherView = dialogView.findViewById<View>(R.id.booster_de) as? SwitcherView ?: return

        val booster_start = getCarData()?.car_ac_booster_ds ?: 0
        var booster_end = getCarData()?.car_ac_booster_de?.minus(1) ?: 0
        if (booster_end < 0) {
            booster_end = 6
        }

        booster_ds.selected = booster_start
        booster_de.selected = booster_end

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lb_booster_day_sel)
            .setView(dialogView)
            .setNegativeButton(R.string.Cancel,null)
            .setPositiveButton(R.string.Set) { _, _ ->

                val booster_start = dialogView.findViewById<SwitcherView>(R.id.booster_ds)
                val booster_end = dialogView.findViewById<SwitcherView>(R.id.booster_de)

                val booster_nds = booster_start.selected
                val booster_nde = booster_end.selected

                val cmd = "7,metrics set xsq.climate.data 1,1,1,-1,$booster_nds,$booster_nde,-1"
                sendCommand(cmd)
            }
            .setNeutralButton(R.string.lb_booster_weekly_off) { _, _ ->
                if (getCarData()?.car_ac_booster_on == "yes") {
                    val cmd = "7,metrics set xsq.climate.data 1,1,2,-1,-1,-1,-1"
                    sendCommand(cmd)
                } else if (getCarData()?.car_ac_booster_weekly == "yes") {
                    val cmd = "7,metrics set xsq.climate.data 1,2,2,-1,-1,-1,-1"
                    sendCommand(cmd)
                }
            }
            .show()
    }

    override fun getStateFromCarData(): Boolean {
        return getCarData()?.car_ac_booster_weekly == "yes"
    }

    override fun commandsAvailable(): Boolean {
        return getCarData()?.car_type in listOf("SQ")
    }
}