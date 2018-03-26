package com.mrpowergamerbr.loritta.utils.modules

import com.github.kevinsawicki.http.HttpRequest
import com.google.common.cache.CacheBuilder
import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.userdata.InviteBlockerConfig
import com.mrpowergamerbr.loritta.userdata.LorittaProfile
import com.mrpowergamerbr.loritta.userdata.PermissionsConfig
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.GuildLorittaUser
import com.mrpowergamerbr.loritta.utils.LorittaPermission
import com.mrpowergamerbr.loritta.utils.MiscUtils
import com.mrpowergamerbr.loritta.utils.webpaste.TemmieBitly
import kotlinx.coroutines.experimental.launch
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Message
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object InviteLinkModule {
	val logger = LoggerFactory.getLogger(InviteLinkModule::class.java)
	val cachedInviteLinks = CacheBuilder.newBuilder().expireAfterWrite(1L, TimeUnit.MINUTES).build<String, List<String>>().asMap()

	fun checkForInviteLinks(message: Message, guild: Guild, lorittaProfile: GuildLorittaUser, permissionsConfig: PermissionsConfig, inviteBlockerConfig: InviteBlockerConfig) {
		if (inviteBlockerConfig.whitelistedChannels.contains(message.channel.id))
			return

		if (lorittaProfile.hasPermission(LorittaPermission.ALLOW_INVITES))
			return

		val content = message.contentRaw
				.replace("\u200B", "")
				.replace("\\", "")

		val matcher = Pattern.compile("[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,7}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)").matcher(content)

		// Se existe algum link na mensagem...
		if (matcher.find()) {
			logger.info("matcher.find() == true! Encontrei um link na mensagem! owo")
			matcher.reset() // Já que nós já usamos find(), vamos resetar o nosso matcher para poder usar o matcher novamente

			val whitelisted = mutableListOf<String>()
			whitelisted.addAll(inviteBlockerConfig.whitelistedIds)

			val callback = {
				logger.info("Verificando invites! Atualmente eu já fiz cache de ${cachedInviteLinks.size} invites de servidores!")
				while (matcher.find()) {
					var url = matcher.group()
					if (url.contains("discord") && url.contains("gg")) {
						url = "discord.gg" + matcher.group(1).replace(".", "")
					}

					val inviteId = MiscUtils.getInviteId("http://$url") ?: MiscUtils.getInviteId("https://$url")

					if (inviteId != null) { // INVITES DO DISCORD
						if (inviteId == "attachments")
							continue

						if (whitelisted.contains(inviteId))
							continue

						val asMention = message.author.asMention
						val name = message.author.name
						val effectiveName = message.member.effectiveName

						if (inviteBlockerConfig.deleteMessage && guild.selfMember.hasPermission(message.textChannel, Permission.MESSAGE_MANAGE))
							message.delete().queue()

						if (inviteBlockerConfig.tellUser && inviteBlockerConfig.warnMessage.isNotEmpty() && message.textChannel.canTalk())
							message.textChannel.sendMessage(inviteBlockerConfig.warnMessage
									.replace("{@user}", asMention)
									.replace("{user}", name)
									.replace("{nickname}", effectiveName)).queue()
					}/*  else { // INVITES DA LORI's SERVER LIST
							try {
								val temmie = TemmieBitly("R_fb665e9e7f6a830134410d9eb7946cdf", "o_5s5av92lgs")
								var newUrl = url.removePrefix(".").removeSuffix(".")
								val bitlyUrl = temmie.expand(url)
								if (!bitlyUrl!!.contains("NOT_FOUND")) {
									newUrl = bitlyUrl!!
								}

								val httpRequest = HttpRequest.get("https://" + newUrl)
										.followRedirects(true)
										.connectTimeout(2500)
										.readTimeout(2500)
										.userAgent(Constants.USER_AGENT)

								val body = httpRequest.body()

								if (body.contains("LoriServerList.PartnerView.start()")) { // Caso a página tenha "LoriServerList.PartnerView.start()", quer dizer que é uma página de divulgação da Lori's Server List
									// mas antes... vamos pegar o ID da guild do servidor!
									val pattern = "var guildId = \"([0-9]+)\"".toPattern().matcher(body).apply { find() }
									val guildId = pattern.group(1)

									if (guild.id == guildId) // Ignorar caso o link da guild seja da guild atual
										continue

									val asMention = message.author.asMention
									val name = message.author.name
									val effectiveName = message.member.effectiveName

									if (inviteBlockerConfig.deleteMessage && guild.selfMember.hasPermission(message.textChannel, Permission.MESSAGE_MANAGE))
										message.delete().queue()

									if (inviteBlockerConfig.tellUser && inviteBlockerConfig.warnMessage.isNotEmpty() && message.textChannel.canTalk())
										message.textChannel.sendMessage(inviteBlockerConfig.warnMessage
												.replace("{@user}", asMention)
												.replace("{user}", name)
												.replace("{nickname}", effectiveName)).queue()
								}
							} catch (e: HttpRequest.HttpRequestException) {
								e.printStackTrace() // TODO: Logger
								continue
							}
						} */
				}
			}

			// Para evitar que use a API do Discord para pegar os invites do servidor toda hora, nós iremos *apenas* pegar caso seja realmente
			// necessário, e, ao pegar, vamos guardar no cache de invites
			if (inviteBlockerConfig.whitelistServerInvites && guild.selfMember.hasPermission(Permission.MANAGE_SERVER)) {
				if (!cachedInviteLinks.containsKey(guild.id)) {
					guild.invites.queue({
						cachedInviteLinks.put(guild.id, it.map { it.code })
						it.forEach {
							whitelisted.add(it.code)
						}
						launch {
							logger.info("Iniciando callback (1)")
							callback.invoke()
						}
					})
					return
				}
			}

			launch {
				logger.info("Iniciando callback (2)")
				callback.invoke()
			}
		}
	}
}