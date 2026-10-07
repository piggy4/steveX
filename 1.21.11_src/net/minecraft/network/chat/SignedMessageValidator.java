package net.minecraft.network.chat;

import com.mojang.logging.LogUtils;
import java.util.function.BooleanSupplier;
import net.minecraft.util.SignatureValidator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@FunctionalInterface
public interface SignedMessageValidator {
	Logger LOGGER = LogUtils.getLogger();
	SignedMessageValidator ACCEPT_UNSIGNED = PlayerChatMessage::removeSignature;
	SignedMessageValidator REJECT_ALL = playerChatMessage -> {
		LOGGER.error("Received chat message from {}, but they have no chat session initialized and secure chat is enforced", playerChatMessage.sender());
		return null;
	};

	@Nullable PlayerChatMessage updateAndValidate(PlayerChatMessage playerChatMessage);

	class KeyBased implements SignedMessageValidator {
		private final SignatureValidator validator;
		private final BooleanSupplier expired;
		private @Nullable PlayerChatMessage lastMessage;
		private boolean isChainValid = true;

		public KeyBased(SignatureValidator signatureValidator, BooleanSupplier booleanSupplier) {
			this.validator = signatureValidator;
			this.expired = booleanSupplier;
		}

		private boolean validateChain(PlayerChatMessage playerChatMessage) {
			if (playerChatMessage.equals(this.lastMessage)) {
				return true;
			} else if (this.lastMessage != null && !playerChatMessage.link().isDescendantOf(this.lastMessage.link())) {
				LOGGER.error(
					"Received out-of-order chat message from {}: expected index > {} for session {}, but was {} for session {}",
					playerChatMessage.sender(),
					this.lastMessage.link().index(),
					this.lastMessage.link().sessionId(),
					playerChatMessage.link().index(),
					playerChatMessage.link().sessionId()
				);
				return false;
			} else {
				return true;
			}
		}

		private boolean validate(PlayerChatMessage playerChatMessage) {
			if (this.expired.getAsBoolean()) {
				LOGGER.error("Received message with expired profile public key from {} with session {}", playerChatMessage.sender(), playerChatMessage.link().sessionId());
				return false;
			} else if (!playerChatMessage.verify(this.validator)) {
				LOGGER.error(
					"Received message with invalid signature (is the session wrong, or signature cache out of sync?): {}", PlayerChatMessage.describeSigned(playerChatMessage)
				);
				return false;
			} else {
				return this.validateChain(playerChatMessage);
			}
		}

		@Override
		public @Nullable PlayerChatMessage updateAndValidate(PlayerChatMessage playerChatMessage) {
			this.isChainValid = this.isChainValid && this.validate(playerChatMessage);
			if (!this.isChainValid) {
				return null;
			}

			this.lastMessage = playerChatMessage;
			return playerChatMessage;
		}
	}
}
