package net.minecraft.client.multiplayer.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChatLog {
	private final LoggedChatEvent[] buffer;
	private int nextId;

	public static Codec<ChatLog> codec(int i) {
		return Codec.list(LoggedChatEvent.CODEC)
			.comapFlatMap(
				list -> {
					int j = list.size();
					return j > i
						? DataResult.error(() -> "Expected: a buffer of size less than or equal to " + i + " but: " + j + " is greater than " + i)
						: DataResult.success(new ChatLog(i, (List<LoggedChatEvent>)list));
				},
				ChatLog::loggedChatEvents
			);
	}

	public ChatLog(int i) {
		this.buffer = new LoggedChatEvent[i];
	}

	private ChatLog(int i, List<LoggedChatEvent> list) {
		this.buffer = list.toArray(LoggedChatEvent[]::new);
		this.nextId = list.size();
	}

	private List<LoggedChatEvent> loggedChatEvents() {
		List<LoggedChatEvent> list = new ArrayList<>(this.size());

		for (int i = this.start(); i <= this.end(); i++) {
			list.add(this.lookup(i));
		}

		return list;
	}

	public void push(LoggedChatEvent loggedChatEvent) {
		this.buffer[this.index(this.nextId++)] = loggedChatEvent;
	}

	public @Nullable LoggedChatEvent lookup(int i) {
		return i >= this.start() && i <= this.end() ? this.buffer[this.index(i)] : null;
	}

	private int index(int i) {
		return i % this.buffer.length;
	}

	public int start() {
		return Math.max(this.nextId - this.buffer.length, 0);
	}

	public int end() {
		return this.nextId - 1;
	}

	private int size() {
		return this.end() - this.start() + 1;
	}
}
