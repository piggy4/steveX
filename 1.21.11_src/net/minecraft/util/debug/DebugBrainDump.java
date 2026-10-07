package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

public record DebugBrainDump(
	String name,
	String profession,
	int xp,
	float health,
	float maxHealth,
	String inventory,
	boolean wantsGolem,
	int angerLevel,
	List<String> activities,
	List<String> behaviors,
	List<String> memories,
	List<String> gossips,
	Set<BlockPos> pois,
	Set<BlockPos> potentialPois
) {
	public static final StreamCodec<FriendlyByteBuf, DebugBrainDump> STREAM_CODEC = StreamCodec.of(
		(friendlyByteBuf, debugBrainDump) -> debugBrainDump.write(friendlyByteBuf), DebugBrainDump::new
	);

	public DebugBrainDump(FriendlyByteBuf friendlyByteBuf) {
		this(
			friendlyByteBuf.readUtf(),
			friendlyByteBuf.readUtf(),
			friendlyByteBuf.readInt(),
			friendlyByteBuf.readFloat(),
			friendlyByteBuf.readFloat(),
			friendlyByteBuf.readUtf(),
			friendlyByteBuf.readBoolean(),
			friendlyByteBuf.readInt(),
			friendlyByteBuf.readList(FriendlyByteBuf::readUtf),
			friendlyByteBuf.readList(FriendlyByteBuf::readUtf),
			friendlyByteBuf.readList(FriendlyByteBuf::readUtf),
			friendlyByteBuf.readList(FriendlyByteBuf::readUtf),
			friendlyByteBuf.readCollection(HashSet::new, BlockPos.STREAM_CODEC),
			friendlyByteBuf.readCollection(HashSet::new, BlockPos.STREAM_CODEC)
		);
	}

	public void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeUtf(this.name);
		friendlyByteBuf.writeUtf(this.profession);
		friendlyByteBuf.writeInt(this.xp);
		friendlyByteBuf.writeFloat(this.health);
		friendlyByteBuf.writeFloat(this.maxHealth);
		friendlyByteBuf.writeUtf(this.inventory);
		friendlyByteBuf.writeBoolean(this.wantsGolem);
		friendlyByteBuf.writeInt(this.angerLevel);
		friendlyByteBuf.writeCollection(this.activities, FriendlyByteBuf::writeUtf);
		friendlyByteBuf.writeCollection(this.behaviors, FriendlyByteBuf::writeUtf);
		friendlyByteBuf.writeCollection(this.memories, FriendlyByteBuf::writeUtf);
		friendlyByteBuf.writeCollection(this.gossips, FriendlyByteBuf::writeUtf);
		friendlyByteBuf.writeCollection(this.pois, BlockPos.STREAM_CODEC);
		friendlyByteBuf.writeCollection(this.potentialPois, BlockPos.STREAM_CODEC);
	}

	public static DebugBrainDump takeBrainDump(ServerLevel serverLevel, LivingEntity livingEntity) {
		String string = DebugEntityNameGenerator.getEntityName(livingEntity);
		String string2;
		int i;
		if (livingEntity instanceof Villager villager) {
			string2 = villager.getVillagerData().profession().getRegisteredName();
			i = villager.getVillagerXp();
		} else {
			string2 = "";
			i = 0;
		}

		float f = livingEntity.getHealth();
		float g = livingEntity.getMaxHealth();
		Brain<?> brain = livingEntity.getBrain();
		long l = livingEntity.level().getGameTime();
		String string3;
		if (livingEntity instanceof InventoryCarrier inventoryCarrier) {
			Container container = inventoryCarrier.getInventory();
			string3 = container.isEmpty() ? "" : container.toString();
		} else {
			string3 = "";
		}

		boolean bl = livingEntity instanceof Villager villager2 && villager2.wantsToSpawnGolem(l);
		int j = livingEntity instanceof Warden warden ? warden.getClientAngerLevel() : -1;
		List<String> list = brain.getActiveActivities().stream().map(Activity::getName).toList();
		List<String> list2 = brain.getRunningBehaviors().stream().map(BehaviorControl::debugString).toList();
		List<String> list3 = getMemoryDescriptions(serverLevel, livingEntity, l).map(stringx -> StringUtil.truncateStringIfNecessary(stringx, 255, true)).toList();
		Set<BlockPos> set = getKnownBlockPositions(brain, MemoryModuleType.JOB_SITE, MemoryModuleType.HOME, MemoryModuleType.MEETING_POINT);
		Set<BlockPos> set2 = getKnownBlockPositions(brain, MemoryModuleType.POTENTIAL_JOB_SITE);
		List<String> list4 = livingEntity instanceof Villager villager3 ? getVillagerGossips(villager3) : List.of();
		return new DebugBrainDump(string, string2, i, f, g, string3, bl, j, list, list2, list3, list4, set, set2);
	}

	@SafeVarargs
	private static Set<BlockPos> getKnownBlockPositions(Brain<?> brain, MemoryModuleType<GlobalPos>... memoryModuleTypes) {
		return Stream.of(memoryModuleTypes)
			.filter(brain::hasMemoryValue)
			.map(brain::getMemory)
			.flatMap(Optional::stream)
			.map(GlobalPos::pos)
			.collect(Collectors.toSet());
	}

	private static List<String> getVillagerGossips(Villager villager) {
		List<String> list = new ArrayList<>();
		villager.getGossips().getGossipEntries().forEach((uUID, object2IntMap) -> {
			String string = DebugEntityNameGenerator.getEntityName(uUID);
			object2IntMap.forEach((gossipType, i) -> list.add(string + ": " + gossipType + ": " + i));
		});
		return list;
	}

	private static Stream<String> getMemoryDescriptions(ServerLevel serverLevel, LivingEntity livingEntity, long l) {
		return livingEntity.getBrain().getMemories().entrySet().stream().map(entry -> {
			MemoryModuleType<?> memoryModuleType = entry.getKey();
			Optional<? extends ExpirableValue<?>> optional = entry.getValue();
			return getMemoryDescription(serverLevel, l, memoryModuleType, optional);
		}).sorted();
	}

	private static String getMemoryDescription(
		ServerLevel serverLevel, long l, MemoryModuleType<?> memoryModuleType, Optional<? extends ExpirableValue<?>> optional
	) {
		String string;
		if (optional.isPresent()) {
			ExpirableValue<?> expirableValue = (ExpirableValue<?>)optional.get();
			Object object = expirableValue.getValue();
			if (memoryModuleType == MemoryModuleType.HEARD_BELL_TIME) {
				long m = l - (Long)object;
				string = m + " ticks ago";
			} else if (expirableValue.canExpire()) {
				string = getShortDescription(serverLevel, object) + " (ttl: " + expirableValue.getTimeToLive() + ")";
			} else {
				string = getShortDescription(serverLevel, object);
			}
		} else {
			string = "-";
		}

		return BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(memoryModuleType).getPath() + ": " + string;
	}

	private static String getShortDescription(ServerLevel serverLevel, @Nullable Object object) {
		return switch (object) {
			case null -> "-";
			case UUID uUID -> getShortDescription(serverLevel, serverLevel.getEntity(uUID));
			case Entity entity -> DebugEntityNameGenerator.getEntityName(entity);
			case WalkTarget walkTarget -> getShortDescription(serverLevel, walkTarget.getTarget());
			case EntityTracker entityTracker -> getShortDescription(serverLevel, entityTracker.getEntity());
			case GlobalPos globalPos -> getShortDescription(serverLevel, globalPos.pos());
			case BlockPosTracker blockPosTracker -> getShortDescription(serverLevel, blockPosTracker.currentBlockPosition());
			case DamageSource damageSource -> {
				Entity entity2 = damageSource.getEntity();
				yield entity2 == null ? object.toString() : getShortDescription(serverLevel, entity2);
			}
			case Collection<?> collection -> "["
				+ (String)collection.stream().map(objectx -> getShortDescription(serverLevel, objectx)).collect(Collectors.joining(", "))
				+ "]";
			default -> object.toString();
		};
	}

	public boolean hasPoi(BlockPos blockPos) {
		return this.pois.contains(blockPos);
	}

	public boolean hasPotentialPoi(BlockPos blockPos) {
		return this.potentialPois.contains(blockPos);
	}
}
