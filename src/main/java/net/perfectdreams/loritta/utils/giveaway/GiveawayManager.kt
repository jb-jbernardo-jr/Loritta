package net.perfectdreams.loritta.utils.giveaway

import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.network.Databases
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.Emotes
import com.mrpowergamerbr.loritta.utils.extensions.await
import com.mrpowergamerbr.loritta.utils.extensions.getRandom
import com.mrpowergamerbr.loritta.utils.extensions.sendMessageAsync
import com.mrpowergamerbr.loritta.utils.lorittaShards
import kotlinx.coroutines.*
import mu.KotlinLogging
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.*
import net.perfectdreams.loritta.dao.Giveaway
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object GiveawayManager {
    var giveawayTasks = mutableMapOf<Long, Job>()
    private val logger = KotlinLogging.logger {}

    fun getReactionMention(reaction: String): String {
        val emoteId = reaction.toLongOrNull()

        if (emoteId != null) {
            val mention = lorittaShards.getEmoteById(emoteId.toString())?.asMention
            if (mention != null)
                return mention
        }

        return reaction
    }

    fun createEmbed(reason: String, description: String, reaction: String, epoch: Long): MessageEmbed {
        val diff = (epoch - System.currentTimeMillis()) / 1000
        val diffSeconds = diff / 1000 % 60
        val diffMinutes = diff / (60 * 1000) % 60
        val diffHours = diff / (60 * 60 * 1000) % 24
        val diffDays = diff / (24 * 60 * 60 * 1000)

        val message = if (diffDays >= 1) {
            "$diffDays dias"
        } else if (diffHours >= 1) {
            "$diffHours horas"
        } else if (diffMinutes >= 1) {
            "$diffDays minutos"
        } else if (diffSeconds >= 1) {
            "$diffMinutes segundos"
        } else {
            "¯\\_(ツ)_/¯"
        }

        val embed = EmbedBuilder().apply {
            setTitle("\uD83C\uDF81 $reason")
            setDescription("$description\n\nUse ${getReactionMention(reaction)} para entrar!\n\n$diff")
            addField("⏰⏰ Tempo restante", message, true)
            setColor(Constants.DISCORD_BLURPLE)
            setFooter("Acabará em", null)
            setTimestamp(Instant.ofEpochMilli(epoch))
        }

        return embed.build()
    }

    suspend fun spawnGiveaway(channel: TextChannel, reason: String, description: String, reaction: String, epoch: Long, numberOfWinners: Int): Giveaway {
        val embed = createEmbed(reason, description, reaction, epoch)

        val message = channel.sendMessage(embed).await()
        val messageId = message.idLong

        val emoteId = reaction.toLongOrNull()

        if (emoteId != null) {
            val mention = lorittaShards.getEmoteById(emoteId.toString())
            message.addReaction(mention).await()
        } else {
            message.addReaction(reaction).await()
        }

        val giveaway = transaction(Databases.loritta) {
            Giveaway.new {
                this.guildId = channel.guild.idLong
                this.textChannelId = channel.idLong
                this.messageId = messageId

                this.numberOfWinners = numberOfWinners
                this.reason = reason
                this.description = description
                this.finishAt = epoch
                this.reaction = reaction
            }
        }

        createGiveawayJob(giveaway)

        return giveaway
    }

    fun createGiveawayJob(giveaway: Giveaway) {
        giveawayTasks[giveaway.id.value] = GlobalScope.launch {
            try {
                while (giveaway.finishAt > System.currentTimeMillis()) {
                    if (!this.isActive) // Oh no, o giveaway acabou então a task não é mais necessária! Ignore...
                        return@launch

                    val guild = lorittaShards.getGuildById(giveaway.guildId) ?: run {
                        cancelGiveaway(giveaway)
                        return@launch
                    }
                    val channel = guild.getTextChannelById(giveaway.textChannelId) ?: run {
                        cancelGiveaway(giveaway)
                        return@launch
                    }
                    val message = channel.getMessageById(giveaway.messageId).await() ?: run {
                        cancelGiveaway(giveaway)
                        return@launch
                    }

                    val embed = GiveawayManager.createEmbed(
                            giveaway.reason,
                            giveaway.description,
                            giveaway.reaction,
                            giveaway.finishAt
                    )

                    if (embed.fields.firstOrNull { it.name == " Tempo restante" }?.value != message.embeds.firstOrNull()?.fields?.firstOrNull { it.name == " Tempo restante" }?.value) {
                        message.editMessage(embed)
                    }

                    delay(1000)
                }

                val guild = lorittaShards.getGuildById(giveaway.guildId)
                val channel = guild!!.getTextChannelById(giveaway.textChannelId)
                val message = channel.getMessageById(giveaway.messageId).await()

                GiveawayManager.finishGiveaway(message, giveaway)
            } catch (e: Exception) {
                logger.error(e) { "Error when processing giveaway ${giveaway.id.value}" }
            }
        }
    }

    suspend fun cancelGiveaway(giveaway: Giveaway) {
        if (lorittaShards.shardManager.shards.any { it.status != JDA.Status.CONNECTED })
            return

        giveawayTasks[giveaway.id.value]?.cancel()
        giveawayTasks.remove(giveaway.id.value)

        transaction(Databases.loritta) {
            giveaway.delete()
        }
    }

    suspend fun finishGiveaway(message: Message, giveaway: Giveaway) {
        val emoteId = giveaway.reaction.toLongOrNull()

        val messageReaction: MessageReaction?

        if (emoteId != null) {
            messageReaction = message.reactions.firstOrNull { it.reactionEmote.emote?.idLong == emoteId }
        } else {
            messageReaction = message.reactions.firstOrNull { it.reactionEmote.name == giveaway.reaction }
        }

        if (messageReaction != null) {
            val users = messageReaction.users.await()

            if (users.size == 1 && users[0].id == Loritta.config.clientId) { // Ninguém participou do giveaway! (Só a Lori, mas ela não conta)
                return
            } else {
                val winners = mutableListOf<User>()
                val reactedUsers = messageReaction.users.await().filter { it.id != Loritta.config.clientId }.toMutableList()

                repeat(giveaway.numberOfWinners) {
                    if (reactedUsers.isEmpty())
                        return@repeat

                    val user = reactedUsers.getRandom()
                    winners.add(user)
                    reactedUsers.remove(user)
                }

                if (winners.size == 1) { // Apenas um ganhador
                    val winner = winners.first()
                    message.channel.sendMessageAsync("\uD83C\uDF89 **|** Parabéns ${winner.asMention} por ganhar o giveaway `${giveaway.reason}`! ${Emotes.LORI_HAPPY}")
                } else { // Mais de um ganhador
                    val replies = mutableListOf("\uD83C\uDF89 **|** Parabéns aos ganhadores do giveaway `${giveaway.reason}`! ${Emotes.LORI_HAPPY}")

                    repeat(giveaway.numberOfWinners) {
                        val user = reactedUsers[it]

                        if (user != null) {
                            replies.add("⭐ **|** ${user.asMention}")
                        } else {
                            replies.add("⭐ **|** ¯\\_(ツ)_/¯")
                        }
                    }

                    message.channel.sendMessageAsync(replies.joinToString("\n"))
                }
            }
        } else {
            message.channel.sendMessageAsync("Nenhuma reação válida na mensagem...")
        }

        val embed = EmbedBuilder().apply {
            setTitle("\uD83C\uDF81 ${giveaway.reason}")
            setDescription(giveaway.description)
            setFooter("Encerrado!", null)
        }

        transaction(Databases.loritta) {
            giveaway.delete()
        }

        giveawayTasks[giveaway.id.value]?.cancel()
        giveawayTasks.remove(giveaway.id.value)
    }
}p