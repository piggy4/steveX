package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class EntityEquipmentToArmorAndHandFix extends DataFix {
	public EntityEquipmentToArmorAndHandFix(Schema schema) {
		super(schema, true);
	}

	@Override
	public TypeRewriteRule makeRule() {
		return this.cap(this.getInputSchema().getTypeRaw(References.ITEM_STACK), this.getOutputSchema().getTypeRaw(References.ITEM_STACK));
	}

	private <ItemStackOld, ItemStackNew> TypeRewriteRule cap(Type<ItemStackOld> type, Type<ItemStackNew> type2) {
		Type<Pair<String, Either<List<ItemStackOld>, Unit>>> type3 = DSL.named(
			References.ENTITY_EQUIPMENT.typeName(), DSL.optional(DSL.field("Equipment", DSL.list(type)))
		);
		Type<Pair<String, Pair<Either<List<ItemStackNew>, Unit>, Pair<Either<List<ItemStackNew>, Unit>, Pair<Either<ItemStackNew, Unit>, Either<ItemStackNew, Unit>>>>>> type4 = DSL.named(
			References.ENTITY_EQUIPMENT.typeName(),
			DSL.and(
				DSL.optional(DSL.field("ArmorItems", DSL.list(type2))),
				DSL.optional(DSL.field("HandItems", DSL.list(type2))),
				DSL.optional(DSL.field("body_armor_item", type2)),
				DSL.optional(DSL.field("saddle", type2))
			)
		);
		if (!type3.equals(this.getInputSchema().getType(References.ENTITY_EQUIPMENT))) {
			throw new IllegalStateException("Input entity_equipment type does not match expected");
		} else if (!type4.equals(this.getOutputSchema().getType(References.ENTITY_EQUIPMENT))) {
			throw new IllegalStateException("Output entity_equipment type does not match expected");
		} else {
			return TypeRewriteRule.seq(
				this.fixTypeEverywhereTyped(
					"EntityEquipmentToArmorAndHandFix - drop chances",
					this.getInputSchema().getType(References.ENTITY),
					typed -> typed.update(DSL.remainderFinder(), EntityEquipmentToArmorAndHandFix::fixDropChances)
				),
				this.fixTypeEverywhere(
					"EntityEquipmentToArmorAndHandFix - equipment",
					type3,
					type4,
					dynamicOps -> {
						ItemStackNew object = type2.read(new Dynamic<>(dynamicOps).emptyMap())
							.result()
							.orElseThrow(() -> new IllegalStateException("Could not parse newly created empty itemstack."))
							.getFirst();
						Either<ItemStackNew, Unit> either = Either.right(DSL.unit());
						return pair -> pair.mapSecond(either2 -> {
							List<ItemStackOld> list = either2.map(Function.identity(), unit -> List.of());
							Either<List<ItemStackNew>, Unit> either3 = Either.right(DSL.unit());
							Either<List<ItemStackNew>, Unit> either4 = Either.right(DSL.unit());
							if (!list.isEmpty()) {
								either3 = Either.left(Lists.newArrayList((ItemStackNew[])(new Object[]{list.getFirst(), object})));
							}

							if (list.size() > 1) {
								List<ItemStackNew> list2 = Lists.newArrayList(object, object, object, object);

								for (int i = 1; i < Math.min(list.size(), 5); i++) {
									list2.set(i - 1, (ItemStackNew)list.get(i));
								}

								either4 = Either.left(list2);
							}

							return Pair.of(either4, Pair.of(either3, Pair.of(either, either)));
						});
					}
				)
			);
		}
	}

	private static Dynamic<?> fixDropChances(Dynamic<?> dynamic) {
		Optional<? extends Stream<? extends Dynamic<?>>> optional = dynamic.get("DropChances").asStreamOpt().result();
		dynamic = dynamic.remove("DropChances");
		if (optional.isPresent()) {
			Iterator<Float> iterator = Stream.concat(optional.get().map(dynamicx -> dynamicx.asFloat(0.0F)), Stream.generate(() -> 0.0F)).iterator();
			float f = iterator.next();
			if (dynamic.get("HandDropChances").result().isEmpty()) {
				dynamic = dynamic.set("HandDropChances", dynamic.createList(Stream.of(f, 0.0F).map(dynamic::createFloat)));
			}

			if (dynamic.get("ArmorDropChances").result().isEmpty()) {
				dynamic = dynamic.set(
					"ArmorDropChances", dynamic.createList(Stream.of(iterator.next(), iterator.next(), iterator.next(), iterator.next()).map(dynamic::createFloat))
				);
			}
		}

		return dynamic;
	}
}
