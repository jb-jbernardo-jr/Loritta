package com.mrpowergamerbr.loritta.commands.vanilla.magic

import com.mrpowergamerbr.loritta.commands.AbstractCommand
import com.mrpowergamerbr.loritta.commands.CommandCategory
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.utils.*
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale

class LoriServerListConfigCommand : AbstractCommand("lslc", category = CommandCategory.MAGIC) {
	override fun onlyOwner(): Boolean {
		return true
	}

	override fun getDescription(locale: BaseLocale): String {
		return "Configura servidores na Lori's Server List"
	}

	override fun run(context: CommandContext, locale: BaseLocale) {
		val arg0 = context.rawArgs.getOrNull(0)
		val arg1 = context.rawArgs.getOrNull(1)
		val arg2 = context.rawArgs.getOrNull(2)
		val arg3 = context.rawArgs.getOrNull(3)

		if (arg0 == "set_sponsor" && arg1 != null && arg2 != null && arg3 != null) {
			val guild = lorittaShards.getGuildById(arg1)!!
			loritta.getServerConfigForGuild(guild.id) { serverConfig ->
				val isSponsor = arg2.toBoolean()

				serverConfig.serverListConfig.isSponsored = isSponsor
				serverConfig.serverListConfig.sponsorPaid = arg3.toDouble()

				val rawArgs = context.rawArgs.toMutableList()
				rawArgs.removeAt(0)
				rawArgs.removeAt(0)
				rawArgs.removeAt(0)
				rawArgs.removeAt(0)

				serverConfig.serverListConfig.sponsoredUntil = rawArgs.joinToString(" ").convertToEpochMillis()

				loritta save serverConfig

				context.reply(
						LoriReply(
								"Servidor `${guild.name}` foi marcado como patrociado até `${serverConfig.serverListConfig.sponsoredUntil.humanize()}`"
						)
				)
			}
		}
	}
}