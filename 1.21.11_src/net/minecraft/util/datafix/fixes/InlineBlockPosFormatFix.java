package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class InlineBlockPosFormatFix extends DataFix {
	public InlineBlockPosFormatFix(Schema schema) {
		super(schema, false);
	}

	@Override
	public TypeRewriteRule makeRule() {
		OpticFinder<?> opticFinder = this.entityFinder("minecraft:vex");
		OpticFinder<?> opticFinder2 = this.entityFinder("minecraft:phantom");
		OpticFinder<?> opticFinder3 = this.entityFinder("minecraft:turtle");
		List<OpticFinder<?>> list = List.of(
			this.entityFinder("minecraft:item_frame"),
			this.entityFinder("minecraft:glow_item_frame"),
			this.entityFinder("minecraft:painting"),
			this.entityFinder("minecraft:leash_knot")
		);
		return TypeRewriteRule.seq(
			this.fixTypeEverywhereTyped(
				"InlineBlockPosFormatFix - player", this.getInputSchema().getType(References.PLAYER), typed -> typed.update(DSL.remainderFinder(), this::fixPlayer)
			),
			this.fixTypeEverywhereTyped(
				"InlineBlockPosFormatFix - entity",
				this.getInputSchema().getType(References.ENTITY),
				typed -> {
					typed = typed.update(DSL.remainderFinder(), this::fixLivingEntity)
						.updateTyped(opticFinder, typedx -> typedx.update(DSL.remainderFinder(), this::fixVex))
						.updateTyped(opticFinder2, typedx -> typedx.update(DSL.remainderFinder(), this::fixPhantom))
						.updateTyped(opticFinder3, typedx -> typedx.update(DSL.remainderFinder(), this::fixTurtle));

					for (OpticFinder<?> opticFinder4 : list) {
						typed = typed.updateTyped(opticFinder4, typedx -> typedx.update(DSL.remainderFinder(), this::fixBlockAttached));
					}

					return typed;
				}
			)
		);
	}

	private OpticFinder<?> entityFinder(String string) {
		return DSL.namedChoice(string, this.getInputSchema().getChoiceType(References.ENTITY, string));
	}

	private Dynamic<?> fixPlayer(Dynamic<?> dynamic) {
		dynamic = this.fixLivingEntity(dynamic);
		Optional<Number> optional = dynamic.get("SpawnX").asNumber().result();
		Optional<Number> optional2 = dynamic.get("SpawnY").asNumber().result();
		Optional<Number> optional3 = dynamic.get("SpawnZ").asNumber().result();
		if (optional.isPresent() && optional2.isPresent() && optional3.isPresent()) {
			Dynamic<?> dynamic2 = dynamic.createMap(
				Map.of(
					dynamic.createString("pos"), ExtraDataFixUtils.createBlockPos(dynamic, optional.get().intValue(), optional2.get().intValue(), optional3.get().intValue())
				)
			);
			dynamic2 = Dynamic.copyField(dynamic, "SpawnAngle", dynamic2, "angle");
			dynamic2 = Dynamic.copyField(dynamic, "SpawnDimension", dynamic2, "dimension");
			dynamic2 = Dynamic.copyField(dynamic, "SpawnForced", dynamic2, "forced");
			dynamic = dynamic.remove("SpawnX").remove("SpawnY").remove("SpawnZ").remove("SpawnAngle").remove("SpawnDimension").remove("SpawnForced");
			dynamic = dynamic.set("respawn", dynamic2);
		}

		Optional<? extends Dynamic<?>> optional4 = dynamic.get("enteredNetherPosition").result();
		if (optional4.isPresent()) {
			dynamic = dynamic.remove("enteredNetherPosition")
				.set(
					"entered_nether_pos",
					dynamic.createList(
						Stream.of(
							dynamic.createDouble(optional4.get().get("x").asDouble(0.0)),
							dynamic.createDouble(optional4.get().get("y").asDouble(0.0)),
							dynamic.createDouble(optional4.get().get("z").asDouble(0.0))
						)
					)
				);
		}

		return dynamic;
	}

	private Dynamic<?> fixLivingEntity(Dynamic<?> dynamic) {
		return ExtraDataFixUtils.fixInlineBlockPos(dynamic, "SleepingX", "SleepingY", "SleepingZ", "sleeping_pos");
	}

	private Dynamic<?> fixVex(Dynamic<?> dynamic) {
		return ExtraDataFixUtils.fixInlineBlockPos(dynamic.renameField("LifeTicks", "life_ticks"), "BoundX", "BoundY", "BoundZ", "bound_pos");
	}

	private Dynamic<?> fixPhantom(Dynamic<?> dynamic) {
		return ExtraDataFixUtils.fixInlineBlockPos(dynamic.renameField("Size", "size"), "AX", "AY", "AZ", "anchor_pos");
	}

	private Dynamic<?> fixTurtle(Dynamic<?> dynamic) {
		dynamic = dynamic.remove("TravelPosX").remove("TravelPosY").remove("TravelPosZ");
		dynamic = ExtraDataFixUtils.fixInlineBlockPos(dynamic, "HomePosX", "HomePosY", "HomePosZ", "home_pos");
		return dynamic.renameField("HasEgg", "has_egg");
	}

	private Dynamic<?> fixBlockAttached(Dynamic<?> dynamic) {
		return ExtraDataFixUtils.fixInlineBlockPos(dynamic, "TileX", "TileY", "TileZ", "block_pos");
	}
}
