package net.minecraft.network.protocol.game;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class ServerboundSeenAdvancementsPacket implements Packet<ServerGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ServerboundSeenAdvancementsPacket> STREAM_CODEC = Packet.codec(
		ServerboundSeenAdvancementsPacket::write, ServerboundSeenAdvancementsPacket::new
	);
	private final ServerboundSeenAdvancementsPacket.Action action;
	private final @Nullable Identifier tab;

	public ServerboundSeenAdvancementsPacket(ServerboundSeenAdvancementsPacket.Action action, @Nullable Identifier identifier) {
		this.action = action;
		this.tab = identifier;
	}

	public static ServerboundSeenAdvancementsPacket openedTab(AdvancementHolder advancementHolder) {
		return new ServerboundSeenAdvancementsPacket(ServerboundSeenAdvancementsPacket.Action.OPENED_TAB, advancementHolder.id());
	}

	public static ServerboundSeenAdvancementsPacket closedScreen() {
		return new ServerboundSeenAdvancementsPacket(ServerboundSeenAdvancementsPacket.Action.CLOSED_SCREEN, null);
	}

	private ServerboundSeenAdvancementsPacket(FriendlyByteBuf friendlyByteBuf) {
		this.action = friendlyByteBuf.readEnum(ServerboundSeenAdvancementsPacket.Action.class);
		if (this.action == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
			this.tab = friendlyByteBuf.readIdentifier();
		} else {
			this.tab = null;
		}
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeEnum(this.action);
		if (this.action == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
			friendlyByteBuf.writeIdentifier(this.tab);
		}
	}

	@Override
	public PacketType<ServerboundSeenAdvancementsPacket> type() {
		return GamePacketTypes.SERVERBOUND_SEEN_ADVANCEMENTS;
	}

	public void handle(ServerGamePacketListener serverGamePacketListener) {
		serverGamePacketListener.handleSeenAdvancements(this);
	}

	public ServerboundSeenAdvancementsPacket.Action getAction() {
		return this.action;
	}

	public @Nullable Identifier getTab() {
		return this.tab;
	}

	public enum Action {
		OPENED_TAB,
		CLOSED_SCREEN;
	}
}
