package com.hia.cashia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AvatarAdapter(
    private val avatars: List<String> = AVAILABLE_AVATARS,
    private val onAvatarClick: (String) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_avatar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(avatars[position], onAvatarClick)
    }

    override fun getItemCount() = avatars.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarText: TextView = itemView.findViewById(R.id.avatarItemText)

        fun bind(avatar: String, onClick: (String) -> Unit) {
            avatarText.text = avatar
            itemView.setOnClickListener {
                onClick(avatar)
            }
        }
    }
}