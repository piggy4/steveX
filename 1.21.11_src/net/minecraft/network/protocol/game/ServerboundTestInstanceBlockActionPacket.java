package net.minecraft.network.protocol.game;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;

public record ServerboundTestInstanceBlockActionPacket(BlockPos pos, ServerboundTestInstanceBlockActionPacket.Action action, TestInstanceBlockEntity.Data data)
	implements Packet<ServerGamePacketListener> {
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundTestInstanceBlockActionPacket> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		ServerboundTestInstanceBlockActionPacket::pos,
		ServerboundTestInstanceBlockActionPacket.Action.STREAM_CODEC,
		ServerboundTestInstanceBlockActionPacket::action,
		TestInstanceBlockEntity.Data.STREAM_CODEC,
		ServerboundTestInstanceBlockActionPacket::data,
		ServerboundTestInstanceBlockActionPacket::new
	);

	public ServerboundTestInstanceBlockActionPacket(
		BlockPos blockPos,
		ServerboundTestInstanceBlockActionPacket.Action action,
		Optional<ResourceKey<GameTestInstance>> optional,
		Vec3i vec3i,
		Rotation rotation,
		boolean bl
	) {
		this(blockPos, action, new TestInstanceBlockEntity.Data(optional, vec3i, rotation, bl, TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
	}

	@Override
	public PacketType<ServerboundTestInstanceBlockActionPacket> type() {
		return GamePacketTypes.SERVERBOUND_TEST_INSTANCE_BLOCK_ACTION;
	}

	public void handle(ServerGamePacketListener serverGamePacketListener) {
		serverGamePacketListener.handleTestInstanceBlockAction(this);
	}

	public enum Action {
		INIT(0),
		QUERY(1),
		SET(2),
		RESET(3),
		SAVE(4),
		EXPORT(5),
		RUN(6);

		private static final IntFunction<ServerboundTestInstanceBlockActionPacket.Action> BY_ID = ByIdMap.continuous(
			action -> action.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO
		);
		public static final StreamCodec<ByteBuf, ServerboundTestInstanceBlockActionPacket.Action> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, action -> action.id);
		private final int id;

		Action(final int j) {
			this.id = j;
		}
	}
}
