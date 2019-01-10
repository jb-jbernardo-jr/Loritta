package net.perfectdreams.loritta.tables

import org.jetbrains.exposed.dao.LongIdTable

object Giveaways : LongIdTable() {
	val guildId = long("guild").index()
	val textChannelId = long("channel")
	val messageId = long("message")

	val reason = text("reason")
	val description = text("description")
	val numberOfWinners = integer("number_of_winners")
	val reaction = text("reaction")
	val finishAt = long("finish_at")
	val customMessage = text("custom_message").nullable()
	val locale = text("locale")
}