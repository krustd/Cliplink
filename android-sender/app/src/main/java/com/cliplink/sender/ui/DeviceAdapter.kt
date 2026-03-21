package com.cliplink.sender.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cliplink.sender.R
import com.cliplink.sender.data.DeviceInfo

class DeviceAdapter(
    private val onClick: (DeviceInfo) -> Unit
) : ListAdapter<DeviceInfo, DeviceAdapter.ViewHolder>(Diff) {
    var selectedEndpoint: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    object Diff : DiffUtil.ItemCallback<DeviceInfo>() {
        override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem.stableKey == newItem.stableKey
        }

        override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).endpointKey == selectedEndpoint)
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (DeviceInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val stateText: TextView = itemView.findViewById(R.id.stateText)
        private val detailText: TextView = itemView.findViewById(R.id.detailText)
        private var current: DeviceInfo? = null

        init {
            itemView.setOnClickListener { current?.let(onClick) }
        }

        fun bind(device: DeviceInfo, isSelected: Boolean) {
            current = device
            itemView.isActivated = isSelected
            nameText.text = device.name
            val os = device.os?.takeIf { it.isNotBlank() } ?: "unknown-os"
            detailText.text = "${device.host}:${device.port} · $os"
            stateText.text = if (isSelected) "当前设备" else "点击使用"
        }
    }
}
