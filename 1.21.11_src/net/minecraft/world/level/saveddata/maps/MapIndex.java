package net.minecraft.world.level.saveddata.maps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class MapIndex extends SavedData {
	private static final int NO_MAP_ID = -1;
	public static final Codec<MapIndex> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(Codec.INT.optionalFieldOf("map", -1).forGetter(mapIndex -> mapIndex.lastMapId)).apply(instance, MapIndex::new)
	);
	public static final SavedDataType<MapIndex> TYPE = new SavedDataType<>("idcounts", MapIndex::new, CODEC, DataFixTypes.SAVED_DATA_MAP_INDEX);
	private int lastMapId;

	public MapIndex() {
		this(-1);
	}

	public MapIndex(int i) {
		this.lastMapId = i;
	}

	public MapId getNextMapId() {
		MapId mapId = new MapId(++this.lastMapId);
		this.setDirty();
		return mapId;
	}
}
