package net.minecraft.client.multiplayer.chat;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public interface LoggedChatMessage extends LoggedChatEvent {
	static LoggedChatMessage.Player player(GameProfile gameProfile, PlayerChatMessage playerChatMessage, ChatTrustLevel chatTrustLevel) {
		return new LoggedChatMessage.Player(gameProfile, playerChatMessage, chatTrustLevel);
	}

	static LoggedChatMessage.System system(Component component, Instant instant) {
		return new LoggedChatMessage.System(component, instant);
	}

	Component toContentComponent();

	default Component toNarrationComponent() {
		return this.toContentComponent();
	}

	boolean canReport(UUID uUID);

	@Environment(EnvType.CLIENT)
	record Player(GameProfile profile, PlayerChatMessage message, ChatTrustLevel trustLevel) implements LoggedChatMessage {
		public static final MapCodec<LoggedChatMessage.Player> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ExtraCodecs.AUTHLIB_GAME_PROFILE.fieldOf("profile").forGetter(LoggedChatMessage.Player::profile),
					PlayerChatMessage.MAP_CODEC.forGetter(LoggedChatMessage.Player::message),
					ChatTrustLevel.CODEC.optionalFieldOf("trust_level", ChatTrustLevel.SECURE).forGetter(LoggedChatMessage.Player::trustLevel)
				)
				.apply(instance, LoggedChatMessage.Player::new)
		);
		private static final DateTimeFormatter TIME_FORMATTER = Util.localizedDateFormatter(FormatStyle.SHORT);

		@Override
		public Component toContentComponent() {
			if (!this.message.filterMask().isEmpty()) {
				Component component = this.message.filterMask().applyWithFormatting(this.message.signedContent());
				return component != null ? component : Component.empty();
			} else {
				return this.message.decoratedContent();
			}
		}

		@Override
		public Component toNarrationComponent() {
			Component component = this.toContentComponent();
			Component component2 = this.getTimeComponent();
			return Component.translatable("gui.chatSelection.message.narrate", this.profile.name(), component, component2);
		}

		public Component toHeadingComponent() {
			Component component = this.getTimeComponent();
			return Component.translatable("gui.chatSelection.heading", this.profile.name(), component);
		}

		private Component getTimeComponent() {
			ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(this.message.timeStamp(), ZoneId.systemDefault());
			return Component.literal(zonedDateTime.format(TIME_FORMATTER)).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
		}

		@Override
		public boolean canReport(UUID uUID) {
			return this.message.hasSignatureFrom(uUID);
		}

		public UUID profileId() {
			return this.profile.id();
		}

		@Override
		public LoggedChatEvent.Type type() {
			return LoggedChatEvent.Type.PLAYER;
		}
	}

	@Environment(EnvType.CLIENT)
	record System(Component message, Instant timeStamp) implements LoggedChatMessage {
		public static final MapCodec<LoggedChatMessage.System> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ComponentSerialization.CODEC.fieldOf("message").forGetter(LoggedChatMessage.System::message),
					ExtraCodecs.INSTANT_ISO8601.fieldOf("time_stamp").forGetter(LoggedChatMessage.System::timeStamp)
				)
				.apply(instance, LoggedChatMessage.System::new)
		);

		@Override
		public Component toContentComponent() {
			return this.message;
		}

		@Override
		public boolean canReport(UUID uUID) {
			return false;
		}

		@Override
		public LoggedChatEvent.Type type() {
			return LoggedChatEvent.Type.SYSTEM;
		}
	}
}
