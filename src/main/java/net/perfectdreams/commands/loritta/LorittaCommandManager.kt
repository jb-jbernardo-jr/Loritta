package net.perfectdreams.commands.loritta

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.LorittaLauncher
import com.mrpowergamerbr.loritta.commands.AbstractCommand
import com.mrpowergamerbr.loritta.commands.vanilla.misc.MagicPingCommand
import com.mrpowergamerbr.loritta.events.LorittaMessageEvent
import com.mrpowergamerbr.loritta.userdata.ServerConfig
import com.mrpowergamerbr.loritta.utils.*
import com.mrpowergamerbr.loritta.utils.extensions.await
import com.mrpowergamerbr.loritta.utils.extensions.localized
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import mu.KotlinLogging
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.exceptions.ErrorResponseException
import net.perfectdreams.commands.dsl.BaseDSLCommand
import net.perfectdreams.commands.manager.CommandContinuationType
import net.perfectdreams.commands.manager.CommandManager
import java.awt.Image
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class LorittaCommandManager(val loritta: Loritta) : CommandManager<LorittaCommandContext, LorittaCommand, BaseDSLCommand>() {
	companion object {
		private val logger = KotlinLogging.logger {}
	}

	val commands = mutableListOf<LorittaCommand>()

	init {
		registerCommand(MagicPingCommand())

		commandListeners.addThrowableListener { context, command, throwable ->
			if (throwable is CommandException) {
				context.reply(
						LoriReply(
								throwable.reason,
								throwable.prefix
						)
				)
				return@addThrowableListener CommandContinuationType.CANCEL
			}
			return@addThrowableListener CommandContinuationType.CONTINUE
		}
		contextManager.registerContext<BaseLocale>(
				{ clazz: KClass<*> -> clazz.isSubclassOf(BaseLocale::class) || clazz == BaseLocale::class },
				{ sender, clazz, stack ->
					sender.locale
				}
		)

		contextManager.registerContext<Image>(
				{ clazz: KClass<*> -> clazz.isSubclassOf(Image::class) || clazz == Image::class },
				{ sender, clazz, stack ->
					val pop = stack.pop()

					sender.getImage(pop)
				}
		)

		contextManager.registerContext<User>(
				{ clazz: KClass<*> -> clazz.isSubclassOf(User::class) || clazz == User::class },
				{ sender, clazz, stack ->
					val link = stack.pop() // Ok, será que isto é uma URL?

					println("user context: $link")

					// Vamos verificar por menções, uma menção do Discord é + ou - assim: <@123170274651668480>
					for (user in sender.message.mentionedUsers) {
						if (user.asMention == link.replace("!", "")) { // O replace é necessário já que usuários com nick tem ! no mention (?)
							// Diferente de null? Então vamos usar o avatar do usuário!
							return@registerContext user
						}
					}

					// Vamos tentar procurar pelo username + discriminator
					if (!sender.isPrivateChannel && !link.isEmpty()) {
						val split = link.split("#").dropLastWhile { it.isEmpty() }.toTypedArray()

						if (split.size == 2 && split[0].isNotEmpty()) {
							val matchedMember = sender.guild.getMembersByName(split[0], false).stream().filter { it -> it.user.discriminator == split[1] }.findFirst()

							if (matchedMember.isPresent) {
								return@registerContext matchedMember.get().user
							}
						}
					}

					// Ok então... se não é link e nem menção... Que tal então verificar por nome?
					if (!sender.isPrivateChannel && !link.isEmpty()) {
						val matchedMembers = sender.guild.getMembersByEffectiveName(link, true)

						if (!matchedMembers.isEmpty()) {
							return@registerContext matchedMembers[0].user
						}
					}

					// Se não, vamos procurar só pelo username mesmo
					if (!sender.isPrivateChannel && !link.isEmpty()) {
						val matchedMembers = sender.guild.getMembersByName(link, true)

						if (!matchedMembers.isEmpty()) {
							return@registerContext matchedMembers[0].user
						}
					}

					// Ok, então só pode ser um ID do Discord!
					try {
						val user = LorittaLauncher.loritta.lorittaShards.retrieveUserById(link)

						if (user != null) { // Pelo visto é!
							return@registerContext user
						}
					} catch (e: Exception) {
					}

					return@registerContext null
				}
		)
	}
	override fun getRegisteredCommands() = commands

	override fun registerCommand(command: LorittaCommand) {
		commands.add(command)
	}

	override fun unregisterCommand(command: LorittaCommand) {
		commands.remove(command)
	}

	suspend fun dispatch(ev: LorittaMessageEvent, conf: ServerConfig, locale: BaseLocale, lorittaUser: LorittaUser): Boolean {
		val rawMessage = ev.message.contentRaw

		// É necessário remover o new line para comandos como "+eval", etc
		val rawArguments = rawMessage.replace("\n", "").split(" ")

		// Primeiro os comandos vanilla da Loritta(tm)
		for (command in getRegisteredCommands()) {
			if (verifyAndDispatch(command, rawArguments, ev, conf, locale, lorittaUser))
				return true
		}

		return false
	}

	suspend fun verifyAndDispatch(command: LorittaCommand, rawArguments: List<String>, ev: LorittaMessageEvent, conf: ServerConfig, locale: BaseLocale, lorittaUser: LorittaUser): Boolean {
		for (subCommand in command.subcommands) {
			if (dispatch(subCommand as LorittaCommand, rawArguments.drop(1).toMutableList(), ev, conf, locale, lorittaUser, true))
				return true
		}

		if (dispatch(command, rawArguments, ev, conf, locale, lorittaUser, false))
			return true

		return false
	}

	suspend fun dispatch(command: LorittaCommand, rawArguments: List<String>, ev: LorittaMessageEvent, conf: ServerConfig, locale: BaseLocale, lorittaUser: LorittaUser, isSubcommand: Boolean): Boolean {
		val message = ev.message.contentDisplay
		val member = ev.message.member

		// Carregar as opções de comandos
		// val cmdOptions = conf.getCommandOptionsFor(command)
		val prefix = conf.commandPrefix

		val labels = command.labels.toMutableList()

		// println("Labels de $command: $labels")
		// if (cmdOptions.enableCustomAliases) // Adicionar labels customizadas no painel
		// 	labels.addAll(cmdOptions.aliases)

		// ignoreCase = true ~ Permite usar "+cOmAnDo"
		var valid = labels.any { rawArguments[0].equals(prefix + it, true) }
		var byMention = false

		if (!isSubcommand && rawArguments.getOrNull(1) != null && (rawArguments[0] == "<@${Loritta.config.clientId}>" || rawArguments[0] == "<@!${Loritta.config.clientId}>")) {
			// by mention
			valid = labels.any { rawArguments[1].equals(it, true) }
			byMention = true
		}

		// println("Vàlido? $valid $rawArguments[0]")

		if (valid) {
			val isPrivateChannel = ev.isFromType(ChannelType.PRIVATE)
			val start = System.currentTimeMillis()

			var args = message.replace("@${ev.guild?.selfMember?.effectiveName ?: ""}", "").stripCodeMarks().split(" ").toTypedArray().remove(0)
			var rawArgs = ev.message.contentRaw.stripCodeMarks().split(" ").toTypedArray().remove(0)
			var strippedArgs = ev.message.contentStripped.stripCodeMarks().split(" ").toTypedArray().remove(0)
			if (byMention) {
				args = args.remove(0)
				rawArgs = rawArgs.remove(0)
				strippedArgs = strippedArgs.remove(0)
			}

			var locale = locale

			if (!isPrivateChannel) { // TODO: Migrar isto para que seja customizável
				when (ev.channel.id) {
					"414839559721975818" -> locale = loritta.getLocaleById("default") // português (default)
					"404713176995987466" -> locale = loritta.getLocaleById("en-us") // inglês
					"414847180285935622" -> locale = loritta.getLocaleById("es-es") // espanhol
					"414847291669872661" -> locale = loritta.getLocaleById("pt-pt") // português de portugal
					"414847379670564874" -> locale = loritta.getLocaleById("pt-funk") // português funk
				}
			}

			val context = LorittaCommandContext(conf, lorittaUser, locale, ev, command, args, rawArgs, strippedArgs)

			if (ev.message.isFromType(ChannelType.TEXT)) {
				logger.info("(${ev.message.guild.name} -> ${ev.message.channel.name}) ${ev.author.name}#${ev.author.discriminator} (${ev.author.id}): ${ev.message.contentDisplay}")
			} else {
				logger.info("(Direct Message) ${ev.author.name}#${ev.author.discriminator} (${ev.author.id}): ${ev.message.contentDisplay}")
			}

			try {
				conf.lastCommandReceivedAt = System.currentTimeMillis()
				com.mrpowergamerbr.loritta.utils.loritta.serversColl.updateOne(
						Filters.eq("_id", conf.guildId),
						Updates.set("lastCommandReceivedAt", conf.lastCommandReceivedAt)
				)

				if (conf != loritta.dummyServerConfig && ev.textChannel != null && !ev.textChannel.canTalk()) { // Se a Loritta não pode falar no canal de texto, avise para o dono do servidor para dar a permissão para ela
					LorittaUtils.warnOwnerNoPermission(ev.guild, ev.textChannel, conf)
					return true
				}

				if (conf.blacklistedChannels.contains(ev.channel.id) && !lorittaUser.hasPermission(LorittaPermission.BYPASS_COMMAND_BLACKLIST)) {
					// if (!conf.miscellaneousConfig.enableBomDiaECia || (conf.miscellaneousConfig.enableBomDiaECia && command !is LigarCommand)) {
						if (conf.warnIfBlacklisted) {
							if (conf.blacklistWarning.isNotEmpty() && ev.guild != null && ev.member != null && ev.textChannel != null) {
								val generatedMessage = MessageUtils.generateMessage(
										conf.blacklistWarning,
										listOf(ev.member, ev.textChannel),
										ev.guild
								)
								ev.textChannel.sendMessage(generatedMessage).queue()
							}
						}
						return true // Ignorar canais bloqueados (return true = fast break, se está bloqueado o canal no primeiro comando que for executado, os outros obviamente também estarão)
					// }
				}

				// if (cmdOptions.override && cmdOptions.blacklistedChannels.contains(ev.channel.id))
				// 	return true // Ignorar canais bloqueados

				// Cooldown
				val diff = System.currentTimeMillis() - com.mrpowergamerbr.loritta.utils.loritta.userCooldown.getOrDefault(ev.author.idLong, 0L)

				if (1250 > diff && ev.author.id != Loritta.config.ownerId) { // Tá bom, é alguém tentando floodar, vamos simplesmente ignorar
					com.mrpowergamerbr.loritta.utils.loritta.userCooldown.put(ev.author.idLong, System.currentTimeMillis()) // E vamos guardar o tempo atual
					return true
				}

				val profile = lorittaUser.profile
				var cooldown = command.cooldown
				if (profile.isActiveDonator() && profile.donatorPaid >= 19.99) {
					cooldown /= 2
				}

				if (cooldown > diff && ev.author.id != Loritta.config.ownerId) {
					val fancy = DateUtils.formatDateDiff((cooldown - diff) + System.currentTimeMillis(), locale)
					context.reply(
							LoriReply(
									locale.format(fancy, "\uD83D\uDE45") { commands.pleaseWaitCooldown },
									"\uD83D\uDD25"
							)
					)
					return true
				}

				loritta.userCooldown[ev.author.idLong] = System.currentTimeMillis()

				LorittaUtilsKotlin.executedCommands++
				command.executedCount++

				if (command.hasCommandFeedback && !conf.commandOutputInPrivate) {
					ev.channel.sendTyping().await()
				}

				// Se estamos dentro de uma guild... (Já que mensagens privadas não possuem permissões)
				if (!isPrivateChannel && ev.guild != null && ev.member != null && ev.textChannel != null) {
					// Verificar se a Loritta possui todas as permissões necessárias
					val botPermissions = command.botPermissions.toMutableList()
					botPermissions.add(Permission.MESSAGE_EMBED_LINKS)
					botPermissions.add(Permission.MESSAGE_EXT_EMOJI)
					botPermissions.add(Permission.MESSAGE_ADD_REACTION)
					botPermissions.add(Permission.MESSAGE_HISTORY)
					val missingPermissions = ArrayList<Permission>(botPermissions.filterNot { ev.guild.selfMember.hasPermission(ev.textChannel, it) })

					if (missingPermissions.isNotEmpty()) {
						// oh no
						val required = missingPermissions.joinToString(", ", transform = { "`" + it.localized(locale) + "`" })
						context.reply(
								LoriReply(
										locale.format(required, "\uD83D\uDE22", "\uD83D\uDE42") { commands.loriDoesntHavePermissionDiscord },
										Constants.ERROR
								)
						)
						return true
					}
				}

				if (!isPrivateChannel && ev.member != null && ev.textChannel != null) {
					val missingPermissions = command.lorittaPermissions.filterNot { lorittaUser.hasPermission(it) }

					if (missingPermissions.isNotEmpty()) {
						// oh no
						val required = missingPermissions.joinToString(", ", transform = { "`" + locale["LORIPERMISSION_${it.name}"] + "`"})
						var message = locale["LORIPERMISSION_MissingPermissions", required]

						if (ev.member.hasPermission(Permission.ADMINISTRATOR) || ev.member.hasPermission(Permission.MANAGE_SERVER)) {
							message += " ${locale["LORIPERMISSION_MissingPermCanConfigure", Loritta.config.websiteUrl]}"
						}
						ev.textChannel.sendMessage(Constants.ERROR + " **|** ${ev.member.asMention} $message").queue()
						return true
					}
				}

				/* if (args.isNotEmpty() && args[0] == "🤷") { // Usar a ajuda caso 🤷 seja usado
					command.explain(context)
					return true
				} */

				if (LorittaUtilsKotlin.handleIfBanned(context, lorittaUser.profile)) {
					return true
				}

				if (context.cmd.onlyOwner && context.userHandle.id != Loritta.config.ownerId) {
					context.reply(
							LoriReply(
									locale.format { commands.commandOnlyForOwner },
									Constants.ERROR
							)
					)
					return true
				}

				if (!context.canUseCommand()) {
					val requiredPermissions = command.discordPermissions.filter { !ev.message.member.hasPermission(ev.message.textChannel, it) }
					val required = requiredPermissions.joinToString(", ", transform = { "`" + it.localized(locale) + "`" })
					context.reply(
							LoriReply(
									locale.format(required) { commands.doesntHavePermissionDiscord },
									Constants.ERROR
							)
					)
					return true
				}

				if (context.isPrivateChannel && !command.canUseInPrivateChannel) {
					context.sendMessage(Constants.ERROR + " **|** " + context.getAsMention(true) + locale["CANT_USE_IN_PRIVATE"])
					return true
				}

				/* if (command.needsToUploadFiles()) {
					if (!LorittaUtils.canUploadFiles(context)) {
						return true
					}
				} */

				if (command.requiresMusic) {
					if (!context.config.musicConfig.isEnabled || context.config.musicConfig.channelId == null) {
						val canManage = context.handle.hasPermission(Permission.MANAGE_SERVER) || context.handle.hasPermission(Permission.ADMINISTRATOR)
						context.sendMessage(Constants.ERROR + " **|** " + context.getAsMention(true) + locale["DJ_LORITTA_DISABLED"] + " \uD83D\uDE1E" + if (canManage) locale["DJ_LORITTA_HOW_TO_ENABLE", "${Loritta.config.websiteUrl}dashboard"] else "")
						return true
					}
				}

				val randomValue = Loritta.RANDOM.nextInt(0, 100)

				if (randomValue == 0) {
					context.reply(
							LoriReply(
									locale["LORITTA_PleaseUpvote", "<https://discordbots.org/bot/loritta/vote>"],
									"\uD83D\uDE0A"
							)
					)
				} else if ((randomValue == 1 || randomValue == 2 || randomValue == 3) && !profile.isActiveDonator()) {
					context.reply(
							LoriReply(
									locale["LORITTA_PleaseDonate", "<${Loritta.config.websiteUrl}donate>"],
									"<:lori_owo:432530033316462593>"
							)
					)
				}

				if (!context.isPrivateChannel) {
					val nickname = context.guild.selfMember.nickname

					if (nickname != null) {
						// #LoritaTambémTemSentimentos
						val hasBadNickname = MiscUtils.hasInappropriateWords(nickname)

						if (hasBadNickname) {
							context.reply(
									LoriReply(
											locale["LORITTA_BadNickname"],
											"<:lori_triste:370344565967814659>"
									)
							)
							if (context.guild.selfMember.hasPermission(Permission.NICKNAME_CHANGE)) {
								context.guild.controller.setNickname(context.guild.selfMember, null).queue()
							} else {
								return true
							}
						}
					}
				}

				val result = execute(context, command, rawArgs)

				if (!isPrivateChannel && ev.guild != null) {
					if (ev.guild.selfMember.hasPermission(ev.textChannel, Permission.MESSAGE_MANAGE) && (conf.deleteMessageAfterCommand)) {
						ev.message.textChannel.getMessageById(ev.messageId).queue {
							// Nós iremos pegar a mensagem novamente, já que talvez ela tenha sido deletada
							it.delete().queue()
						}
					}
				}

				loritta.userCooldown[ev.author.idLong] = System.currentTimeMillis()

				val end = System.currentTimeMillis()
				if (ev.message.isFromType(ChannelType.TEXT)) {
					logger.info("(${ev.message.guild.name} -> ${ev.message.channel.name}) ${ev.author.name}#${ev.author.discriminator} (${ev.author.id}): ${ev.message.contentDisplay} - OK! Processado em ${end - start}ms")
				} else {
					logger.info("(Direct Message) ${ev.author.name}#${ev.author.discriminator} (${ev.author.id}): ${ev.message.contentDisplay} - OK! Processado em ${end - start}ms")
				}
				return result
			} catch (e: Exception) {
				if (e is ErrorResponseException) {
					if (e.errorCode == 40005) { // Request entity too large
						if (ev.isFromType(ChannelType.PRIVATE) || (ev.isFromType(ChannelType.TEXT) && ev.textChannel != null && ev.textChannel.canTalk()))
							context.reply(
									LoriReply(
											context.locale.format("8MB", Emotes.LORI_TEMMIE) { commands.imageTooLarge },
											"\uD83E\uDD37"
									)
							)
						return true
					}
				}

				AbstractCommand.logger.error("Exception ao executar comando ${command.javaClass.simpleName}", e)
				LorittaUtilsKotlin.sendStackTrace(ev.message, e)

				// Avisar ao usuário que algo deu muito errado
				val mention = if (conf.mentionOnCommandOutput) "${ev.author.asMention} " else ""
				val reply = "\uD83E\uDD37 **|** " + mention + locale["ERROR_WHILE_EXECUTING_COMMAND"]

				if (!e.message.isNullOrEmpty())
					reply + " ${e.message!!.escapeMentions()}"

				if (ev.isFromType(ChannelType.PRIVATE) || (ev.isFromType(ChannelType.TEXT) && ev.textChannel != null && ev.textChannel.canTalk()))
					ev.channel.sendMessage(reply).queue()

				return true
			}
		}
		return false
	}
}