package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.stream.IntStream;

public class ChunkTicketUnpackPosFix extends DataFix {
	private static final long CHUNK_COORD_BITS = 32L;
	private static final long CHUNK_COORD_MASK = 4294967295L;

	public ChunkTicketUnpackPosFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			"ChunkTicketUnpackPosFix",
			this.getInputSchema().getType(References.SAVED_DATA_TICKETS),
			typed -> typed.update(
				DSL.remainderFinder(),
				dynamic -> dynamic.update(
					"data",
					dynamicx -> dynamicx.update(
						"tickets", dynamicxx -> dynamicxx.createList(dynamicxx.asStream().map(dynamicxxx -> dynamicxxx.update("chunk_pos", dynamicxxxx -> {
							long l = dynamicxxxx.asLong(0L);
							int i = (int)(l & 4294967295L);
							int j = (int)(l >>> 32 & 4294967295L);
							return dynamicxxxx.createIntList(IntStream.of(i, j));
						})))
					)
				)
			)
		);
	}
}
