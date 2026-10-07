package net.minecraft.network.protocol.cookie;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public record ClientboundCookieRequestPacket(Identifier key) implements Packet<ClientCookiePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ClientboundCookieRequestPacket> STREAM_CODEC = Packet.codec(
		ClientboundCookieRequestPacket::write, ClientboundCookieRequestPacket::new
	);

	private ClientboundCookieRequestPacket(FriendlyByteBuf friendlyByteBuf) {
		this(friendlyByteBuf.readIdentifier());
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeIdentifier(this.key);
	}

	@Override
	public PacketType<ClientboundCookieRequestPacket> type() {
		return CookiePacketTypes.CLIENTBOUND_COOKIE_REQUEST;
	}

	public void handle(ClientCookiePacketListener clientCookiePacketListener) {
		clientCookiePacketListener.handleRequestCookie(this);
	}
}
