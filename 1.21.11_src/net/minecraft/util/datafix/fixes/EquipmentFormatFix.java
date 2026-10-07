package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class EquipmentFormatFix extends DataFix {
	public EquipmentFormatFix(Schema schema) {
		super(schema, true);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getTypeRaw(References.ITEM_STACK);
		Type<?> type2 = this.getOutputSchema().getTypeRaw(References.ITEM_STACK);
		OpticFinder<?> opticFinder = type.findField("id");
		return this.fix(type, type2, opticFinder);
	}

	private <ItemStackOld, ItemStackNew> TypeRewriteRule fix(Type<ItemStackOld> type, Type<ItemStackNew> type2, OpticFinder<?> opticFinder) {
		Type<Pair<String, Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<ItemStackOld, Unit>, Either<ItemStackOld, Unit>>>>>> type3 = DSL.named(
			References.ENTITY_EQUIPMENT.typeName(),
			DSL.and(
				DSL.optional(DSL.field("ArmorItems", DSL.list(type))),
				DSL.optional(DSL.field("HandItems", DSL.list(type))),
				DSL.optional(DSL.field("body_armor_item", type)),
				DSL.optional(DSL.field("saddle", type))
			)
		);
		Type<Pair<String, Either<Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Dynamic<?>>>>>>>>>, Unit>>> type4 = DSL.named(
			References.ENTITY_EQUIPMENT.typeName(),
			DSL.optional(
				DSL.field(
					"equipment",
					DSL.and(
						DSL.optional(DSL.field("mainhand", type2)),
						DSL.optional(DSL.field("offhand", type2)),
						DSL.optional(DSL.field("feet", type2)),
						DSL.and(
							DSL.optional(DSL.field("legs", type2)),
							DSL.optional(DSL.field("chest", type2)),
							DSL.optional(DSL.field("head", type2)),
							DSL.and(DSL.optional(DSL.field("body", type2)), DSL.optional(DSL.field("saddle", type2)), DSL.remainderType())
						)
					)
				)
			)
		);
		if (!type3.equals(this.getInputSchema().getType(References.ENTITY_EQUIPMENT))) {
			throw new IllegalStateException("Input entity_equipment type does not match expected");
		} else if (!type4.equals(this.getOutputSchema().getType(References.ENTITY_EQUIPMENT))) {
			throw new IllegalStateException("Output entity_equipment type does not match expected");
		} else {
			return this.fixTypeEverywhere(
				"EquipmentFormatFix",
				type3,
				type4,
				dynamicOps -> {
					Predicate<ItemStackOld> predicate = object -> {
						Typed<ItemStackOld> typed = new Typed<>(type, dynamicOps, object);
						return typed.getOptional(opticFinder).isEmpty();
					};
					return pair -> {
						String string = pair.getFirst();
						Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<ItemStackOld, Unit>, Either<ItemStackOld, Unit>>>> pair2 = pair.getSecond();
						List<ItemStackOld> list = pair2.getFirst().map(Function.identity(), unit -> List.of());
						List<ItemStackOld> list2 = pair2.getSecond().getFirst().map(Function.identity(), unit -> List.of());
						Either<ItemStackOld, Unit> either = pair2.getSecond().getSecond().getFirst();
						Either<ItemStackOld, Unit> either2 = pair2.getSecond().getSecond().getSecond();
						Either<ItemStackOld, Unit> either3 = getItemFromList(0, list, predicate);
						Either<ItemStackOld, Unit> either4 = getItemFromList(1, list, predicate);
						Either<ItemStackOld, Unit> either5 = getItemFromList(2, list, predicate);
						Either<ItemStackOld, Unit> either6 = getItemFromList(3, list, predicate);
						Either<ItemStackOld, Unit> either7 = getItemFromList(0, list2, predicate);
						Either<ItemStackOld, Unit> either8 = getItemFromList(1, list2, predicate);
						return areAllEmpty(either, either2, either3, either4, either5, either6, either7, either8)
							? Pair.of(string, Either.right(Unit.INSTANCE))
							: Pair.of(
								string,
								Either.left(
									Pair.of(
										(Either<ItemStackNew, Unit>)either7,
										Pair.of(
											(Either<ItemStackNew, Unit>)either8,
											Pair.of(
												(Either<ItemStackNew, Unit>)either3,
												Pair.of(
													(Either<ItemStackNew, Unit>)either4,
													Pair.of(
														(Either<ItemStackNew, Unit>)either5,
														Pair.of(
															(Either<ItemStackNew, Unit>)either6,
															Pair.of((Either<ItemStackNew, Unit>)either, Pair.of((Either<ItemStackNew, Unit>)either2, new Dynamic(dynamicOps)))
														)
													)
												)
											)
										)
									)
								)
							);
					};
				}
			);
		}
	}

	@SafeVarargs
	private static boolean areAllEmpty(Either<?, Unit>... eithers) {
		for (Either<?, Unit> either : eithers) {
			if (either.right().isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private static <ItemStack> Either<ItemStack, Unit> getItemFromList(int i, List<ItemStack> list, Predicate<ItemStack> predicate) {
		if (i >= list.size()) {
			return Either.right(Unit.INSTANCE);
		}

		ItemStack object = list.get(i);
		return predicate.test(object) ? Either.right(Unit.INSTANCE) : Either.left(object);
	}
}
