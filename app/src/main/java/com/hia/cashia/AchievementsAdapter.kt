package com.hia.cashia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AchievementsAdapter(
    private val achievements: List<Achievement>
) : RecyclerView.Adapter<AchievementsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.nav_achievements_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(achievements[position])
    }

    override fun getItemCount() = achievements.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconText: TextView = itemView.findViewById(R.id.achievementIcon)
        private val nameText: TextView = itemView.findViewById(R.id.achievementName)
        private val descriptionText: TextView = itemView.findViewById(R.id.achievementDescription)
        private val statusText: TextView = itemView.findViewById(R.id.achievementStatus)
        private val progressText: TextView = itemView.findViewById(R.id.achievementProgress)
        private val card: MaterialCardView = itemView.findViewById(R.id.achievementCard)

        init {
            // Ensure all views are not null
            if (iconText == null || nameText == null || descriptionText == null ||
                statusText == null || progressText == null || card == null) {
                throw IllegalStateException("Missing required views in nav_achievements_item.xml")
            }
        }

        fun bind(achievement: Achievement) {
            iconText.text = achievement.icon
            nameText.text = achievement.name
            descriptionText.text = achievement.description

            if (achievement.isUnlocked) {
                statusText.visibility = View.VISIBLE
                statusText.text = "✓"
                progressText.visibility = View.GONE
                card.setCardBackgroundColor(itemView.context.getColor(R.color.success_green))
            } else {
                statusText.visibility = View.GONE
                progressText.visibility = View.VISIBLE
                progressText.text = "${achievement.progress}/${achievement.requirement}"
                card.setCardBackgroundColor(itemView.context.getColor(R.color.primary_dark))
            }
        }
    }
}