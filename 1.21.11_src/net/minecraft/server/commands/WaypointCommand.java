package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HexColorArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.WaypointArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import net.minecraft.world.waypoints.WaypointTransmitter;

public class WaypointCommand {
	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher, CommandBuildContext commandBuildContext) {
		commandDispatcher.register(
			Commands.literal("waypoint")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(commandContext -> listWaypoints(commandContext.getSource())))
				.then(
					Commands.literal("modify")
						.then(
							Commands.argument("waypoint", EntityArgument.entity())
								.then(
									Commands.literal("color")
										.then(
											Commands.argument("color", ColorArgument.color())
												.executes(
													commandContext -> setWaypointColor(
														commandContext.getSource(), WaypointArgument.getWaypoint(commandContext, "waypoint"), ColorArgument.getColor(commandContext, "color")
													)
												)
										)
										.then(
											Commands.literal("hex")
												.then(
													Commands.argument("color", HexColorArgument.hexColor())
														.executes(
															commandContext -> setWaypointColor(
																commandContext.getSource(), WaypointArgument.getWaypoint(commandContext, "waypoint"), HexColorArgument.getHexColor(commandContext, "color")
															)
														)
												)
										)
										.then(
											Commands.literal("reset")
												.executes(commandContext -> resetWaypointColor(commandContext.getSource(), WaypointArgument.getWaypoint(commandContext, "waypoint")))
										)
								)
								.then(
									Commands.literal("style")
										.then(
											Commands.literal("reset")
												.executes(
													commandContext -> setWaypointStyle(
														commandContext.getSource(), WaypointArgument.getWaypoint(commandContext, "waypoint"), WaypointStyleAssets.DEFAULT
													)
												)
										)
										.then(
											Commands.literal("set")
												.then(
													Commands.argument("style", IdentifierArgument.id())
														.executes(
															commandContext -> setWaypointStyle(
																commandContext.getSource(),
																WaypointArgument.getWaypoint(commandContext, "waypoint"),
																ResourceKey.create(WaypointStyleAssets.ROOT_ID, IdentifierArgument.getId(commandContext, "style"))
															)
														)
												)
										)
								)
						)
				)
		);
	}

	private static int setWaypointStyle(
		CommandSourceStack commandSourceStack, WaypointTransmitter waypointTransmitter, ResourceKey<WaypointStyleAsset> resourceKey
	) {
		mutateIcon(commandSourceStack, waypointTransmitter, icon -> icon.style = resourceKey);
		commandSourceStack.sendSuccess(() -> Component.translatable("commands.waypoint.modify.style"), false);
		return 0;
	}

	private static int setWaypointColor(CommandSourceStack commandSourceStack, WaypointTransmitter waypointTransmitter, ChatFormatting chatFormatting) {
		mutateIcon(commandSourceStack, waypointTransmitter, icon -> icon.color = Optional.of(chatFormatting.getColor()));
		commandSourceStack.sendSuccess(
			() -> Component.translatable("commands.waypoint.modify.color", Component.literal(chatFormatting.getName()).withStyle(chatFormatting)), false
		);
		return 0;
	}

	private static int setWaypointColor(CommandSourceStack commandSourceStack, WaypointTransmitter waypointTransmitter, Integer integer) {
		mutateIcon(commandSourceStack, waypointTransmitter, icon -> icon.color = Optional.of(integer));
		commandSourceStack.sendSuccess(
			() -> Component.translatable(
				"commands.waypoint.modify.color", Component.literal(HexFormat.of().withUpperCase().toHexDigits(ARGB.color(0, integer), 6)).withColor(integer)
			),
			false
		);
		return 0;
	}

	private static int resetWaypointColor(CommandSourceStack commandSourceStack, WaypointTransmitter waypointTransmitter) {
		mutateIcon(commandSourceStack, waypointTransmitter, icon -> icon.color = Optional.empty());
		commandSourceStack.sendSuccess(() -> Component.translatable("commands.waypoint.modify.color.reset"), false);
		return 0;
	}

	private static int listWaypoints(CommandSourceStack commandSourceStack) {
		ServerLevel serverLevel = commandSourceStack.getLevel();
		Set<WaypointTransmitter> set = serverLevel.getWaypointManager().transmitters();
		String string = serverLevel.dimension().identifier().toString();
		if (set.isEmpty()) {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.waypoint.list.empty", string), false);
			return 0;
		} else {
			Component component = ComponentUtils.formatList(
				set.stream()
					.map(
						waypointTransmitter -> {
							if (waypointTransmitter instanceof LivingEntity livingEntity) {
								BlockPos blockPos = livingEntity.blockPosition();
								return livingEntity.getFeedbackDisplayName()
									.copy()
									.withStyle(
										style -> style.withClickEvent(
												new ClickEvent.SuggestCommand("/execute in " + string + " run tp @s " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ())
											)
											.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip")))
											.withColor(waypointTransmitter.waypointIcon().color.orElse(-1))
									);
							} else {
								return Component.literal(waypointTransmitter.toString());
							}
						}
					)
					.toList(),
				Function.identity()
			);
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.waypoint.list.success", set.size(), string, component), false);
			return set.size();
		}
	}

	private static void mutateIcon(CommandSourceStack commandSourceStack, WaypointTransmitter waypointTransmitter, Consumer<Waypoint.Icon> consumer) {
		ServerLevel serverLevel = commandSourceStack.getLevel();
		serverLevel.getWaypointManager().untrackWaypoint(waypointTransmitter);
		consumer.accept(waypointTransmitter.waypointIcon());
		serverLevel.getWaypointManager().trackWaypoint(waypointTransmitter);
	}
}
