package com.digitalparenting.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.data.local.AppUsageStats

class UsageAdapter : RecyclerView.Adapter<UsageAdapter.ViewHolder>() {

    private var data = listOf<AppUsageStats>()

    fun submitList(newData: List<AppUsageStats>) {
        data = newData
        notifyDataSetChanged()
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.view.findViewById<TextView>(android.R.id.text1).text = item.packageName
        holder.view.findViewById<TextView>(android.R.id.text2).text = 
            "Time: ${String.format("%.1f", item.totalTime / 1000.0)}s"
    }
}
