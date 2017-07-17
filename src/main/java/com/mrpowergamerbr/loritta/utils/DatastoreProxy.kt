package com.mrpowergamerbr.loritta.utils

import com.github.andrewoma.kwery.core.DefaultSession
import com.github.andrewoma.kwery.core.dialect.PostgresDialect
import com.google.gson.Gson
import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.userdata.ServerConfig

/**
 * Datastore Proxy, atualmente usado apenas para quando o Datastore do Morphia quiser salvar algo.
 *
 * Nós utilizamos este "proxy" para redirecionar para SQL quando necessário
 */
class DatastoreProxy {
	fun <T> save(var1: T) {
		if (var1 is ServerConfig) {
			// Salvar TODAS as configs no PostgreSQL, vamos ver no que vai dar :^)
			if (true || Loritta.postgreSqlTestServers.contains(var1.guildId)) {
				val session = DefaultSession(connection, PostgresDialect()) // Standard JDBC connection
				session.update("""INSERT INTO public.servers (id, data)
VALUES (:guildId, cast(:jsonConfig as json))
ON CONFLICT (id) DO UPDATE
  SET data = cast(:jsonConfig as json);""", mapOf("guildId" to var1.guildId, "jsonConfig" to Gson().toJson(var1, ServerConfig::class.java)))
				// return;
			}
		}
		loritta.ds.save(var1)
	}
}