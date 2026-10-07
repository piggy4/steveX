package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

public class GameModeCommand {
	public static final PermissionCheck PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
		commandDispatcher.register(
			Commands.literal("gamemode")
				.requires(Commands.hasPermission(PERMISSION_CHECK))
				.then(
					Commands.argument("gamemode", GameModeArgument.gameMode())
						.executes(
							commandContext -> setMode(
								commandContext, Collections.singleton(commandContext.getSource().getPlayerOrException()), GameModeArgument.getGameMode(commandContext, "gamemode")
							)
						)
						.then(
							Commands.argument("target", EntityArgument.players())
								.executes(
									commandContext -> setMode(
										commandContext, EntityArgument.getPlayers(commandContext, "target"), GameModeArgument.getGameMode(commandContext, "gamemode")
									)
								)
						)
				)
		);
	}

	private static void logGamemodeChange(CommandSourceStack commandSourceStack, ServerPlayer serverPlayer, GameType gameType) {
		Component component = Component.translatable("gameMode." + gameType.getName());
		if (commandSourceStack.getEntity() == serverPlayer) {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.gamemode.success.self", component), true);
		} else {
			if (commandSourceStack.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK)) {
				serverPlayer.sendSystemMessage(Component.translatable("gameMode.changed", component));
			}

			commandSourceStack.sendSuccess(() -> Component.translatable("commands.gamemode.success.other", serverPlayer.getDisplayName(), component), true);
		}
	}

	private static int setMode(CommandContext<CommandSourceStack> commandContext, Collection<ServerPlayer> collection, GameType gameType) {
		int i = 0;

		for (ServerPlayer serverPlayer : collection) {
			if (setGameMode(commandContext.getSource(), serverPlayer, gameType)) {
				i++;
			}
		}

		return i;
	}

	public static void setGameMode(ServerPlayer serverPlayer, GameType gameType) {
		setGameMode(serverPlayer.createCommandSourceStack(), serverPlayer, gameType);
	}

	private static boolean setGameMode(CommandSourceStack commandSourceStack, ServerPlayer serverPlayer, GameType gameType) {
		if (serverPlayer.setGameMode(gameType)) {
			logGamemodeChange(commandSourceStack, serverPlayer, gameType);
			return true;
		} else {
			return false;
		}
	}
}
