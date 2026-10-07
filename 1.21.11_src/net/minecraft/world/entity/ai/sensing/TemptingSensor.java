package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class TemptingSensor extends Sensor<PathfinderMob> {
	private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
	private final BiPredicate<PathfinderMob, ItemStack> temptations;

	public TemptingSensor(Predicate<ItemStack> predicate) {
		this((pathfinderMob, itemStack) -> predicate.test(itemStack));
	}

	public static TemptingSensor forAnimal() {
		return new TemptingSensor((pathfinderMob, itemStack) -> pathfinderMob instanceof Animal animal ? animal.isFood(itemStack) : false);
	}

	private TemptingSensor(BiPredicate<PathfinderMob, ItemStack> biPredicate) {
		this.temptations = biPredicate;
	}

	protected void doTick(ServerLevel serverLevel, PathfinderMob pathfinderMob) {
		Brain<?> brain = pathfinderMob.getBrain();
		TargetingConditions targetingConditions = TEMPT_TARGETING.copy().range((float)pathfinderMob.getAttributeValue(Attributes.TEMPT_RANGE));
		List<Player> list = serverLevel.players()
			.stream()
			.filter(EntitySelector.NO_SPECTATORS)
			.filter(serverPlayer -> targetingConditions.test(serverLevel, pathfinderMob, serverPlayer))
			.filter(serverPlayer -> this.playerHoldingTemptation(pathfinderMob, serverPlayer))
			.filter(serverPlayer -> !pathfinderMob.hasPassenger(serverPlayer))
			.sorted(Comparator.comparingDouble(pathfinderMob::distanceToSqr))
			.collect(Collectors.toList());
		if (!list.isEmpty()) {
			Player player = list.get(0);
			brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, player);
		} else {
			brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
		}
	}

	private boolean playerHoldingTemptation(PathfinderMob pathfinderMob, Player player) {
		return this.isTemptation(pathfinderMob, player.getMainHandItem()) || this.isTemptation(pathfinderMob, player.getOffhandItem());
	}

	private boolean isTemptation(PathfinderMob pathfinderMob, ItemStack itemStack) {
		return this.temptations.test(pathfinderMob, itemStack);
	}

	@Override
	public Set<MemoryModuleType<?>> requires() {
		return ImmutableSet.of(MemoryModuleType.TEMPTING_PLAYER);
	}
}
