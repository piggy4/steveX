package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class DynamicLoot extends LootPoolSingletonContainer {
	public static final MapCodec<DynamicLoot> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Identifier.CODEC.fieldOf("name").forGetter(dynamicLoot -> dynamicLoot.name))
			.and(singletonFields(instance))
			.apply(instance, DynamicLoot::new)
	);
	private final Identifier name;

	private DynamicLoot(Identifier identifier, int i, int j, List<LootItemCondition> list, List<LootItemFunction> list2) {
		super(i, j, list, list2);
		this.name = identifier;
	}

	@Override
	public LootPoolEntryType getType() {
		return LootPoolEntries.DYNAMIC;
	}

	@Override
	public void createItemStack(Consumer<ItemStack> consumer, LootContext lootContext) {
		lootContext.addDynamicDrops(this.name, consumer);
	}

	public static LootPoolSingletonContainer.Builder<?> dynamicEntry(Identifier identifier) {
		return simpleBuilder((i, j, list, list2) -> new DynamicLoot(identifier, i, j, list, list2));
	}
}
