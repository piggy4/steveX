package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import org.slf4j.Logger;

public class LevelUUIDFix extends AbstractUUIDFix {
	private static final Logger LOGGER = LogUtils.getLogger();

	public LevelUUIDFix(Schema schema) {
		super(schema, References.LEVEL);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getType(this.typeReference);
		OpticFinder<?> opticFinder = type.findField("CustomBossEvents");
		OpticFinder<?> opticFinder2 = DSL.typeFinder(
			DSL.and(DSL.optional(DSL.field("Name", this.getInputSchema().getTypeRaw(References.TEXT_COMPONENT))), DSL.remainderType())
		);
		return this.fixTypeEverywhereTyped("LevelUUIDFix", type, typed -> typed.update(DSL.remainderFinder(), dynamic -> {
			dynamic = this.updateDragonFight(dynamic);
			return this.updateWanderingTrader(dynamic);
		}).updateTyped(opticFinder, typedx -> typedx.updateTyped(opticFinder2, typedxx -> typedxx.update(DSL.remainderFinder(), this::updateCustomBossEvent))));
	}

	private Dynamic<?> updateWanderingTrader(Dynamic<?> dynamic) {
		return replaceUUIDString(dynamic, "WanderingTraderId", "WanderingTraderId").orElse(dynamic);
	}

	private Dynamic<?> updateDragonFight(Dynamic<?> dynamic) {
		return dynamic.update(
			"DimensionData",
			dynamicx -> dynamicx.updateMapValues(
				pair -> pair.mapSecond(
					dynamicxx -> dynamicxx.update("DragonFight", dynamicxxx -> replaceUUIDLeastMost(dynamicxxx, "DragonUUID", "Dragon").orElse(dynamicxxx))
				)
			)
		);
	}

	private Dynamic<?> updateCustomBossEvent(Dynamic<?> dynamic) {
		return dynamic.update("Players", dynamic2 -> dynamic.createList(dynamic2.asStream().map(dynamicxx -> createUUIDFromML(dynamicxx).orElseGet(() -> {
			LOGGER.warn("CustomBossEvents contains invalid UUIDs.");
			return dynamicxx;
		}))));
	}
}
