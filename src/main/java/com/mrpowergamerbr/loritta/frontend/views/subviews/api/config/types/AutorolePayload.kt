package com.mrpowergamerbr.loritta.frontend.views.subviews.api.config.types

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.mrpowergamerbr.loritta.userdata.ServerConfig
import net.dv8tion.jda.core.entities.Guild

class AutorolePayload : ConfigPayloadType("autorole") {
	override fun process(payload: JsonObject, serverConfig: ServerConfig, guild: Guild) {
		val autoroleConfig = serverConfig.autoroleConfig
		autoroleConfig.isEnabled = payload["isEnabled"].bool
		autoroleConfig.roles = payload["roles"].array.map { it.string }.toMutableList()
	}
}