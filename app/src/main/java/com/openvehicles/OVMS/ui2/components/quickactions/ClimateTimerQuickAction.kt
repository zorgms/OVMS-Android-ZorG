package com.openvehicles.OVMS.ui2.components.quickactions

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.openvehicles.OVMS.R
import com.openvehicles.OVMS.api.ApiService
import com.openvehicles.OVMS.ui.witdet.SlideNumericView
import com.openvehicles.OVMS.ui.witdet.SwitcherView

/**
 * Quick action handling climate control
 */
class ClimateTimerQuickAction(apiServiceGetter: () -> ApiService?, context: Context? = null) :

    QuickAction(ACTION_ID, R.drawable.heat_cool_w, apiServiceGetter,
        actionOnTint = R.color.colorDarkgreen,// R.attr.colorSecondaryContainer,
        actionOffTint = R.attr.colorSecondaryContainer, // R.color.cardview_dark_background,
        label = context?.getString(R.string.lb_booster_time)) {
    companion object {
        const val ACTION_ID = "climatetimer"
    }

    @SuppressLint("CutPasteId")
    override fun onAction() {
        val context = context ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dlg_booster_time, null)
        val booster_h = dialogView.findViewById<View>(R.id.booster_time_hour) as SlideNumericView?
        val booster_m = dialogView.findViewById<View>(R.id.booster_time_min) as SlideNumericView?
        val booster_sel = dialogView.findViewById<View>(R.id.booster_SwitcherView) as SwitcherView?

        val booster_weekly = getCarData()?.car_ac_booster_weekly
        val timeraw = getCarData()?.car_ac_booster_time?.split("")
        val time_h = String.format("%s%s", timeraw?.get(1), timeraw?.get(2))
        val time_m = String.format("%s%s", timeraw?.get(3), timeraw?.get(4))
        val booster_start = String.format("%s",getCarData()?.car_ac_booster_ds)
        val booster_end = String.format("%s", getCarData()?.car_ac_booster_de?.minus(1))
        val bdt = getCarData()?.car_ac_booster_bdt

        booster_h!!.value = time_h.toInt()
        booster_m!!.value = time_m.toInt()
        booster_sel!!.selected = bdt!!.toInt()
        val state_weekly = if(booster_weekly == "yes") "1" else "2"


        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.climate_control)
            .setView(dialogView)
            .setNegativeButton(R.string.Cancel,null)
            .setPositiveButton(R.string.Set) { _, _ ->

                val booster_hd = dialogView.findViewById<SlideNumericView>(R.id.booster_time_hour)
                val booster_md = dialogView.findViewById<SlideNumericView>(R.id.booster_time_min)
                val booster_sel_d = dialogView.findViewById<SwitcherView>(R.id.booster_SwitcherView)

                booster_hd?.let {
                    booster_md?.let {
                        booster_sel_d?.let {
                            val booster_h_val = if (booster_hd.value < 10) String.format("0%d", booster_hd.value) else booster_hd.value
                            val booster_m_val = if (booster_md.value < 10) String.format("0%d", booster_md.value) else booster_md.value
                            val booster_btdsel_val = booster_sel_d.selected
                            val cmd = "7,metrics set xsq.booster.data 1,1,$state_weekly,$booster_h_val$booster_m_val,$booster_start,$booster_end,$booster_btdsel_val"
                            sendCommand(cmd)
                        }
                    }
                }
            }
            .setNeutralButton(R.string.lb_booster_time_off) { _, _ ->
                if (getCarData()?.car_ac_booster_on == "yes") {
                    val cmd = "7,metrics set xsq.booster.data 1,2,2,-1,-1,-1,-1"
                    sendCommand(cmd)
                }
            }
            .show()
    }

    override fun getStateFromCarData(): Boolean {
        return getCarData()?.car_ac_booster_on == "yes"
    }

    override fun commandsAvailable(): Boolean {
        return getCarData()?.car_type in listOf("SQ")
    }
}