package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

public class VersionCommand {
	private static final Component HEADER = Component.translatable("commands.version.header");
	private static final Component STABLE = Component.translatable("commands.version.stable.yes");
	private static final Component UNSTABLE = Component.translatable("commands.version.stable.no");

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher, boolean bl) {
		commandDispatcher.register(
			Commands.literal("version").requires(Commands.hasPermission(bl ? Commands.LEVEL_GAMEMASTERS : Commands.LEVEL_ALL)).executes(commandContext -> {
				CommandSourceStack commandSourceStack = commandContext.getSource();
				commandSourceStack.sendSystemMessage(HEADER);
				dumpVersion(commandSourceStack::sendSystemMessage);
				return 1;
			})
		);
	}

	public static void dumpVersion(Consumer<Component> consumer) {
		WorldVersion worldVersion = SharedConstants.getCurrentVersion();
		consumer.accept(Component.translatable("commands.version.id", worldVersion.id()));
		consumer.accept(Component.translatable("commands.version.name", worldVersion.name()));
		consumer.accept(Component.translatable("commands.version.data", worldVersion.dataVersion().version()));
		consumer.accept(Component.translatable("commands.version.series", worldVersion.dataVersion().series()));
		consumer.accept(
			Component.translatable("commands.version.protocol", worldVersion.protocolVersion(), "0x" + Integer.toHexString(worldVersion.protocolVersion()))
		);
		consumer.accept(Component.translatable("commands.version.build_time", Component.translationArg(worldVersion.buildTime())));
		consumer.accept(Component.translatable("commands.version.pack.resource", worldVersion.packVersion(PackType.CLIENT_RESOURCES).toString()));
		consumer.accept(Component.translatable("commands.version.pack.data", worldVersion.packVersion(PackType.SERVER_DATA).toString()));
		consumer.accept(worldVersion.stable() ? STABLE : UNSTABLE);
	}
}
