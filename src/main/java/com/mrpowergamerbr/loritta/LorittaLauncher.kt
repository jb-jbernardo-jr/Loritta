package com.mrpowergamerbr.loritta

import com.fasterxml.jackson.module.kotlin.readValue
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.MigrationTool
import com.mrpowergamerbr.loritta.utils.config.DiscordConfig
import com.mrpowergamerbr.loritta.utils.config.GeneralConfig
import com.mrpowergamerbr.loritta.utils.config.GeneralDiscordConfig
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * Loritta's Launcher
 *
 * @author MrPowerGamerBR
 */
object LorittaLauncher {
	// STATIC MAGIC(tm)
	lateinit var loritta: Loritta

	@JvmStatic
	fun main(args: Array<String>) {
		// Isto apenas acontece se... "uma falha de segurança na API de comandos em JS for detectada"
		val doNotStart = File("do_not_start").exists()
		if (doNotStart) {
			while (true) {
				System.out.println("Falha de segurança detectada!")
				Thread.sleep(120000)
			}
		}

		// https://bugs.openjdk.java.net/browse/JDK-7016595
		// Nós precisamos ativar o PATCH manualmente
		// WebsiteUtils.allowMethods("PATCH")

		// https://www.reddit.com/r/Kotlin/comments/8qdd4x/kotlin_script_engine_and_your_classpaths_what/
		val path = this::class.java.protectionDomain.codeSource.location.path
		val jar = JarFile(path)
		val mf = jar.manifest
		val mattr = mf.mainAttributes
		// Yes, you SHOULD USE Attributes.Name.CLASS_PATH! Don't try using "Class-Path", it won't work!
		val manifestClassPath = mattr[Attributes.Name.CLASS_PATH] as String

		// The format within the Class-Path attribute is different than the one expected by the property, so let's fix it!
		// By the way, don't forget to append your original JAR at the end of the string!
		val clazz = LorittaLauncher::class.java
		val protectionDomain = clazz.protectionDomain
		val propClassPath = manifestClassPath.replace(" ", ":") + ":${Paths.get(protectionDomain.codeSource.location.toURI()).fileName}"

		// Now we set it to our own classpath
		System.setProperty("kotlin.script.classpath", propClassPath)

		val configurationFile = File(System.getProperty("conf") ?: "./loritta.conf")
		val config: GeneralConfig
		val discordConfigurationFile = File(System.getProperty("discordConf") ?: "./discord.conf")
		val discordConfig: GeneralDiscordConfig

		if (!configurationFile.exists() || !discordConfigurationFile.exists()) {
			println("Welcome to Loritta Morenitta! :3")
			println("")
			println("I want to make a world a better place... helping people, making them laugh... I hope I succeed!")
			println("")
			println("Before we start, you will need to configure me.")
			println("I will create a file named \"loritta.conf\" (general configuration) and \"discord.conf\" (platform specific configuration), open it on your favorite text editor and change it!")
			println("")
			println("After configuring the file, run me again!")

			copyFromJar("/loritta.conf", "./loritta.conf")
			copyFromJar("/discord.conf", "./discord.conf")

			System.exit(1)
			return
		}

		try {
			val json = configurationFile.readText()
			config = Constants.HOCON_MAPPER.readValue(json)
		} catch (e: IOException) {
			e.printStackTrace()
			System.exit(1) // Sair caso der erro
			return
		}

		try {
			val json = discordConfigurationFile.readText()
			discordConfig = Constants.HOCON_MAPPER.readValue(json)
		} catch (e: IOException) {
			e.printStackTrace()
			System.exit(1) // Sair caso der erro
			return
		}

		val arg0 = args.getOrNull(0)
		val arg1 = args.getOrNull(1)

		if (arg0 != null && arg0 == "migrate" && arg1 != null) {
			val tool = MigrationTool(config)

			when (arg1) {}
			return
		}

		// Iniciar instância da Loritta
		loritta = Loritta(discordConfig, config)
		loritta.start()
	}

	fun copyFromJar(inputPath: String, outputPath: String) {
		val inputStream = LorittaLauncher::class.java.getResourceAsStream(inputPath)
		File(outputPath).writeBytes(inputStream.readAllBytes())
	}
}