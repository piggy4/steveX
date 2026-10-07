package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;

public interface OutgoingChatMessage {
	Component content();

	void sendToPlayer(ServerPlayer serverPlayer, boolean bl, ChatType.Bound bound);

	static OutgoingChatMessage create(PlayerChatMessage playerChatMessage) {
		return playerChatMessage.isSystem()
			? new OutgoingChatMessage.Disguised(playerChatMessage.decoratedContent())
			: new OutgoingChatMessage.Player(playerChatMessage);
	}

	record Disguised(Component content) implements OutgoingChatMessage {
		@Override
		public void sendToPlayer(ServerPlayer serverPlayer, boolean bl, ChatType.Bound bound) {
			serverPlayer.connection.sendDisguisedChatMessage(this.content, bound);
		}
	}

	record Player(PlayerChatMessage message) implements OutgoingChatMessage {
		@Override
		public Component content() {
			return this.message.decoratedContent();
		}

		@Override
		public void sendToPlayer(ServerPlayer serverPlayer, boolean bl, ChatType.Bound bound) {
			PlayerChatMessage playerChatMessage = this.message.filter(bl);
			if (!playerChatMessage.isFullyFiltered()) {
				serverPlayer.connection.sendPlayerChatMessage(playerChatMessage, bound);
			}
		}
	}
}
