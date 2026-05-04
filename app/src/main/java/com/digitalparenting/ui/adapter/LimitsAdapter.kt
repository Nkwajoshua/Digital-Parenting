package com.digitalparenting.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.data.local.AppLimit

class LimitsAdapter(
    private var limits: List<AppLimit>,
    private val onSave: (AppLimit) -> Unit
) : RecyclerView.Adapter<LimitsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val etMinutes: EditText = view.findViewById(R.id.etMinutes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_limit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val limit = limits[position]
        holder.tvAppName.text = limit.appName
        holder.etMinutes.setText(limit.maxMinutes.toString())

        holder.etMinutes.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newLimit = limit.copy(maxMinutes = holder.etMinutes.text.toString().toIntOrNull() ?: 30)
                onSave(newLimit)
            }
        }
    }

    override fun getItemCount() = limits.size
}