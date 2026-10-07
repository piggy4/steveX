package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundMountScreenOpenPacket implements Packet<ClientGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ClientboundMountScreenOpenPacket> STREAM_CODEC = Packet.codec(
		ClientboundMountScreenOpenPacket::write, ClientboundMountScreenOpenPacket::new
	);
	private final int containerId;
	private final int inventoryColumns;
	private final int entityId;

	public ClientboundMountScreenOpenPacket(int i, int j, int k) {
		this.containerId = i;
		this.inventoryColumns = j;
		this.entityId = k;
	}

	private ClientboundMountScreenOpenPacket(FriendlyByteBuf friendlyByteBuf) {
		this.containerId = friendlyByteBuf.readContainerId();
		this.inventoryColumns = friendlyByteBuf.readVarInt();
		this.entityId = friendlyByteBuf.readInt();
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeContainerId(this.containerId);
		friendlyByteBuf.writeVarInt(this.inventoryColumns);
		friendlyByteBuf.writeInt(this.entityId);
	}

	@Override
	public PacketType<ClientboundMountScreenOpenPacket> type() {
		return GamePacketTypes.CLIENTBOUND_MOUNT_SCREEN_OPEN;
	}

	public void handle(ClientGamePacketListener clientGamePacketListener) {
		clientGamePacketListener.handleMountScreenOpen(this);
	}

	public int getContainerId() {
		return this.containerId;
	}

	public int getInventoryColumns() {
		return this.inventoryColumns;
	}

	public int getEntityId() {
		return this.entityId;
	}
}
