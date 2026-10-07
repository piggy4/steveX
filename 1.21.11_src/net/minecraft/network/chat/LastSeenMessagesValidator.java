package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

public class LastSeenMessagesValidator {
	private final int lastSeenCount;
	private final ObjectList<LastSeenTrackedEntry> trackedMessages = new ObjectArrayList<>();
	private @Nullable MessageSignature lastPendingMessage;

	public LastSeenMessagesValidator(int i) {
		this.lastSeenCount = i;

		for (int j = 0; j < i; j++) {
			this.trackedMessages.add(null);
		}
	}

	public void addPending(MessageSignature messageSignature) {
		if (!messageSignature.equals(this.lastPendingMessage)) {
			this.trackedMessages.add(new LastSeenTrackedEntry(messageSignature, true));
			this.lastPendingMessage = messageSignature;
		}
	}

	public int trackedMessagesCount() {
		return this.trackedMessages.size();
	}

	public void applyOffset(int i) throws LastSeenMessagesValidator.ValidationException {
		int j = this.trackedMessages.size() - this.lastSeenCount;
		if (i >= 0 && i <= j) {
			this.trackedMessages.removeElements(0, i);
		} else {
			throw new LastSeenMessagesValidator.ValidationException("Advanced last seen window by " + i + " messages, but expected at most " + j);
		}
	}

	public LastSeenMessages applyUpdate(LastSeenMessages.Update update) throws LastSeenMessagesValidator.ValidationException {
		this.applyOffset(update.offset());
		ObjectList<MessageSignature> objectList = new ObjectArrayList<>(update.acknowledged().cardinality());
		if (update.acknowledged().length() > this.lastSeenCount) {
			throw new LastSeenMessagesValidator.ValidationException(
				"Last seen update contained " + update.acknowledged().length() + " messages, but maximum window size is " + this.lastSeenCount
			);
		}

		for (int i = 0; i < this.lastSeenCount; i++) {
			boolean bl = update.acknowledged().get(i);
			LastSeenTrackedEntry lastSeenTrackedEntry = this.trackedMessages.get(i);
			if (bl) {
				if (lastSeenTrackedEntry == null) {
					throw new LastSeenMessagesValidator.ValidationException("Last seen update acknowledged unknown or previously ignored message at index " + i);
				}

				this.trackedMessages.set(i, lastSeenTrackedEntry.acknowledge());
				objectList.add(lastSeenTrackedEntry.signature());
			} else {
				if (lastSeenTrackedEntry != null && !lastSeenTrackedEntry.pending()) {
					throw new LastSeenMessagesValidator.ValidationException(
						"Last seen update ignored previously acknowledged message at index " + i + " and signature " + lastSeenTrackedEntry.signature()
					);
				}

				this.trackedMessages.set(i, null);
			}
		}

		LastSeenMessages lastSeenMessages = new LastSeenMessages(objectList);
		if (!update.verifyChecksum(lastSeenMessages)) {
			throw new LastSeenMessagesValidator.ValidationException("Checksum mismatch on last seen update: the client and server must have desynced");
		} else {
			return lastSeenMessages;
		}
	}

	public static class ValidationException extends Exception {
		public ValidationException(String string) {
			super(string);
		}
	}
}
