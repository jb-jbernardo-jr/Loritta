package com.mrpowergamerbr.loritta.commands.vanilla.discord

import com.mrpowergamerbr.loritta.commands.AbstractCommand
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.LoriReply
import com.mrpowergamerbr.loritta.utils.isValidSnowflake
import com.mrpowergamerbr.loritta.utils.locale.LegacyBaseLocale
import com.mrpowergamerbr.loritta.utils.lorittaShards
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.perfectdreams.loritta.api.commands.CommandCategory

class ServerIconCommand : AbstractCommand("servericon", listOf("guildicon", "iconeserver", "iconeguild", "iconedoserver", "iconedaguild", "íconedoserver", "iconedoservidor", "íconeguild", "íconedoserver", "íconedaguild", "íconedoservidor"), category = CommandCategory.DISCORD) {
	override fun getDescription(locale: LegacyBaseLocale): String {
		return locale.get("SERVERICON_DESCRIPTION")
	}

	override fun canUseInPrivateChannel(): Boolean {
		return false
	}

	override suspend fun run(context: CommandContext,locale: LegacyBaseLocale) {
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

		if (guild.iconUrl == null) {
			context.reply(
					LoriReply(
							message = context.legacyLocale["SERVERICON_NoIcon"],
							prefix = Constants.ERROR
					)
			)
			return
		}

		val embed = EmbedBuilder()
		embed.setColor(Constants.DISCORD_BLURPLE) // Cor do embed (Cor padrão do Discord)
		val description = "**${context.legacyLocale["AVATAR_CLICKHERE", guild.iconUrl + "?size=2048"]}**"

		val guildIconUrl = guild.iconUrl!!

		embed.setDescription(description)
		embed.setImage(guild.iconUrl) // Ícone da Guild
		embed.setColor(Constants.DISCORD_BLURPLE) // Cor do embed (Cor padrão do Discord)
		embed.setTitle("<:discord:314003252830011395> ${guild.name}", null) // Nome da Guild
		embed.setImage(guildIconUrl.replace("jpg", "png") + (if (!guildIconUrl.endsWith(".gif")) "?size=2048" else ""))

		context.sendMessage(context.getAsMention(true), embed.build()) // phew, agora finalmente poderemos enviar o embed!
	}
}
