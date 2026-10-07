package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

public class DeOpCommands {
	private static final SimpleCommandExceptionType ERROR_NOT_OP = new SimpleCommandExceptionType(Component.translatable("commands.deop.failed"));

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
		commandDispatcher.register(
			Commands.literal("deop")
				.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.then(
					Commands.argument("targets", GameProfileArgument.gameProfile())
						.suggests(
							(commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
								commandContext.getSource().getServer().getPlayerList().getOpNames(), suggestionsBuilder
							)
						)
						.executes(commandContext -> deopPlayers(commandContext.getSource(), GameProfileArgument.getGameProfiles(commandContext, "targets")))
				)
		);
	}

	private static int deopPlayers(CommandSourceStack commandSourceStack, Collection<NameAndId> collection) throws CommandSyntaxException {
		PlayerList playerList = commandSourceStack.getServer().getPlayerList();
		int i = 0;

		for (NameAndId nameAndId : collection) {
			if (playerList.isOp(nameAndId)) {
				playerList.deop(nameAndId);
				i++;
				commandSourceStack.sendSuccess(() -> Component.translatable("commands.deop.success", collection.iterator().next().name()), true);
			}
		}

		if (i == 0) {
			throw ERROR_NOT_OP.create();
		}

		commandSourceStack.getServer().kickUnlistedPlayers();
		return i;
	}
}
