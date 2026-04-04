package com.digitalparenting.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.data.local.ProtectionIncidentEntity
import java.text.SimpleDateFormat
import java.util.*

class ProtectionIncidentAdapter :
    ListAdapter<ProtectionIncidentEntity, ProtectionIncidentAdapter.IncidentViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncidentViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.incident_item, parent, false)
        return IncidentViewHolder(itemView as ViewGroup)
    }

    override fun onBindViewHolder(holder: IncidentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class IncidentViewHolder(private val itemView: ViewGroup) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.incidentTitle)
        private val messageText: TextView = itemView.findViewById(R.id.incidentMessage)
        private val timeText: TextView = itemView.findViewById(R.id.incidentTime)

        fun bind(incident: ProtectionIncidentEntity) {
            titleText.text = incident.title
            messageText.text = incident.message
            timeText.text = formatTime(incident.timestamp)
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ProtectionIncidentEntity>() {
        override fun areItemsTheSame(
            oldItem: ProtectionIncidentEntity,
            newItem: ProtectionIncidentEntity
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ProtectionIncidentEntity,
            newItem: ProtectionIncidentEntity
        ): Boolean = oldItem == newItem
    }
}
