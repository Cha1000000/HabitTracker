package com.habittracker.data.model

import com.habittracker.domain.model.Nudge

/**
 * Firebase модель для Nudge (пинг между участниками)
 */
data class FirebaseNudge(
    var id: String = "",
    var habitId: String = "",
    var senderId: String = "",
    var receiverId: String = "",
    var sentAt: Long = 0L
) {
    fun toDomain(): Nudge = Nudge(
        id = id,
        habitId = habitId,
        senderId = senderId,
        receiverId = receiverId,
        sentAt = sentAt
    )
    
    companion object {
        fun fromDomain(nudge: Nudge): FirebaseNudge = FirebaseNudge(
            id = nudge.id,
            habitId = nudge.habitId,
            senderId = nudge.senderId,
            receiverId = nudge.receiverId,
            sentAt = nudge.sentAt
        )
    }
}
