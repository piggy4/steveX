package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ClientboundSetEntityMotionPacket implements Packet<ClientGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ClientboundSetEntityMotionPacket> STREAM_CODEC = Packet.codec(
		ClientboundSetEntityMotionPacket::write, ClientboundSetEntityMotionPacket::new
	);
	private final int id;
	private final Vec3 movement;

	public ClientboundSetEntityMotionPacket(Entity entity) {
		this(entity.getId(), entity.getDeltaMovement());
	}

	public ClientboundSetEntityMotionPacket(int i, Vec3 vec3) {
		this.id = i;
		this.movement = vec3;
	}

	private ClientboundSetEntityMotionPacket(FriendlyByteBuf friendlyByteBuf) {
		this.id = friendlyByteBuf.readVarInt();
		this.movement = friendlyByteBuf.readLpVec3();
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeVarInt(this.id);
		friendlyByteBuf.writeLpVec3(this.movement);
	}

	@Override
	public PacketType<ClientboundSetEntityMotionPacket> type() {
		return GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION;
	}

	public void handle(ClientGamePacketListener clientGamePacketListener) {
		clientGamePacketListener.handleSetEntityMotion(this);
	}

	public int getId() {
		return this.id;
	}

	public Vec3 getMovement() {
		return this.movement;
	}
}
