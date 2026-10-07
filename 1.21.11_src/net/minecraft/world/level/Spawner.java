package net.minecraft.world.level;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

public interface Spawner {
	void setEntityId(EntityType<?> entityType, RandomSource randomSource);

	static void appendHoverText(@Nullable TypedEntityData<BlockEntityType<?>> typedEntityData, Consumer<Component> consumer, String string) {
		Component component = getSpawnEntityDisplayName(typedEntityData, string);
		if (component != null) {
			consumer.accept(component);
		} else {
			consumer.accept(CommonComponents.EMPTY);
			consumer.accept(Component.translatable("block.minecraft.spawner.desc1").withStyle(ChatFormatting.GRAY));
			consumer.accept(CommonComponents.space().append(Component.translatable("block.minecraft.spawner.desc2").withStyle(ChatFormatting.BLUE)));
		}
	}

	static @Nullable Component getSpawnEntityDisplayName(@Nullable TypedEntityData<BlockEntityType<?>> typedEntityData, String string) {
		return typedEntityData == null
			? null
			: typedEntityData.getUnsafe()
				.getCompound(string)
				.flatMap(compoundTag -> compoundTag.getCompound("entity"))
				.flatMap(compoundTag -> compoundTag.read("id", EntityType.CODEC))
				.map(entityType -> Component.translatable(entityType.getDescriptionId()).withStyle(ChatFormatting.GRAY))
				.orElse(null);
	}
}
