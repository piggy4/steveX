package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class ClientboundAddEntityPacket implements Packet<ClientGamePacketListener> {
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAddEntityPacket> STREAM_CODEC = Packet.codec(
		ClientboundAddEntityPacket::write, ClientboundAddEntityPacket::new
	);
	private final int id;
	private final UUID uuid;
	private final EntityType<?> type;
	private final double x;
	private final double y;
	private final double z;
	private final Vec3 movement;
	private final byte xRot;
	private final byte yRot;
	private final byte yHeadRot;
	private final int data;

	public ClientboundAddEntityPacket(Entity entity, ServerEntity serverEntity) {
		this(entity, serverEntity, 0);
	}

	public ClientboundAddEntityPacket(Entity entity, ServerEntity serverEntity, int i) {
		this(
			entity.getId(),
			entity.getUUID(),
			serverEntity.getPositionBase().x(),
			serverEntity.getPositionBase().y(),
			serverEntity.getPositionBase().z(),
			serverEntity.getLastSentXRot(),
			serverEntity.getLastSentYRot(),
			entity.getType(),
			i,
			serverEntity.getLastSentMovement(),
			serverEntity.getLastSentYHeadRot()
		);
	}

	public ClientboundAddEntityPacket(Entity entity, int i, BlockPos blockPos) {
		this(
			entity.getId(),
			entity.getUUID(),
			blockPos.getX(),
			blockPos.getY(),
			blockPos.getZ(),
			entity.getXRot(),
			entity.getYRot(),
			entity.getType(),
			i,
			entity.getDeltaMovement(),
			entity.getYHeadRot()
		);
	}

	public ClientboundAddEntityPacket(int i, UUID uUID, double d, double e, double f, float g, float h, EntityType<?> entityType, int j, Vec3 vec3, double k) {
		this.id = i;
		this.uuid = uUID;
		this.x = d;
		this.y = e;
		this.z = f;
		this.movement = vec3;
		this.xRot = Mth.packDegrees(g);
		this.yRot = Mth.packDegrees(h);
		this.yHeadRot = Mth.packDegrees((float)k);
		this.type = entityType;
		this.data = j;
	}

	private ClientboundAddEntityPacket(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
		this.id = registryFriendlyByteBuf.readVarInt();
		this.uuid = registryFriendlyByteBuf.readUUID();
		this.type = ByteBufCodecs.registry(Registries.ENTITY_TYPE).decode(registryFriendlyByteBuf);
		this.x = registryFriendlyByteBuf.readDouble();
		this.y = registryFriendlyByteBuf.readDouble();
		this.z = registryFriendlyByteBuf.readDouble();
		this.movement = registryFriendlyByteBuf.readLpVec3();
		this.xRot = registryFriendlyByteBuf.readByte();
		this.yRot = registryFriendlyByteBuf.readByte();
		this.yHeadRot = registryFriendlyByteBuf.readByte();
		this.data = registryFriendlyByteBuf.readVarInt();
	}

	private void write(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
		registryFriendlyByteBuf.writeVarInt(this.id);
		registryFriendlyByteBuf.writeUUID(this.uuid);
		ByteBufCodecs.registry(Registries.ENTITY_TYPE).encode(registryFriendlyByteBuf, this.type);
		registryFriendlyByteBuf.writeDouble(this.x);
		registryFriendlyByteBuf.writeDouble(this.y);
		registryFriendlyByteBuf.writeDouble(this.z);
		registryFriendlyByteBuf.writeLpVec3(this.movement);
		registryFriendlyByteBuf.writeByte(this.xRot);
		registryFriendlyByteBuf.writeByte(this.yRot);
		registryFriendlyByteBuf.writeByte(this.yHeadRot);
		registryFriendlyByteBuf.writeVarInt(this.data);
	}

	@Override
	public PacketType<ClientboundAddEntityPacket> type() {
		return GamePacketTypes.CLIENTBOUND_ADD_ENTITY;
	}

	public void handle(ClientGamePacketListener clientGamePacketListener) {
		clientGamePacketListener.handleAddEntity(this);
	}

	public int getId() {
		return this.id;
	}

	public UUID getUUID() {
		return this.uuid;
	}

	public EntityType<?> getType() {
		return this.type;
	}

	public double getX() {
		return this.x;
	}

	public double getY() {
		return this.y;
	}

	public double getZ() {
		return this.z;
	}

	public Vec3 getMovement() {
		return this.movement;
	}

	public float getXRot() {
		return Mth.unpackDegrees(this.xRot);
	}

	public float getYRot() {
		return Mth.unpackDegrees(this.yRot);
	}

	public float getYHeadRot() {
		return Mth.unpackDegrees(this.yHeadRot);
	}

	public int getData() {
		return this.data;
	}
}
