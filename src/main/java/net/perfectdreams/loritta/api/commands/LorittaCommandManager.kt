package net.perfectdreams.loritta.api.commands

import com.mrpowergamerbr.loritta.utils.LoriReply
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import com.mrpowergamerbr.loritta.utils.locale.LegacyBaseLocale
import mu.KotlinLogging
import net.perfectdreams.commands.dsl.BaseDSLCommand
import net.perfectdreams.commands.manager.CommandContinuationType
import net.perfectdreams.commands.manager.CommandManager
import net.perfectdreams.loritta.api.platform.LorittaBot
import java.awt.Image
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

abstract class LorittaCommandManager(val loritta: LorittaBot) : CommandManager<LorittaCommandContext, LorittaCommand, BaseDSLCommand>() {
	companion object {
		internal val logger = KotlinLogging.logger {}
	}

	val commands = mutableListOf<LorittaCommand>()

	init {
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

		contextManager.registerContext<LegacyBaseLocale>(
				{ clazz: KClass<*> -> clazz.isSubclassOf(LegacyBaseLocale::class) || clazz == LegacyBaseLocale::class },
				{ sender, clazz, stack ->
					sender.legacyLocale
				}
		)

		contextManager.registerContext<Image>(
				{ clazz: KClass<*> -> clazz.isSubclassOf(Image::class) || clazz == Image::class },
				{ sender, clazz, stack ->
					val pop = stack.pop()

					sender.getImage(pop)
				}
		)
	}

	final override fun getRegisteredCommands() = commands

	final override fun registerCommand(command: LorittaCommand) {
		commands.add(command)
	}

	final override fun unregisterCommand(command: LorittaCommand) {
		commands.remove(command)
	}
}