package net.minecraft.world.level.levelgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class StructureFeatureIndexSavedData extends SavedData {
	private final LongSet all;
	private final LongSet remaining;
	private static final Codec<LongSet> LONG_SET = Codec.LONG_STREAM.xmap(LongOpenHashSet::toSet, LongCollection::longStream);
	public static final Codec<StructureFeatureIndexSavedData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				LONG_SET.fieldOf("All").forGetter(structureFeatureIndexSavedData -> structureFeatureIndexSavedData.all),
				LONG_SET.fieldOf("Remaining").forGetter(structureFeatureIndexSavedData -> structureFeatureIndexSavedData.remaining)
			)
			.apply(instance, StructureFeatureIndexSavedData::new)
	);

	public static SavedDataType<StructureFeatureIndexSavedData> type(String string) {
		return new SavedDataType<>(string, StructureFeatureIndexSavedData::new, CODEC, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES);
	}

	private StructureFeatureIndexSavedData(LongSet longSet, LongSet longSet2) {
		this.all = longSet;
		this.remaining = longSet2;
	}

	public StructureFeatureIndexSavedData() {
		this(new LongOpenHashSet(), new LongOpenHashSet());
	}

	public void addIndex(long l) {
		this.all.add(l);
		this.remaining.add(l);
		this.setDirty();
	}

	public boolean hasStartIndex(long l) {
		return this.all.contains(l);
	}

	public boolean hasUnhandledIndex(long l) {
		return this.remaining.contains(l);
	}

	public void removeIndex(long l) {
		if (this.remaining.remove(l)) {
			this.setDirty();
		}
	}

	public LongSet getAll() {
		return this.all;
	}
}
