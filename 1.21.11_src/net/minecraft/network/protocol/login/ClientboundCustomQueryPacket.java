package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryPayload;
import net.minecraft.resources.Identifier;

public record ClientboundCustomQueryPacket(int transactionId, CustomQueryPayload payload) implements Packet<ClientLoginPacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ClientboundCustomQueryPacket> STREAM_CODEC = Packet.codec(
		ClientboundCustomQueryPacket::write, ClientboundCustomQueryPacket::new
	);
	private static final int MAX_PAYLOAD_SIZE = 1048576;

	private ClientboundCustomQueryPacket(FriendlyByteBuf friendlyByteBuf) {
		this(friendlyByteBuf.readVarInt(), readPayload(friendlyByteBuf.readIdentifier(), friendlyByteBuf));
	}

	private static CustomQueryPayload readPayload(Identifier identifier, FriendlyByteBuf friendlyByteBuf) {
		return readUnknownPayload(identifier, friendlyByteBuf);
	}

	private static DiscardedQueryPayload readUnknownPayload(Identifier identifier, FriendlyByteBuf friendlyByteBuf) {
		int i = friendlyByteBuf.readableBytes();
		if (i >= 0 && i <= 1048576) {
			friendlyByteBuf.skipBytes(i);
			return new DiscardedQueryPayload(identifier);
		} else {
			throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
		}
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeVarInt(this.transactionId);
		friendlyByteBuf.writeIdentifier(this.payload.id());
		this.payload.write(friendlyByteBuf);
	}

	@Override
	public PacketType<ClientboundCustomQueryPacket> type() {
		return LoginPacketTypes.CLIENTBOUND_CUSTOM_QUERY;
	}

	public void handle(ClientLoginPacketListener clientLoginPacketListener) {
		clientLoginPacketListener.handleCustomQuery(this);
	}
}
