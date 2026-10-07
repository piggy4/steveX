package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;

public class ServerboundSetJigsawBlockPacket implements Packet<ServerGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ServerboundSetJigsawBlockPacket> STREAM_CODEC = Packet.codec(
		ServerboundSetJigsawBlockPacket::write, ServerboundSetJigsawBlockPacket::new
	);
	private final BlockPos pos;
	private final Identifier name;
	private final Identifier target;
	private final Identifier pool;
	private final String finalState;
	private final JigsawBlockEntity.JointType joint;
	private final int selectionPriority;
	private final int placementPriority;

	public ServerboundSetJigsawBlockPacket(
		BlockPos blockPos, Identifier identifier, Identifier identifier2, Identifier identifier3, String string, JigsawBlockEntity.JointType jointType, int i, int j
	) {
		this.pos = blockPos;
		this.name = identifier;
		this.target = identifier2;
		this.pool = identifier3;
		this.finalState = string;
		this.joint = jointType;
		this.selectionPriority = i;
		this.placementPriority = j;
	}

	private ServerboundSetJigsawBlockPacket(FriendlyByteBuf friendlyByteBuf) {
		this.pos = friendlyByteBuf.readBlockPos();
		this.name = friendlyByteBuf.readIdentifier();
		this.target = friendlyByteBuf.readIdentifier();
		this.pool = friendlyByteBuf.readIdentifier();
		this.finalState = friendlyByteBuf.readUtf();
		this.joint = JigsawBlockEntity.JointType.CODEC.byName(friendlyByteBuf.readUtf(), JigsawBlockEntity.JointType.ALIGNED);
		this.selectionPriority = friendlyByteBuf.readVarInt();
		this.placementPriority = friendlyByteBuf.readVarInt();
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeBlockPos(this.pos);
		friendlyByteBuf.writeIdentifier(this.name);
		friendlyByteBuf.writeIdentifier(this.target);
		friendlyByteBuf.writeIdentifier(this.pool);
		friendlyByteBuf.writeUtf(this.finalState);
		friendlyByteBuf.writeUtf(this.joint.getSerializedName());
		friendlyByteBuf.writeVarInt(this.selectionPriority);
		friendlyByteBuf.writeVarInt(this.placementPriority);
	}

	@Override
	public PacketType<ServerboundSetJigsawBlockPacket> type() {
		return GamePacketTypes.SERVERBOUND_SET_JIGSAW_BLOCK;
	}

	public void handle(ServerGamePacketListener serverGamePacketListener) {
		serverGamePacketListener.handleSetJigsawBlock(this);
	}

	public BlockPos getPos() {
		return this.pos;
	}

	public Identifier getName() {
		return this.name;
	}

	public Identifier getTarget() {
		return this.target;
	}

	public Identifier getPool() {
		return this.pool;
	}

	public String getFinalState() {
		return this.finalState;
	}

	public JigsawBlockEntity.JointType getJoint() {
		return this.joint;
	}

	public int getSelectionPriority() {
		return this.selectionPriority;
	}

	public int getPlacementPriority() {
		return this.placementPriority;
	}
}
