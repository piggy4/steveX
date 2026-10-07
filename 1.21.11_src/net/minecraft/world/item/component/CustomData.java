package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public final class CustomData {
	public static final CustomData EMPTY = new CustomData(new CompoundTag());
	public static final Codec<CompoundTag> COMPOUND_TAG_CODEC = Codec.withAlternative(CompoundTag.CODEC, TagParser.FLATTENED_CODEC);
	public static final Codec<CustomData> CODEC = COMPOUND_TAG_CODEC.xmap(CustomData::new, customData -> customData.tag);
	@Deprecated
	public static final StreamCodec<ByteBuf, CustomData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(CustomData::new, customData -> customData.tag);
	private final CompoundTag tag;

	private CustomData(CompoundTag compoundTag) {
		this.tag = compoundTag;
	}

	public static CustomData of(CompoundTag compoundTag) {
		return new CustomData(compoundTag.copy());
	}

	public boolean matchedBy(CompoundTag compoundTag) {
		return NbtUtils.compareNbt(compoundTag, this.tag, true);
	}

	public static void update(DataComponentType<CustomData> dataComponentType, ItemStack itemStack, Consumer<CompoundTag> consumer) {
		CustomData customData = itemStack.getOrDefault(dataComponentType, EMPTY).update(consumer);
		if (customData.tag.isEmpty()) {
			itemStack.remove(dataComponentType);
		} else {
			itemStack.set(dataComponentType, customData);
		}
	}

	public static void set(DataComponentType<CustomData> dataComponentType, ItemStack itemStack, CompoundTag compoundTag) {
		if (!compoundTag.isEmpty()) {
			itemStack.set(dataComponentType, of(compoundTag));
		} else {
			itemStack.remove(dataComponentType);
		}
	}

	public CustomData update(Consumer<CompoundTag> consumer) {
		CompoundTag compoundTag = this.tag.copy();
		consumer.accept(compoundTag);
		return new CustomData(compoundTag);
	}

	public boolean isEmpty() {
		return this.tag.isEmpty();
	}

	public CompoundTag copyTag() {
		return this.tag.copy();
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		} else {
			return object instanceof CustomData customData ? this.tag.equals(customData.tag) : false;
		}
	}

	@Override
	public int hashCode() {
		return this.tag.hashCode();
	}

	@Override
	public String toString() {
		return this.tag.toString();
	}
}
