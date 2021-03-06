package com.mrpowergamerbr.loritta.commands.vanilla.discord

import com.mrpowergamerbr.loritta.commands.AbstractCommand
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.utils.*
import com.mrpowergamerbr.loritta.utils.extensions.humanize
import com.mrpowergamerbr.loritta.utils.locale.LegacyBaseLocale
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.utils.MiscUtil
import net.perfectdreams.loritta.api.commands.CommandCategory

class ServerInfoCommand : AbstractCommand("serverinfo", listOf("guildinfo"), category = CommandCategory.DISCORD) {
	override fun getDescription(locale: LegacyBaseLocale): String {
		return locale.get("SERVERINFO_DESCRIPTION")
	}

	override fun canUseInPrivateChannel(): Boolean {
		return false
	}

	override suspend fun run(context: CommandContext,locale: LegacyBaseLocale) {
		val embed = EmbedBuilder()

		var guild: Guild? = context.guild
		val id = if (context.args.isNotEmpty()) { context.args[0] } else { null }

		if (id != null && id.isValidSnowflake()) {
			guild = lorittaShards.getGuildById(context.args[0])
		}

		if (guild == null) {
			context.reply(
					LoriReply(
							message = context.legacyLocale["SERVERINFO_UnknownGuild", context.args[0]],
							prefix = Constants.ERROR
					)
			)
			return
		}

		// Baseado no comando ?serverinfo do Dyno
		embed.setThumbnail(guild.iconUrl) // Ícone da Guild
		embed.setColor(Constants.DISCORD_BLURPLE) // Cor do embed (Cor padrão do Discord)
		embed.setTitle("<:discord:314003252830011395> ${guild.name}", null) // Nome da Guild
		embed.addField("💻 ID", guild.id, true) // ID da Guild
		embed.addField("\uD83D\uDCBB Shard ID", "${MiscUtil.getShardForGuild(guild, loritta.lorittaShards.shardManager.shards.size)}", true)
		embed.addField("👑 ${context.legacyLocale["SERVERINFO_OWNER"]}", guild.owner?.asMention, true) // Dono da Guild
		embed.addField("🌎 ${context.legacyLocale["SERVERINFO_REGION"]}", guild.region.getName(), true) // Região da Guild
		embed.addField("\uD83D\uDCAC ${context.legacyLocale["SERVERINFO_CHANNELS"]} (${guild.textChannels.size + guild.voiceChannels.size})", "\uD83D\uDCDD **${locale["SERVERINFO_CHANNELS_TEXT"]}:** ${guild.textChannels.size}\n\uD83D\uDDE3 **${locale["SERVERINFO_CHANNELS_VOICE"]}:** ${guild.voiceChannels.size}", true) // Canais da Guild
		val createdAtDiff = DateUtils.formatDateDiff(guild.timeCreated.toInstant().toEpochMilli(), locale)
		embed.addField("\uD83D\uDCC5 ${context.legacyLocale["SERVERINFO_CREATED_IN"]}", "${guild.timeCreated.humanize(locale)} ($createdAtDiff)", true)
		val joinedAtDiff = DateUtils.formatDateDiff(guild.selfMember.timeJoined.toInstant().toEpochMilli(), locale)
		embed.addField("\uD83C\uDF1F ${context.legacyLocale["SERVERINFO_JOINED_IN"]}", "${guild.selfMember.timeJoined.humanize(locale)} ($joinedAtDiff)", true)
		embed.addField("👥 ${context.legacyLocale["SERVERINFO_MEMBERS"]} (${guild.members.size})", "<:online:313956277808005120> **${context.legacyLocale.get("SERVERINFO_ONLINE")}:** ${guild.members.filter{ it.onlineStatus == OnlineStatus.ONLINE }.size} |<:away:313956277220802560> **${context.legacyLocale.get("SERVERINFO_AWAY")}:** ${guild.members.filter { it.onlineStatus == OnlineStatus.IDLE }.size} |<:dnd:313956276893646850> **${context.legacyLocale.get("SERVERINFO_BUSY")}:** ${guild.members.filter { it.onlineStatus == OnlineStatus.DO_NOT_DISTURB }.size} |<:offline:313956277237710868> **${context.legacyLocale.get("SERVERINFO_OFFLINE")}:** ${guild.members.filter { it.onlineStatus == OnlineStatus.OFFLINE }.size}\n\uD83D\uDE4B **${context.legacyLocale.get("SERVERINFO_PEOPLE")}:** ${guild.members.filter{ !it.user.isBot }.size}\n\uD83E\uDD16 **${context.legacyLocale["SERVERINFO_BOTS"]}:** ${guild.members.count { it.user.isBot }}", true) // Membros da Guild
		// val roles = guild.roles.filter { !it.isPublicRole }
		// embed.addField("\uD83D\uDCBC ${context.locale["SERVERINFO_ROLES"]} (${roles.size})", roles.joinToString(", ", transform = { it.name }).substringIfNeeded(), true)

		context.sendMessage(context.getAsMention(true), embed.build()) // phew, agora finalmente poderemos enviar o embed!
	}
}
