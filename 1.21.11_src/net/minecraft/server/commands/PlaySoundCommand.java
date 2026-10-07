package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PlaySoundCommand {
	private static final SimpleCommandExceptionType ERROR_TOO_FAR = new SimpleCommandExceptionType(Component.translatable("commands.playsound.failed"));

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
		RequiredArgumentBuilder<CommandSourceStack, Identifier> requiredArgumentBuilder = Commands.argument("sound", IdentifierArgument.id())
			.suggests(SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS))
			.executes(
				commandContext -> playSound(
					commandContext.getSource(),
					getCallingPlayerAsCollection(commandContext.getSource().getPlayer()),
					IdentifierArgument.getId(commandContext, "sound"),
					SoundSource.MASTER,
					commandContext.getSource().getPosition(),
					1.0F,
					1.0F,
					0.0F
				)
			);

		for (SoundSource soundSource : SoundSource.values()) {
			requiredArgumentBuilder.then(source(soundSource));
		}

		commandDispatcher.register(Commands.literal("playsound").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(requiredArgumentBuilder));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> source(SoundSource soundSource) {
		return Commands.literal(soundSource.getName())
			.executes(
				commandContext -> playSound(
					commandContext.getSource(),
					getCallingPlayerAsCollection(commandContext.getSource().getPlayer()),
					IdentifierArgument.getId(commandContext, "sound"),
					soundSource,
					commandContext.getSource().getPosition(),
					1.0F,
					1.0F,
					0.0F
				)
			)
			.then(
				Commands.argument("targets", EntityArgument.players())
					.executes(
						commandContext -> playSound(
							commandContext.getSource(),
							EntityArgument.getPlayers(commandContext, "targets"),
							IdentifierArgument.getId(commandContext, "sound"),
							soundSource,
							commandContext.getSource().getPosition(),
							1.0F,
							1.0F,
							0.0F
						)
					)
					.then(
						Commands.argument("pos", Vec3Argument.vec3())
							.executes(
								commandContext -> playSound(
									commandContext.getSource(),
									EntityArgument.getPlayers(commandContext, "targets"),
									IdentifierArgument.getId(commandContext, "sound"),
									soundSource,
									Vec3Argument.getVec3(commandContext, "pos"),
									1.0F,
									1.0F,
									0.0F
								)
							)
							.then(
								Commands.argument("volume", FloatArgumentType.floatArg(0.0F))
									.executes(
										commandContext -> playSound(
											commandContext.getSource(),
											EntityArgument.getPlayers(commandContext, "targets"),
											IdentifierArgument.getId(commandContext, "sound"),
											soundSource,
											Vec3Argument.getVec3(commandContext, "pos"),
											commandContext.getArgument("volume", Float.class),
											1.0F,
											0.0F
										)
									)
									.then(
										Commands.argument("pitch", FloatArgumentType.floatArg(0.0F, 2.0F))
											.executes(
												commandContext -> playSound(
													commandContext.getSource(),
													EntityArgument.getPlayers(commandContext, "targets"),
													IdentifierArgument.getId(commandContext, "sound"),
													soundSource,
													Vec3Argument.getVec3(commandContext, "pos"),
													commandContext.getArgument("volume", Float.class),
													commandContext.getArgument("pitch", Float.class),
													0.0F
												)
											)
											.then(
												Commands.argument("minVolume", FloatArgumentType.floatArg(0.0F, 1.0F))
													.executes(
														commandContext -> playSound(
															commandContext.getSource(),
															EntityArgument.getPlayers(commandContext, "targets"),
															IdentifierArgument.getId(commandContext, "sound"),
															soundSource,
															Vec3Argument.getVec3(commandContext, "pos"),
															commandContext.getArgument("volume", Float.class),
															commandContext.getArgument("pitch", Float.class),
															commandContext.getArgument("minVolume", Float.class)
														)
													)
											)
									)
							)
					)
			);
	}

	private static Collection<ServerPlayer> getCallingPlayerAsCollection(@Nullable ServerPlayer serverPlayer) {
		return serverPlayer != null ? List.of(serverPlayer) : List.of();
	}

	private static int playSound(
		CommandSourceStack commandSourceStack,
		Collection<ServerPlayer> collection,
		Identifier identifier,
		SoundSource soundSource,
		Vec3 vec3,
		float f,
		float g,
		float h
	) throws CommandSyntaxException {
		Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(identifier));
		double d = Mth.square(holder.value().getRange(f));
		ServerLevel serverLevel = commandSourceStack.getLevel();
		long l = serverLevel.getRandom().nextLong();
		List<ServerPlayer> list = new ArrayList<>();

		for (ServerPlayer serverPlayer : collection) {
			if (serverPlayer.level() == serverLevel) {
				double e = vec3.x - serverPlayer.getX();
				double i = vec3.y - serverPlayer.getY();
				double j = vec3.z - serverPlayer.getZ();
				double k = e * e + i * i + j * j;
				Vec3 vec32 = vec3;
				float m = f;
				if (k > d) {
					if (h <= 0.0F) {
						continue;
					}

					double n = Math.sqrt(k);
					vec32 = new Vec3(serverPlayer.getX() + e / n * 2.0, serverPlayer.getY() + i / n * 2.0, serverPlayer.getZ() + j / n * 2.0);
					m = h;
				}

				serverPlayer.connection.send(new ClientboundSoundPacket(holder, soundSource, vec32.x(), vec32.y(), vec32.z(), m, g, l));
				list.add(serverPlayer);
			}
		}

		int o = list.size();
		if (o == 0) {
			throw ERROR_TOO_FAR.create();
		}

		if (o == 1) {
			commandSourceStack.sendSuccess(
				() -> Component.translatable("commands.playsound.success.single", Component.translationArg(identifier), list.getFirst().getDisplayName()), true
			);
		} else {
			commandSourceStack.sendSuccess(() -> Component.translatable("commands.playsound.success.multiple", Component.translationArg(identifier), o), true);
		}

		return o;
	}
}
