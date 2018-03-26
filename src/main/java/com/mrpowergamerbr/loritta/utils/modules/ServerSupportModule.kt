package com.mrpowergamerbr.loritta.utils.modules

import com.mrpowergamerbr.loritta.utils.response.responses.*
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

object ServerSupportModule {
	fun checkForSupport(event: MessageReceivedEvent, message: Message) {
		if (message.textChannel.id != "398987569485971466")
			return

		val content = message.contentRaw
				.replace("\u200B", "")
				.replace("\\", "")

		val responses = listOf(
				LoriOfflineResponse(),
				LanguageResponse(),
				MentionChannelResponse(),
				MusicResponse(),
				StarboardResponse(),
				LimparPlaylistResponse(),
				AddEmotesResponse(),
				SendFanArtsResponse(),
				LoriMandarComandosResponse()
		)

		responses.forEach {
			if (it.handleResponse(event, content))
				event.channel.sendMessage(it.getResponse(event, content)).queue()
			return
		}
		return
	}
}