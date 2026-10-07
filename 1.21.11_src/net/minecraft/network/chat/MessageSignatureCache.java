package net.minecraft.network.chat;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class MessageSignatureCache {
	public static final int NOT_FOUND = -1;
	private static final int DEFAULT_CAPACITY = 128;
	private final @Nullable MessageSignature[] entries;

	public MessageSignatureCache(int i) {
		this.entries = new MessageSignature[i];
	}

	public static MessageSignatureCache createDefault() {
		return new MessageSignatureCache(128);
	}

	public int pack(MessageSignature messageSignature) {
		for (int i = 0; i < this.entries.length; i++) {
			if (messageSignature.equals(this.entries[i])) {
				return i;
			}
		}

		return -1;
	}

	public @Nullable MessageSignature unpack(int i) {
		return this.entries[i];
	}

	public void push(SignedMessageBody signedMessageBody, @Nullable MessageSignature messageSignature) {
		List<MessageSignature> list = signedMessageBody.lastSeen().entries();
		ArrayDeque<MessageSignature> arrayDeque = new ArrayDeque<>(list.size() + 1);
		arrayDeque.addAll(list);
		if (messageSignature != null) {
			arrayDeque.add(messageSignature);
		}

		this.push(arrayDeque);
	}

	@VisibleForTesting
	void push(List<MessageSignature> list) {
		this.push(new ArrayDeque<>(list));
	}

	private void push(ArrayDeque<MessageSignature> arrayDeque) {
		Set<MessageSignature> set = new ObjectOpenHashSet<>(arrayDeque);

		for (int i = 0; !arrayDeque.isEmpty() && i < this.entries.length; i++) {
			MessageSignature messageSignature = this.entries[i];
			this.entries[i] = arrayDeque.removeLast();
			if (messageSignature != null && !set.contains(messageSignature)) {
				arrayDeque.addFirst(messageSignature);
			}
		}
	}
}
