package com.mrpowergamerbr.loritta.frontend.views.subviews

import com.mongodb.client.model.Filters
import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.frontend.evaluate
import com.mrpowergamerbr.loritta.utils.loritta
import com.mrpowergamerbr.loritta.utils.lorittaShards
import org.jooby.Request
import org.jooby.Response
import java.io.File
import kotlin.collections.MutableMap
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.getOrNull
import kotlin.collections.set

class LoriPartnerView : AbstractView() {
	override fun handleRender(req: Request, res: Response, path: String, variables: MutableMap<String, Any?>): Boolean {
		val arg0 = path.split("/").getOrNull(2) ?: return false

		val server = loritta.serversColl.find(
				Filters.or(
						Filters.and(
								Filters.eq("serverListConfig.enabled", true),
								Filters.eq("serverListConfig.vanityUrl", arg0)
						),
						Filters.and(
								Filters.eq("serverListConfig.enabled", true),
								Filters.eq("_id", arg0)
						)
				)
		).firstOrNull() ?: return false

		return path.startsWith("/s/")
	}

	override fun render(req: Request, res: Response, path: String, variables: MutableMap<String, Any?>): String {
		val arg0 = path.split("/").getOrNull(2) ?: return ":whatdog:"
		variables["guildId"] = arg0
		val server = loritta.serversColl.find(
				Filters.or(
						Filters.and(
								Filters.eq("serverListConfig.enabled", true),
								Filters.eq("serverListConfig.vanityUrl", arg0)
						),
						Filters.and(
								Filters.eq("serverListConfig.enabled", true),
								Filters.eq("_id", arg0)
						)
				)
		).firstOrNull() ?: return "Something went wrong, sorry."

		val guild = lorittaShards.getGuildById(server.guildId) ?: return "Something went wrong, sorry."

		variables["serverListConfig"] = server.serverListConfig
		variables["guild"] = guild
		var tagline = server.serverListConfig.tagline ?: ""
		guild.emotes.forEach {
			tagline = tagline.replace(":${it.name}:", "")
		}
		variables["tagline"] = tagline
		variables["iconUrl"] = guild.iconUrl?.replace("jpg", "png?size=512")
		variables["hasCustomBackground"] = File(Loritta.FRONTEND, "static/assets/img/servers/backgrounds/${server.guildId}.png").exists()
		return evaluate("partner_view.html", variables)
	}
}