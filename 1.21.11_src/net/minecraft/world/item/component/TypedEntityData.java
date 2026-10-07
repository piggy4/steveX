package net.minecraft.world.item.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;

public final class TypedEntityData<IdType> implements TooltipProvider {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String TYPE_TAG = "id";
	final IdType type;
	final CompoundTag tag;

	public static <T> Codec<TypedEntityData<T>> codec(Codec<T> codec) {
		return new Codec<TypedEntityData<T>>() {
			@Override
			public <V> DataResult<Pair<TypedEntityData<T>, V>> decode(DynamicOps<V> dynamicOps, V object) {
				return CustomData.COMPOUND_TAG_CODEC
					.decode(dynamicOps, object)
					.flatMap(
						pair -> {
							CompoundTag compoundTag = pair.getFirst().copy();
							Tag tag = compoundTag.remove("id");
							return tag == null
								? DataResult.error(() -> "Expected 'id' field in " + object)
								: codec.parse(asNbtOps((DynamicOps<T>)dynamicOps), tag)
									.map(objectxx -> Pair.of((TypedEntityData<T>)(new TypedEntityData<>(objectxx, compoundTag)), (V)pair.getSecond()));
						}
					);
			}

			public <V> DataResult<V> encode(TypedEntityData<T> typedEntityData, DynamicOps<V> dynamicOps, V object) {
				return codec.encodeStart(asNbtOps((DynamicOps<T>)dynamicOps), typedEntityData.type).flatMap(tag -> {
					CompoundTag compoundTag = typedEntityData.tag.copy();
					compoundTag.put("id", tag);
					return CustomData.COMPOUND_TAG_CODEC.encode(compoundTag, dynamicOps, object);
				});
			}

			private static <T> DynamicOps<Tag> asNbtOps(DynamicOps<T> dynamicOps) {
				return dynamicOps instanceof RegistryOps<T> registryOps ? registryOps.withParent(NbtOps.INSTANCE) : NbtOps.INSTANCE;
			}
		};
	}

	public static <B extends ByteBuf, T> StreamCodec<B, TypedEntityData<T>> streamCodec(StreamCodec<B, T> streamCodec) {
		return StreamCodec.composite(
			streamCodec,
			(Function<TypedEntityData<T>, T>)(TypedEntityData::type),
			ByteBufCodecs.COMPOUND_TAG,
			TypedEntityData::tag,
			(BiFunction<T, CompoundTag, TypedEntityData<T>>)(TypedEntityData::new)
		);
	}

	TypedEntityData(IdType object, CompoundTag compoundTag) {
		this.type = object;
		this.tag = stripId(compoundTag);
	}

	public static <T> TypedEntityData<T> of(T object, CompoundTag compoundTag) {
		return new TypedEntityData<>(object, compoundTag);
	}

	private static CompoundTag stripId(CompoundTag compoundTag) {
		if (compoundTag.contains("id")) {
			CompoundTag compoundTag2 = compoundTag.copy();
			compoundTag2.remove("id");
			return compoundTag2;
		} else {
			return compoundTag;
		}
	}

	public IdType type() {
		return this.type;
	}

	public boolean contains(String string) {
		return this.tag.contains(string);
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		} else {
			return !(object instanceof TypedEntityData<?> typedEntityData) ? false : this.type == typedEntityData.type && this.tag.equals(typedEntityData.tag);
		}
	}

	@Override
	public int hashCode() {
		return 31 * this.type.hashCode() + this.tag.hashCode();
	}

	@Override
	public String toString() {
		return this.type + " " + this.tag;
	}

	public void loadInto(Entity entity) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
			entity.saveWithoutId(tagValueOutput);
			CompoundTag compoundTag = tagValueOutput.buildResult();
			UUID uUID = entity.getUUID();
			compoundTag.merge(this.getUnsafe());
			entity.load(TagValueInput.create(scopedCollector, entity.registryAccess(), compoundTag));
			entity.setUUID(uUID);
		}
	}

	public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider provider) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, provider);
			blockEntity.saveCustomOnly(tagValueOutput);
			CompoundTag compoundTag = tagValueOutput.buildResult();
			CompoundTag compoundTag2 = compoundTag.copy();
			compoundTag.merge(this.getUnsafe());
			if (!compoundTag.equals(compoundTag2)) {
				try {
					blockEntity.loadCustomOnly(TagValueInput.create(scopedCollector, provider, compoundTag));
					blockEntity.setChanged();
					return true;
				} catch (Exception exception) {
					LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), exception);

					try {
						blockEntity.loadCustomOnly(TagValueInput.create(scopedCollector.forChild(() -> "(rollback)"), provider, compoundTag2));
					} catch (Exception exception2) {
						LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), exception2);
					}
				}
			}

			return false;
		}
	}

	private CompoundTag tag() {
		return this.tag;
	}

	@Deprecated
	public CompoundTag getUnsafe() {
		return this.tag;
	}

	public CompoundTag copyTagWithoutId() {
		return this.tag.copy();
	}

	@Override
	public void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag, DataComponentGetter dataComponentGetter) {
		if (this.type.getClass() == EntityType.class) {
			EntityType<?> entityType = (EntityType<?>)this.type;
			if (tooltipContext.isPeaceful() && !entityType.isAllowedInPeaceful()) {
				consumer.accept(Component.translatable("item.spawn_egg.peaceful").withStyle(ChatFormatting.RED));
			}
		}
	}
}
