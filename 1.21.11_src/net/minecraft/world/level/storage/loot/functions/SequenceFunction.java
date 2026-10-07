package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public class SequenceFunction implements LootItemFunction {
	public static final MapCodec<SequenceFunction> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(LootItemFunctions.TYPED_CODEC.listOf().fieldOf("functions").forGetter(sequenceFunction -> sequenceFunction.functions))
			.apply(instance, SequenceFunction::new)
	);
	public static final Codec<SequenceFunction> INLINE_CODEC = LootItemFunctions.TYPED_CODEC
		.listOf()
		.xmap(SequenceFunction::new, sequenceFunction -> sequenceFunction.functions);
	private final List<LootItemFunction> functions;
	private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

	private SequenceFunction(List<LootItemFunction> list) {
		this.functions = list;
		this.compositeFunction = LootItemFunctions.compose(list);
	}

	public static SequenceFunction of(List<LootItemFunction> list) {
		return new SequenceFunction(List.copyOf(list));
	}

	public ItemStack apply(ItemStack itemStack, LootContext lootContext) {
		return this.compositeFunction.apply(itemStack, lootContext);
	}

	@Override
	public void validate(ValidationContext validationContext) {
		LootItemFunction.super.validate(validationContext);

		for (int i = 0; i < this.functions.size(); i++) {
			this.functions.get(i).validate(validationContext.forChild(new ProblemReporter.IndexedFieldPathElement("functions", i)));
		}
	}

	@Override
	public LootItemFunctionType<SequenceFunction> getType() {
		return LootItemFunctions.SEQUENCE;
	}
}
