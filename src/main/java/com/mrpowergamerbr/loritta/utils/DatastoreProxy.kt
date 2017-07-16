package com.mrpowergamerbr.loritta.utils

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
			if (Loritta.postgreSqlTestServers.contains(var1.guildId)) {
				return;
			}
		}
		loritta.ds.save(var1)
	}
}