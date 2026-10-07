package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityFurnaceBurnTimeFix extends NamedEntityFix {
	public BlockEntityFurnaceBurnTimeFix(Schema schema, String string) {
		super(schema, false, "BlockEntityFurnaceBurnTimeFix" + string, References.BLOCK_ENTITY, string);
	}

	public Dynamic<?> fixBurnTime(Dynamic<?> dynamic) {
		dynamic = dynamic.renameField("CookTime", "cooking_time_spent");
		dynamic = dynamic.renameField("CookTimeTotal", "cooking_total_time");
		dynamic = dynamic.renameField("BurnTime", "lit_time_remaining");
		return dynamic.setFieldIfPresent("lit_total_time", dynamic.get("lit_time_remaining").result());
	}

	@Override
	protected Typed<?> fix(Typed<?> typed) {
		return typed.update(DSL.remainderFinder(), this::fixBurnTime);
	}
}
