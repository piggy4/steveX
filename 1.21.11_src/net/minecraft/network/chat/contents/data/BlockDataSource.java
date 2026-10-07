package net.minecraft.network.chat.contents.data;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public record BlockDataSource(String posPattern, @Nullable Coordinates compiledPos) implements DataSource {
	public static final MapCodec<BlockDataSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Codec.STRING.fieldOf("block").forGetter(BlockDataSource::posPattern)).apply(instance, BlockDataSource::new)
	);

	public BlockDataSource(String string) {
		this(string, compilePos(string));
	}

	private static @Nullable Coordinates compilePos(String string) {
		try {
			return BlockPosArgument.blockPos().parse(new StringReader(string));
		} catch (CommandSyntaxException commandSyntaxException) {
			return null;
		}
	}

	@Override
	public Stream<CompoundTag> getData(CommandSourceStack commandSourceStack) {
		if (this.compiledPos != null) {
			ServerLevel serverLevel = commandSourceStack.getLevel();
			BlockPos blockPos = this.compiledPos.getBlockPos(commandSourceStack);
			if (serverLevel.isLoaded(blockPos)) {
				BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
				if (blockEntity != null) {
					return Stream.of(blockEntity.saveWithFullMetadata(commandSourceStack.registryAccess()));
				}
			}
		}

		return Stream.empty();
	}

	@Override
	public MapCodec<BlockDataSource> codec() {
		return MAP_CODEC;
	}

	@Override
	public String toString() {
		return "block=" + this.posPattern;
	}

	@Override
	public boolean equals(Object object) {
		return this == object ? true : object instanceof BlockDataSource blockDataSource && this.posPattern.equals(blockDataSource.posPattern);
	}

	@Override
	public int hashCode() {
		return this.posPattern.hashCode();
	}
}
