package com.mrpowergamerbr.loritta.commands.vanilla.administration

import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.set
import com.google.common.cache.CacheBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mrpowergamerbr.loritta.Loritta.Companion.GSON
import com.mrpowergamerbr.loritta.commands.AbstractCommand
import com.mrpowergamerbr.loritta.commands.CommandCategory
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.LoriReply
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import com.mrpowergamerbr.loritta.utils.onReactionAddByAuthor
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.VoiceChannel
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class BackupCommand : AbstractCommand("backup") {
	companion object {
		var backupCache = CacheBuilder.newBuilder().maximumSize(1000L).expireAfterAccess(5L, TimeUnit.MINUTES).build<String, Boolean>().asMap()
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
		if (backupCache.containsKey(context.guild.id)) {
			context.reply(
					LoriReply(
							message = "Já existe um backup em andamento!",
							prefix = Constants.ERROR
					)
			)
			return
		}
		val attention = context.reply(
				LoriReply(
						message = "**Atenção:** O backup do servidor poderá conter informações confidenciais, é recomendado que você use o comando em um canal de texto aonde membros não podem ler ele. Caso queira continuar, clique em ✅",
						prefix = "⛔"
				)
		)

		attention.addReaction("✅").complete()

		attention.onReactionAddByAuthor(context) {
			attention.delete().complete()

			backupCache[context.guild.id] = true
			val message = context.sendMessage("Criando backup...")
			val jsonObject = JsonObject()

			jsonObject["epoch"] = System.currentTimeMillis()
			jsonObject["lori"] = GSON.toJsonTree(context.config)
			jsonObject["lori"].obj.remove("guildId") // Remover guild ID, desnecessário e é melhor não salvar

			val discordObject = JsonObject()
			val guild = context.guild

			discordObject["iconUrl"] = guild.iconUrl
			discordObject["name"] = guild.name
			discordObject["region"] = guild.regionRaw
			discordObject["mfaLevel"] = guild.requiredMFALevel.name
			discordObject["verificationLevel"] = guild.verificationLevel.name
			discordObject["bans"] = GSON.toJsonTree(guild.bans.complete().map { it.id })

			discordObject["roles"] = GSON.toJsonTree(guild.roles.map {
				val role = JsonObject()
				role["id"] = it.id
				role["name"] = it.name
				role["isHoisted"] = it.isHoisted
				role["isManaged"] = it.isManaged
				role["isMentionable"] = it.isMentionable
				role["isPublicRole"] = it.isPublicRole
				role["permissionsRaw"] = it.permissionsRaw
				role["positionRaw"] = it.positionRaw

				if (it.color != null) {
					role["color"] = GSON.toJsonTree(it.color)
				}

				role["users"] = GSON.toJsonTree(guild.getMembersWithRoles(it).map { it.user.id })

				role
			})

			discordObject["emotes"] = GSON.toJsonTree(guild.emotes.map {
				val emote = JsonObject()
				emote["id"] = it.id
				emote["name"] = it.name
				emote["imageUrl"] = it.imageUrl

				emote
			})

			fun createJsonFromTextChannel(textChannel: TextChannel): JsonObject {
				val jsonTextChannel = JsonObject()
				jsonTextChannel["id"] = textChannel.id
				jsonTextChannel["name"] = textChannel.name
				jsonTextChannel["isNSFW"] = textChannel.isNSFW
				jsonTextChannel["topic"] = textChannel.topic
				jsonTextChannel["positionRaw"] = textChannel.positionRaw
				jsonTextChannel["rolePermissionOverrides"] = GSON.toJsonTree(textChannel.rolePermissionOverrides.map {
					val obj = JsonObject()
					obj["id"] = it.role.id
					obj["allowed"] = it.allowedRaw
					obj["denied"] = it.deniedRaw
					obj["inherit"] = it.inheritRaw
					obj
				})
				jsonTextChannel["memberPermissionOverrides"] = GSON.toJsonTree(textChannel.memberPermissionOverrides.map {
					val obj = JsonObject()
					obj["id"] = it.member.user.id
					obj["allowed"] = it.allowedRaw
					obj["denied"] = it.deniedRaw
					obj["inherit"] = it.inheritRaw
					obj
				})

				/* message.editMessage("Salvando histórico de `${textChannel.name}`...").complete()
				val history = textChannel.history.retrievePast(100).complete().reversed()
				val messages = JsonArray()

				for (message in history) {
					val jsonMessage = JsonObject()
					jsonMessage["id"] = message.id
					jsonMessage["author"] = message.author.name
					jsonMessage["avatarUrl"] = message.author.effectiveAvatarUrl
					jsonMessage["content"] = message.rawContent
					jsonMessage["attachments"] = GSON.toJsonTree(message.attachments.map { it.url })
					messages.add(jsonMessage)
				}

				jsonTextChannel["history"] = messages */

				return jsonTextChannel
			}

			fun createJsonFromVoiceChannel(voiceChannel: VoiceChannel): JsonObject {
				val jsonVoiceChannel = JsonObject()
				jsonVoiceChannel["id"] = voiceChannel.id
				jsonVoiceChannel["name"] = voiceChannel.name
				jsonVoiceChannel["bitrate"] = voiceChannel.bitrate
				jsonVoiceChannel["userLimit"] = voiceChannel.userLimit
				jsonVoiceChannel["positionRaw"] = voiceChannel.positionRaw
				jsonVoiceChannel["rolePermissionOverrides"] = GSON.toJsonTree(voiceChannel.rolePermissionOverrides.map {
					val obj = JsonObject()
					obj["id"] = it.role.id
					obj["allowed"] = it.allowedRaw
					obj["denied"] = it.deniedRaw
					obj["inherit"] = it.inheritRaw
					obj
				})
				jsonVoiceChannel["memberPermissionOverrides"] = GSON.toJsonTree(voiceChannel.memberPermissionOverrides.map {
					val obj = JsonObject()
					obj["id"] = it.member.user.id
					obj["allowed"] = it.allowedRaw
					obj["denied"] = it.deniedRaw
					obj["inherit"] = it.inheritRaw
					obj
				})
				return jsonVoiceChannel
			}

			val root = JsonObject()
			val textChannels = JsonArray()
			for (textChannel in guild.textChannels.filter { it.parent == null }) {
				textChannels.add(createJsonFromTextChannel(textChannel))
			}
			val voiceChannels = JsonArray()
			for (voiceChannel in guild.voiceChannels.filter { it.parent == null }) {
				voiceChannels.add(createJsonFromVoiceChannel(voiceChannel))
			}

			root["textChannels"] = textChannels
			root["voiceChannels"] = voiceChannels

			val categories = JsonArray()

			for (category in guild.categories) {
				val jsonCategory = JsonObject()
				jsonCategory["name"] = category.name
				jsonCategory["positionRaw"] = category.positionRaw
				val textChannels = category.textChannels.map { createJsonFromTextChannel(it) }
				val voiceChannels = category.voiceChannels.map { createJsonFromVoiceChannel(it) }

				jsonCategory["textChannels"] = GSON.toJsonTree(textChannels)
				jsonCategory["voiceChannels"] = GSON.toJsonTree(voiceChannels)

				jsonCategory["rolePermissionOverrides"] = GSON.toJsonTree(category.rolePermissionOverrides.map {
					val obj = JsonObject()
					obj["id"] = it.role.id
					obj["allowed"] = it.allowedRaw
					obj["denied"] = it.deniedRaw
					obj["inherit"] = it.inheritRaw
					obj
				})
				jsonCategory["memberPermissionOverrides"] = GSON.toJsonTree(category.memberPermissionOverrides.map {
					val obj = JsonObject()
					obj["id"] = it.member.user.id
					obj["allowed"] = it.allowedRaw
					obj["denied"] = it.deniedRaw
					obj["inherit"] = it.inheritRaw
					obj
				})

				categories.add(jsonCategory)
			}

			root["categories"] = categories

			discordObject["root"] = root
			jsonObject["discord"] = discordObject

			val json = jsonObject.toString()
			context.sendFile(json.byteInputStream(Charset.forName("UTF-8")), "backup-${System.currentTimeMillis()}.json", context.getAsMention(true))
			backupCache.remove(context.guild.id)
		}
	}
}