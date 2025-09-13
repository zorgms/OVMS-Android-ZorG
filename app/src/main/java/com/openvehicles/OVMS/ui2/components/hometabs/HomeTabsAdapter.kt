package com.openvehicles.OVMS.ui2.components.hometabs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.openvehicles.OVMS.R
import java.util.Collections


class HomeTabsAdapter internal constructor(
    context: Context?,
    var mData: MutableList<HomeTab> = mutableListOf()
) : RecyclerView.Adapter<HomeTabsAdapter.ViewHolder>() {
    private val mInflater: LayoutInflater
    private var mClickListener: ItemClickListener? = null
    private var mLongClickListener: ItemLongClickListener? = null

    init {
        mInflater = LayoutInflater.from(context)
        setHasStableIds(true)
    }

    // inflates the row layout from xml when needed
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = mInflater.inflate(R.layout.home_tab_item, parent, false)
        return ViewHolder(view, mClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = mData[position]
        holder.tabName.text = action.tabName
        holder.tabIcon.setImageResource(action.tabIcon)
        holder.tabSubTitle.visibility = if (action.tabDesc == null || action.tabDesc!!.isEmpty()) View.GONE else View.VISIBLE
        holder.tabSubTitle.text = action.tabDesc
        holder.clickListener = mClickListener
        holder.longClickListener = mLongClickListener
        // hideIndicator & drag handle optional external control via tags
        holder.hideIndicator.visibility = if (action.tabDesc == "__HIDDEN__") View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int {
        return mData.size
    }

    override fun getItemId(position: Int): Long {
        return if (position in 0 until mData.size) mData[position].tabId.toLong() else RecyclerView.NO_ID
    }

    class ViewHolder internal constructor(itemView: View, var clickListener: ItemClickListener?, var longClickListener: ItemLongClickListener? = null) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener, View.OnLongClickListener {
        val tabName: TextView = itemView.findViewById(R.id.tabName)
        val tabSubTitle: TextView = itemView.findViewById(R.id.tabExtraInfo)
        val tabIcon: ImageView = itemView.findViewById(R.id.tabIcon)
    val hideIndicator: ImageView = itemView.findViewById(R.id.hideIndicator)

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(view: View) {
            clickListener?.onItemClick(view, adapterPosition)
        }

        override fun onLongClick(v: View?): Boolean {
            longClickListener?.onItemLongClick(adapterPosition)
            return true
        }
    }

    fun getItem(id: Int): HomeTab? {
        if (id >= 0 && id < mData.size)
            return mData[id]
        else
            return null
    }

    fun setClickListener(itemClickListener: ItemClickListener?) {
        mClickListener = itemClickListener
    }

    fun setLongClickListener(itemLongClickListener: ItemLongClickListener?) {
        mLongClickListener = itemLongClickListener
    }

    fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition || fromPosition !in 0 until mData.size || toPosition !in 0 until mData.size) return
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(mData, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(mData, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    interface ItemClickListener {
        fun onItemClick(view: View?, position: Int)
    }

    interface ItemLongClickListener {
        fun onItemLongClick(position: Int)
    }
}