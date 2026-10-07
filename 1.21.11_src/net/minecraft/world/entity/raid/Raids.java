package net.minecraft.world.entity.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Raids extends SavedData {
	private static final String RAID_FILE_ID = "raids";
	public static final Codec<Raids> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Raids.RaidWithId.CODEC
					.listOf()
					.optionalFieldOf("raids", List.of())
					.forGetter(raids -> raids.raidMap.int2ObjectEntrySet().stream().map(Raids.RaidWithId::from).toList()),
				Codec.INT.fieldOf("next_id").forGetter(raids -> raids.nextId),
				Codec.INT.fieldOf("tick").forGetter(raids -> raids.tick)
			)
			.apply(instance, Raids::new)
	);
	public static final SavedDataType<Raids> TYPE = new SavedDataType<>("raids", Raids::new, CODEC, DataFixTypes.SAVED_DATA_RAIDS);
	public static final SavedDataType<Raids> TYPE_END = new SavedDataType<>("raids_end", Raids::new, CODEC, DataFixTypes.SAVED_DATA_RAIDS);
	private final Int2ObjectMap<Raid> raidMap = new Int2ObjectOpenHashMap<>();
	private int nextId = 1;
	private int tick;

	public static SavedDataType<Raids> getType(Holder<DimensionType> holder) {
		return holder.is(BuiltinDimensionTypes.END) ? TYPE_END : TYPE;
	}

	public Raids() {
		this.setDirty();
	}

	private Raids(List<Raids.RaidWithId> list, int i, int j) {
		for (Raids.RaidWithId raidWithId : list) {
			this.raidMap.put(raidWithId.id, raidWithId.raid);
		}

		this.nextId = i;
		this.tick = j;
	}

	public @Nullable Raid get(int i) {
		return this.raidMap.get(i);
	}

	public OptionalInt getId(Raid raid) {
		for (Entry<Raid> entry : this.raidMap.int2ObjectEntrySet()) {
			if (entry.getValue() == raid) {
				return OptionalInt.of(entry.getIntKey());
			}
		}

		return OptionalInt.empty();
	}

	public void tick(ServerLevel serverLevel) {
		this.tick++;
		Iterator<Raid> iterator = this.raidMap.values().iterator();

		while (iterator.hasNext()) {
			Raid raid = iterator.next();
			if (!serverLevel.getGameRules().get(GameRules.RAIDS)) {
				raid.stop();
			}

			if (raid.isStopped()) {
				iterator.remove();
				this.setDirty();
			} else {
				raid.tick(serverLevel);
			}
		}

		if (this.tick % 200 == 0) {
			this.setDirty();
		}
	}

	public static boolean canJoinRaid(Raider raider) {
		return raider.isAlive() && raider.canJoinRaid() && raider.getNoActionTime() <= 2400;
	}

	public @Nullable Raid createOrExtendRaid(ServerPlayer serverPlayer, BlockPos blockPos) {
		if (serverPlayer.isSpectator()) {
			return null;
		}

		ServerLevel serverLevel = serverPlayer.level();
		if (!serverLevel.getGameRules().get(GameRules.RAIDS)) {
			return null;
		}

		if (!serverLevel.environmentAttributes().getValue(EnvironmentAttributes.CAN_START_RAID, blockPos)) {
			return null;
		}

		List<PoiRecord> list = serverLevel.getPoiManager()
			.getInRange(holder -> holder.is(PoiTypeTags.VILLAGE), blockPos, 64, PoiManager.Occupancy.IS_OCCUPIED)
			.toList();
		int i = 0;
		Vec3 vec3 = Vec3.ZERO;

		for (PoiRecord poiRecord : list) {
			BlockPos blockPos2 = poiRecord.getPos();
			vec3 = vec3.add(blockPos2.getX(), blockPos2.getY(), blockPos2.getZ());
			i++;
		}

		BlockPos blockPos3;
		if (i > 0) {
			vec3 = vec3.scale(1.0 / i);
			blockPos3 = BlockPos.containing(vec3);
		} else {
			blockPos3 = blockPos;
		}

		Raid raid = this.getOrCreateRaid(serverLevel, blockPos3);
		if (!raid.isStarted() && !this.raidMap.containsValue(raid)) {
			this.raidMap.put(this.getUniqueId(), raid);
		}

		if (!raid.isStarted() || raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel()) {
			raid.absorbRaidOmen(serverPlayer);
		}

		this.setDirty();
		return raid;
	}

	private Raid getOrCreateRaid(ServerLevel serverLevel, BlockPos blockPos) {
		Raid raid = serverLevel.getRaidAt(blockPos);
		return raid != null ? raid : new Raid(blockPos, serverLevel.getDifficulty());
	}

	public static Raids load(CompoundTag compoundTag) {
		return CODEC.parse(NbtOps.INSTANCE, compoundTag).resultOrPartial().orElseGet(Raids::new);
	}

	private int getUniqueId() {
		return ++this.nextId;
	}

	public @Nullable Raid getNearbyRaid(BlockPos blockPos, int i) {
		Raid raid = null;
		double d = i;

		for (Raid raid2 : this.raidMap.values()) {
			double e = raid2.getCenter().distSqr(blockPos);
			if (raid2.isActive() && e < d) {
				raid = raid2;
				d = e;
			}
		}

		return raid;
	}

	@VisibleForDebug
	public List<BlockPos> getRaidCentersInChunk(ChunkPos chunkPos) {
		return this.raidMap.values().stream().map(Raid::getCenter).filter(chunkPos::contains).toList();
	}

	record RaidWithId(int id, Raid raid) {
		public static final Codec<Raids.RaidWithId> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(Codec.INT.fieldOf("id").forGetter(Raids.RaidWithId::id), Raid.MAP_CODEC.forGetter(Raids.RaidWithId::raid))
				.apply(instance, Raids.RaidWithId::new)
		);

		public static Raids.RaidWithId from(Entry<Raid> entry) {
			return new Raids.RaidWithId(entry.getIntKey(), entry.getValue());
		}
	}
}
