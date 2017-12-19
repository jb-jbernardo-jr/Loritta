package com.mrpowergamerbr.loritta.commands.vanilla.administration

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullArray
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.common.cache.CacheBuilder
import com.google.gson.JsonObject
import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.commands.AbstractCommand
import com.mrpowergamerbr.loritta.commands.CommandCategory
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.userdata.ServerConfig
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.JSON_PARSER
import com.mrpowergamerbr.loritta.utils.LoriReply
import com.mrpowergamerbr.loritta.utils.LorittaUtils
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import com.mrpowergamerbr.loritta.utils.loritta
import com.mrpowergamerbr.loritta.utils.onReactionAddByAuthor
import com.mrpowergamerbr.loritta.utils.onResponseByAuthor
import com.mrpowergamerbr.loritta.utils.save
import com.mrpowergamerbr.loritta.utils.substringIfNeeded
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.Region
import net.dv8tion.jda.core.entities.Category
import net.dv8tion.jda.core.entities.Channel
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Icon
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.VoiceChannel
import net.dv8tion.jda.core.exceptions.HierarchyException
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class RestoreCommand : AbstractCommand("restore", listOf("restaurar")) {
	companion object {
		var restoreCache = CacheBuilder.newBuilder().maximumSize(1000L).expireAfterAccess(5L, TimeUnit.MINUTES).build<String, Boolean>().asMap()
	}

	override val cooldown: Int
		get() = 60000

	override fun getDescription(locale: BaseLocale): String {
		return "Experimental"
	}

	override fun getCategory(): CommandCategory {
		return CommandCategory.ADMIN
	}

	override fun getDiscordPermissions(): List<Permission> {
		return listOf(Permission.ADMINISTRATOR)
	}

	override fun getBotPermissions(): List<Permission> {
		return listOf(Permission.ADMINISTRATOR)
	}

	override fun run(context: CommandContext, locale: BaseLocale) {
		if (restoreCache.containsKey(context.guild.id)) {
			context.reply(
					LoriReply(
							message = "Já existe um restore em andamento!",
							prefix = Constants.ERROR
					)
			)
			return
		}
		val file = context.message.attachments.getOrNull(0)

		if (file != null) {
			val attention = context.reply(
					LoriReply(
							message = "**Atenção:** As ações deste comando são *irreversíveis*, caso queria continuar, clique em ✅",
							prefix = "⛔"
					)
			)

			attention.addReaction("✅").complete()

			attention.onReactionAddByAuthor(context) {
				attention.delete().complete()

				val message = context.sendMessage("\uD83D\uDCE5 **|** Baixando arquivo...")

				val conn = URL(file.url).openConnection()
				conn.setRequestProperty("User-Agent", Constants.USER_AGENT)
				val content = conn.getInputStream().use { it.readBytes() }
				val raw = String(content, Charset.forName("UTF-8"))

				val arg = context.args.getOrNull(0)

				message.delete().complete()

				val replies = mutableListOf(
						LoriReply(
								message = "Responda com as opções que você deseja (exemplo: `wipe, lori, misc`)",
								prefix = "<:eu_te_moido:366047906689581085>"
						),
						LoriReply(message = "**Lista de opções**:"))

				for (backupOption in BackupOption.values()) {
					replies.add(LoriReply(
							"`${backupOption.key}` - ${backupOption.translationKey}"
					))
				}

				replies.add(LoriReply("`all` - Caso você deseja realizar tudo"))
				replies.add(LoriReply("`all-except-wipe` - Caso você deseja realizar tudo exceto wipe"))
				val choose = context.reply(
						*replies.toTypedArray()
				)

				choose.onResponseByAuthor(context) {
					val message = it.message.rawContent.split(", ")

					val options = mutableSetOf<BackupOption>()

					for (key in message) {
						if (key == "all") {
							options.addAll(BackupOption.values())
						}
						if (key == "all-except-wipe") {
							options.addAll(BackupOption.values().filter { it != BackupOption.WIPE })
						}
						val option = BackupOption.getByKey(key.toLowerCase())

						if (option != null) {
							options.add(option)
						}
					}

					context.message.delete().complete()

					val replies = mutableListOf(
							LoriReply(
									"**Opções escolhidas:**"
							)
					)

					for (backupOption in options) {
						replies.add(LoriReply(
								"`${backupOption.key}` - ${backupOption.translationKey}"
						))
					}

					replies.add(
							LoriReply(
									"Clique em ✅ para continuar"
							)
					)

					val continueConfirm = context.reply(*replies.toTypedArray())

					continueConfirm.addReaction("✅").complete()

					continueConfirm.onReactionAddByAuthor(context) {
						val message = context.sendMessage("Iniciando processo de restauração...")
						restoreCache[context.guild.id] = true

						if (options.contains(BackupOption.WIPE)) { // WIPE
							message.editMessage("\uD83D\uDDD1 **|** Deletando emojis...").complete()

							context.guild.emotes.forEach {
								it.delete().queue()
							}

							message.editMessage("\uD83D\uDDD1 **|** Deletando cargos...").complete()

							context.guild.roles.filter { !it.isPublicRole && !it.isManaged && context.guild.selfMember.canInteract(it) }.forEach {
								try {
									it.delete().complete()
								} catch (e: HierarchyException) {}
							}

							message.editMessage("\uD83D\uDDD1 **|** Deletando canais...").complete()

							context.guild.textChannels.filter { it != context.message.textChannel }.forEach {
								it.delete().complete()
							}

							context.guild.voiceChannels.forEach {
								it.delete().complete()
							}

							context.guild.categories.forEach {
								it.delete().complete()
							}
						}

						val jsonObject = JSON_PARSER.parse(raw).obj

						val discordObject = jsonObject["discord"].obj

						if (options.contains(BackupOption.MISC)) {
							message.editMessage("<:wumplus:388417805126467594> **|** Alterando nome do servidor...").complete()
							val name = discordObject["name"].string
							context.guild.manager.setName(name).complete()

							val iconUrl = discordObject["iconUrl"].nullString

							if (iconUrl != null) {
								message.editMessage("<:wumplus:388417805126467594> **|** Alterando ícone do servidor...").complete()

								val os = ByteArrayOutputStream()
								try {
									ImageIO.write(LorittaUtils.downloadImage(iconUrl), "png", os)
								} catch (e: Exception) {
								}

								val inputStream = ByteArrayInputStream(os.toByteArray())

								context.guild.manager.setIcon(Icon.from(inputStream)).complete()
							}

							message.editMessage("<:wumplus:388417805126467594> **|** Alterando região do servidor...").complete()
							val regionRaw = discordObject["region"].string
							context.guild.manager.setRegion(Region.fromKey(regionRaw)).complete()

							message.editMessage("<:wumplus:388417805126467594> **|** Alterando MFA Level do servidor...").complete()
							val mfaLevel = discordObject["mfaLevel"].string
							context.guild.manager.setRequiredMFALevel(Guild.MFALevel.valueOf(mfaLevel))

							message.editMessage("<:wumplus:388417805126467594> **|** Alterando nível de verificação do servidor...").complete()
							val verificationLevel = discordObject["verificationLevel"].string
							context.guild.manager.setVerificationLevel(Guild.VerificationLevel.valueOf(verificationLevel))
						}

						val channelIdRemapper = mutableMapOf<String, Channel>()
						val roleIdRemapper = mutableMapOf<String, Role>()

						if (!options.contains(BackupOption.CHANNELS)) {
							for (channel in context.guild.textChannels) {
								channelIdRemapper[channel.id] = channel
							}
							for (channel in context.guild.voiceChannels) {
								channelIdRemapper[channel.id] = channel
							}
						}

						if (!options.contains(BackupOption.ROLES)) {
							for (role in context.guild.roles) {
								roleIdRemapper[role.id] = role
							}
						}

						if (options.contains(BackupOption.EMOJIS)) {
							run {
								val emotes = discordObject["emotes"].array

								val emoteCount = emotes.size()
								var current = 1

								for (emote in emotes) {
									val name = emote["name"].string
									val imageUrl = emote["imageUrl"].string

									if ((current - 1) % 5 == 0) {
										message.editMessage("<:wumplus:388417805126467594> **|** Adicionando emote `$name`... ($current/$emoteCount)").complete()
									}

									val os = ByteArrayOutputStream()
									try {
										ImageIO.write(LorittaUtils.downloadImage(imageUrl), "png", os)
									} catch (e: Exception) {
									}

									val inputStream = ByteArrayInputStream(os.toByteArray())

									val emote = context.guild.controller.createEmote(name, Icon.from(inputStream)).queue()

									current++
								}
							}
						}

						if (options.contains(BackupOption.ROLES)) {
							run {
								val roles = discordObject["roles"].array

								var current = 1
								var roleCount = roles.size()
								for (role in roles) {
									val name = role["name"].string
									val id = role["id"].string
									val isMentionable = role["isMentionable"].bool
									val isManaged = role["isManaged"].bool
									val isPublicRole = role["isPublicRole"].bool
									val isHoisted = role["isHoisted"].bool
									val permissionsRaw = role["permissionsRaw"].long
									val color = role.obj["color"].nullObj
									val users = role.obj["users"].nullArray

									if ((current - 1) % 5 == 0) {
										message.editMessage("\uD83D\uDCBC **|** Adicionando cargo `$name`... ($current/$roleCount)").queue()
									}

									if (!isManaged && !isPublicRole) {
										val action = context.guild.controller.createRole()
												.setName(name)
												.setHoisted(isHoisted)
												.setMentionable(isMentionable)
												.setPermissions(permissionsRaw)

										if (color != null) {
											action.setColor(Loritta.GSON.fromJson<Color>(color))
										}

										val role = action.complete()

										roleIdRemapper[id] = role

										if (users != null) {
											for (id in users) {
												val member = context.guild.getMemberById(id.string)
												if (member != null) {
													context.guild.controller.addSingleRoleToMember(member, role).complete()
												}
											}
										}
									}

									if (isPublicRole) { // @everyone
										context.guild.publicRole.manager.setPermissions(permissionsRaw).complete()
									}

									current++
								}
							}
						}

						if (options.contains(BackupOption.CHANNELS)) {
							message.editMessage("<:wumplus:388417805126467594> **|** Criando canais...").complete()

							run {
								fun createTextChannelFromJson(textChannel: JsonObject, parent: Category? = null): TextChannel {
									val name = textChannel["name"].string
									val topic = textChannel["topic"].string
									val isNSFW = textChannel["isNSFW"].bool
									val action = context.guild.controller.createTextChannel(name)
											.setTopic(topic)
											.setNSFW(isNSFW)

									if (parent != null)
										action.setParent(parent)

									val channel = action.complete() as TextChannel

									val rolePermissionOverrides = textChannel.obj["rolePermissionOverrides"].nullArray

									if (rolePermissionOverrides != null) {
										for (override in rolePermissionOverrides) {
											val id = override["id"].string
											val allowed = override["allowed"].long
											val denied = override["denied"].long
											val inherit = override["inherit"].long
											val role = roleIdRemapper.getOrDefault(id, null)

											if (role != null) {
												if (channel.getPermissionOverride(role) == null) {
													channel.createPermissionOverride(role)
															.setAllow(allowed)
															.setDeny(denied)
															.complete()
												}
											}
										}
									}

									val memberPermissionOverrides = textChannel.obj["memberPermissionOverrides"].nullArray

									if (memberPermissionOverrides != null) {
										for (override in memberPermissionOverrides) {
											val id = override["id"].string
											val allowed = override["allowed"].long
											val denied = override["denied"].long
											val inherit = override["inherit"].long

											val member = context.guild.getMemberById(id)

											if (member != null) {
												if (channel.getPermissionOverride(member) == null) {
													channel.createPermissionOverride(member)
															.setAllow(allowed)
															.setDeny(denied)
															.complete()
												}
											}
										}
									}

									/* val messages = textChannel.obj["history"].nullArray

									if (messages != null) {
										for (message in messages) {
											val author = message["author"].string
											val avatarUrl = message["avatarUrl"].string
											var content = message["content"].string
											val attachments = message["attachments"].array

											for (attachment in attachments) {
												content += "\n${attachment.string}"
											}

											channel.sendMessage("**$author:** $content".substringIfNeeded()).queue()
										}
									} */

									return channel
								}

								fun createVoiceChannelFromJson(voiceChannel: JsonObject, parent: Category? = null): VoiceChannel {
									val name = voiceChannel["name"].string
									val bitrate = voiceChannel["bitrate"].int
									val userLimit = voiceChannel["userLimit"].int
									val action = context.guild.controller.createVoiceChannel(name)
											.setBitrate(bitrate)
											.setUserlimit(userLimit)

									if (parent != null)
										action.setParent(parent)

									val channel = action.complete()
									val rolePermissionOverrides = voiceChannel.obj["rolePermissionOverrides"].nullArray

									if (rolePermissionOverrides != null) {
										for (override in rolePermissionOverrides) {
											val id = override["id"].string
											val allowed = override["allowed"].long
											val denied = override["denied"].long
											val inherit = override["inherit"].long
											val role = roleIdRemapper.getOrDefault(id, null)

											if (role != null) {
												if (channel.getPermissionOverride(role) == null) {
													channel.createPermissionOverride(role)
															.setAllow(allowed)
															.setDeny(denied)
															.complete()
												}
											}
										}
									}

									val memberPermissionOverrides = voiceChannel.obj["memberPermissionOverrides"].nullArray

									if (memberPermissionOverrides != null) {
										for (override in memberPermissionOverrides) {
											val id = override["id"].string
											val allowed = override["allowed"].long
											val denied = override["denied"].long
											val inherit = override["inherit"].long

											val member = context.guild.getMemberById(id)

											if (member != null) {
												if (channel.getPermissionOverride(member) == null) {
													channel.createPermissionOverride(member)
															.setAllow(allowed)
															.setDeny(denied)
															.complete()
												}
											}
										}
									}

									return channel as VoiceChannel
								}

								val root = discordObject["root"].obj

								for (textChannel in root["textChannels"].array) {
									channelIdRemapper[textChannel["id"].string] = createTextChannelFromJson(textChannel.obj)
								}

								for (voiceChannel in root["voiceChannels"].array) {
									val name = voiceChannel["name"].string
									val bitrate = voiceChannel["bitrate"].int
									val userLimit = voiceChannel["userLimit"].int
									val channel = context.guild.controller.createVoiceChannel(name)
											.setBitrate(bitrate)
											.setUserlimit(userLimit)
											.complete()

									channelIdRemapper[voiceChannel["id"].string] = channel
								}

								for (category in root["categories"].array) {
									val name = category["name"].string

									val parent = context.guild.controller.createCategory(name)
											.complete() as Category

									val rolePermissionOverrides = category.obj["rolePermissionOverrides"].nullArray

									if (rolePermissionOverrides != null) {
										for (override in rolePermissionOverrides) {
											val id = override["id"].string
											val allowed = override["allowed"].long
											val denied = override["denied"].long
											val inherit = override["inherit"].long
											val role = roleIdRemapper.getOrDefault(id, null)

											if (role != null) {
												if (parent.getPermissionOverride(role) == null) {
													parent.createPermissionOverride(role)
															.setAllow(allowed)
															.setDeny(denied)
															.complete()
												}
											}
										}
									}

									val memberPermissionOverrides = category.obj["memberPermissionOverrides"].nullArray

									if (memberPermissionOverrides != null) {
										for (override in memberPermissionOverrides) {
											val id = override["id"].string
											val allowed = override["allowed"].long
											val denied = override["denied"].long
											val inherit = override["inherit"].long

											val member = context.guild.getMemberById(id)

											if (member != null) {
												if (parent.getPermissionOverride(member) == null) {
													parent.createPermissionOverride(member)
															.setAllow(allowed)
															.setDeny(denied)
															.complete()
												}
											}
										}
									}

									for (textChannel in category["textChannels"].array) {
										channelIdRemapper[textChannel["id"].string] = createTextChannelFromJson(textChannel.obj, parent)
									}

									for (voiceChannel in category["voiceChannels"].array) {
										channelIdRemapper[voiceChannel["id"].string] = createVoiceChannelFromJson(voiceChannel.obj, parent)
									}
								}
							}
						}

						if (options.contains(BackupOption.LORI)) {
							message.editMessage("\uD83D\uDCBC **|** Restaurando configuração...").complete()

							val serverConfig = Loritta.GSON.fromJson<ServerConfig>(jsonObject["lori"])
							serverConfig.guildId = context.guild.id

							// ===[ TODO ]===
							serverConfig.giveaways.clear() // TODO: Giveaway restore
							serverConfig.starboardEmbeds.clear() // TODO: Starboard embed restore

							// ===[ SLOW MODE ]===
							val slowModeChannels = HashMap<String, Int>()
							serverConfig.slowModeChannels.forEach {
								val channel = channelIdRemapper.getOrDefault(it.key, null)

								if (channel != null) {
									slowModeChannels.put(channel.id, it.value)
								}
							}
							serverConfig.slowModeChannels = slowModeChannels

							// ===[ MUSIC ]===
							serverConfig.musicConfig.apply {
								val channel = channelIdRemapper.getOrDefault(this.channelId, null)
								val musicChannel = channelIdRemapper.getOrDefault(this.musicGuildId, null)

								if (channel != null) {
									this.channelId = channel.id
								}
								if (musicChannel != null) {
									this.musicGuildId = musicChannel.id
								}
							}

							// ===[ AMINO ]===
							serverConfig.aminoConfig.aminos.forEach {
								val repostToChannel = channelIdRemapper.getOrDefault(it.repostToChannelId, null)

								if (repostToChannel != null)
									it.repostToChannelId = repostToChannel.id
							}

							// ===[ EVENT LOG ]===
							serverConfig.eventLogConfig.apply {
								val eventLogChannel = channelIdRemapper.getOrDefault(this.eventLogChannelId, null)

								if (eventLogChannel != null)
									this.eventLogChannelId = eventLogChannel.id
							}

							// ===[ INVITE BLOCKER CONFIG ]===
							val whitelistedChannels = mutableListOf<String>()
							serverConfig.inviteBlockerConfig.whitelistedChannels.forEach {
								val whitelistedChannel = channelIdRemapper.getOrDefault(it, null)

								if (whitelistedChannel != null)
									whitelistedChannels.add(whitelistedChannel.id)
							}
							serverConfig.inviteBlockerConfig.whitelistedChannels = whitelistedChannels

							// ===[ JOIN & LEAVE CONFIG ]===
							serverConfig.joinLeaveConfig.apply {
								val joinChannel = channelIdRemapper.getOrDefault(this.canalJoinId, null)
								val leaveChannel = channelIdRemapper.getOrDefault(this.canalJoinId, null)

								if (joinChannel != null)
									this.canalJoinId = joinChannel.id
								if (leaveChannel != null)
									this.canalLeaveId = leaveChannel.id
							}

							// ===[ YOUTUBE CONFIG ]===
							serverConfig.youTubeConfig.channels.forEach {
								val repostToChannel = channelIdRemapper.getOrDefault(it.repostToChannelId, null)

								if (repostToChannel != null)
									it.repostToChannelId = repostToChannel.id
							}

							// ===[ TWITCH CONFIG ]===
							serverConfig.youTubeConfig.channels.forEach {
								val repostToChannel = channelIdRemapper.getOrDefault(it.repostToChannelId, null)

								if (repostToChannel != null)
									it.repostToChannelId = repostToChannel.id
							}

							// ===[ RSS CONFIG ]===
							serverConfig.rssFeedConfig.feeds.forEach {
								val repostToChannel = channelIdRemapper.getOrDefault(it.repostToChannelId, null)

								if (repostToChannel != null)
									it.repostToChannelId = repostToChannel.id
							}

							// ===[ STARBOARD CONFIG ]===
							serverConfig.starboardConfig.apply {
								val starboardChannel = channelIdRemapper.getOrDefault(this.starboardId, null)

								if (starboardChannel != null)
									this.starboardId = starboardChannel.id
							}

							// ===[ AUTOROLE CONFIG ]===
							val roles = mutableListOf<String>()
							serverConfig.autoroleConfig.roles.forEach {
								val role = roleIdRemapper.getOrDefault(it, null)

								if (role != null)
									roles.add(role.id)
							}
							serverConfig.autoroleConfig.roles = roles

							// ===[ BLACKLISTED CHANNELS ]===
							val blacklistedChannels = mutableListOf<String>()
							serverConfig.blacklistedChannels.forEach {
								val blacklistedChannel = channelIdRemapper.getOrDefault(it, null)

								if (blacklistedChannel != null)
									blacklistedChannels.add(it)
							}

							// Salvar config...
							loritta save serverConfig
						}

						if (options.contains(BackupOption.BANS)) {
							message.editMessage("\uD83D\uDCBC **|** Restaurando bans...").complete()

							val bans = discordObject["bans"].array
							for ((index, id) in bans.withIndex()) {
								if (index % 5 == 0) {
									message.editMessage("\uD83D\uDCBC **|** Restaurando bans (${index + 1}/${bans.size()})...").complete()
								}
								context.guild.controller.ban(id.string, 0).complete()
							}
						}

						message.editMessage("<:loritta:331179879582269451> **|** Backup restaurado com sucesso!").complete()
						restoreCache.remove(context.guild.id)
					}
				}
			}
		} else {
			context.reply(
					LoriReply(
							message = "Nenhum arquivo de backup encontrado na sua mensagem",
							prefix = Constants.ERROR
					)
			)
		}
	}

	enum class BackupOption(val key: String, val translationKey: String) {
		MISC("misc", "Restaura o nome do servidor, ícone do servidor, nível de verificação e nível de 2FA"),
		EMOJIS("emojis", "Restaura emojis do servidor"),
		ROLES("roles", "Restaura cargos do servidor"),
		CHANNELS("channels", "Restura canais de texto, canais de voz e categorias"),
		MESSAGES("messages", "Caso \"channels\" esteja ativado, irei restaurar as mensagens"),
		LORI("lori", "Restaura minhas configurações"),
		BANS("bans", "Restaura a lista de usuários banidos"),
		WIPE("wipe", "Deleta TUDO do seu servidor antes de restaurar");

		companion object {
			fun getByKey(key: String): BackupOption? {
				return values().firstOrNull { it.key == key }
			}
		}
	}
}