package com.hia.cashia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AchievementsDialogAdapter(
    private val achievements: List<Achievement>
) : RecyclerView.Adapter<AchievementsDialogAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
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
        private val progressBar: View = itemView.findViewById(R.id.achievementProgressBar)
        private val progressText: TextView = itemView.findViewById(R.id.achievementProgressText)
        private val statusText: TextView = itemView.findViewById(R.id.achievementStatus)
        private val card: MaterialCardView = itemView.findViewById(R.id.achievementCard)

        fun bind(achievement: Achievement) {
            iconText.text = achievement.icon
            nameText.text = achievement.name
            descriptionText.text = achievement.description

            if (achievement.isUnlocked) {
                statusText.visibility = View.VISIBLE
                statusText.text = "✓ UNLOCKED"
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                card.setCardBackgroundColor(itemView.context.getColor(R.color.success_green))
            } else {
                statusText.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                progressText.visibility = View.VISIBLE
                progressText.text = "${achievement.progress}/${achievement.requirement}"

                // Set progress bar width based on percentage
                val percentage = (achievement.progress.toFloat() / achievement.requirement.toFloat()) * 100
                val params = progressBar.layoutParams
                params.width = ((progressBar.parent as View).width * percentage / 100).toInt()
                progressBar.layoutParams = params

                card.setCardBackgroundColor(itemView.context.getColor(R.color.primary_dark))
            }
        }
    }
}