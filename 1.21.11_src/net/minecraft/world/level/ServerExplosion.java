package net.minecraft.world.level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ServerExplosion implements Explosion {
	private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
	private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
	private static final float LARGE_EXPLOSION_RADIUS = 2.0F;
	private final boolean fire;
	private final Explosion.BlockInteraction blockInteraction;
	private final ServerLevel level;
	private final Vec3 center;
	private final @Nullable Entity source;
	private final float radius;
	private final DamageSource damageSource;
	private final ExplosionDamageCalculator damageCalculator;
	private final Map<Player, Vec3> hitPlayers = new HashMap<>();

	public ServerExplosion(
		ServerLevel serverLevel,
		@Nullable Entity entity,
		@Nullable DamageSource damageSource,
		@Nullable ExplosionDamageCalculator explosionDamageCalculator,
		Vec3 vec3,
		float f,
		boolean bl,
		Explosion.BlockInteraction blockInteraction
	) {
		this.level = serverLevel;
		this.source = entity;
		this.radius = f;
		this.center = vec3;
		this.fire = bl;
		this.blockInteraction = blockInteraction;
		this.damageSource = damageSource == null ? serverLevel.damageSources().explosion(this) : damageSource;
		this.damageCalculator = explosionDamageCalculator == null ? this.makeDamageCalculator(entity) : explosionDamageCalculator;
	}

	private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
		return entity == null ? EXPLOSION_DAMAGE_CALCULATOR : new EntityBasedExplosionDamageCalculator(entity);
	}

	public static float getSeenPercent(Vec3 vec3, Entity entity) {
		AABB aABB = entity.getBoundingBox();
		double d = 1.0 / ((aABB.maxX - aABB.minX) * 2.0 + 1.0);
		double e = 1.0 / ((aABB.maxY - aABB.minY) * 2.0 + 1.0);
		double f = 1.0 / ((aABB.maxZ - aABB.minZ) * 2.0 + 1.0);
		double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
		double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;
		if (!(d < 0.0) && !(e < 0.0) && !(f < 0.0)) {
			int i = 0;
			int j = 0;

			for (double k = 0.0; k <= 1.0; k += d) {
				for (double l = 0.0; l <= 1.0; l += e) {
					for (double m = 0.0; m <= 1.0; m += f) {
						double n = Mth.lerp(k, aABB.minX, aABB.maxX);
						double o = Mth.lerp(l, aABB.minY, aABB.maxY);
						double p = Mth.lerp(m, aABB.minZ, aABB.maxZ);
						Vec3 vec32 = new Vec3(n + g, o, p + h);
						if (entity.level().clip(new ClipContext(vec32, vec3, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) {
							i++;
						}

						j++;
					}
				}
			}

			return (float)i / j;
		} else {
			return 0.0F;
		}
	}

	@Override
	public float radius() {
		return this.radius;
	}

	@Override
	public Vec3 center() {
		return this.center;
	}

	private List<BlockPos> calculateExplodedPositions() {
		Set<BlockPos> set = new HashSet<>();
		int i = 16;

		for (int j = 0; j < 16; j++) {
			for (int k = 0; k < 16; k++) {
				for (int l = 0; l < 16; l++) {
					if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
						double d = j / 15.0F * 2.0F - 1.0F;
						double e = k / 15.0F * 2.0F - 1.0F;
						double f = l / 15.0F * 2.0F - 1.0F;
						double g = Math.sqrt(d * d + e * e + f * f);
						d /= g;
						e /= g;
						f /= g;
						float h = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
						double m = this.center.x;
						double n = this.center.y;
						double o = this.center.z;
						float p = 0.3F;

						while (h > 0.0F) {
							BlockPos blockPos = BlockPos.containing(m, n, o);
							BlockState blockState = this.level.getBlockState(blockPos);
							FluidState fluidState = this.level.getFluidState(blockPos);
							if (!this.level.isInWorldBounds(blockPos)) {
								break;
							}

							Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, blockPos, blockState, fluidState);
							if (optional.isPresent()) {
								h -= (optional.get() + 0.3F) * 0.3F;
							}

							if (h > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, blockPos, blockState, h)) {
								set.add(blockPos);
							}

							m += d * 0.3F;
							n += e * 0.3F;
							o += f * 0.3F;
							h -= 0.22500001F;
						}
					}
				}
			}
		}

		return new ObjectArrayList<>(set);
	}

	private void hurtEntities() {
		if (!(this.radius < 1.0E-5F)) {
			float f = this.radius * 2.0F;
			int i = Mth.floor(this.center.x - f - 1.0);
			int j = Mth.floor(this.center.x + f + 1.0);
			int k = Mth.floor(this.center.y - f - 1.0);
			int l = Mth.floor(this.center.y + f + 1.0);
			int m = Mth.floor(this.center.z - f - 1.0);
			int n = Mth.floor(this.center.z + f + 1.0);

			for (Entity entity : this.level.getEntities(this.source, new AABB(i, k, m, j, l, n))) {
				if (!entity.ignoreExplosion(this)) {
					double d = Math.sqrt(entity.distanceToSqr(this.center)) / f;
					if (!(d > 1.0)) {
						Vec3 vec3 = entity instanceof PrimedTnt ? entity.position() : entity.getEyePosition();
						Vec3 vec32 = vec3.subtract(this.center).normalize();
						boolean bl = this.damageCalculator.shouldDamageEntity(this, entity);
						float g = this.damageCalculator.getKnockbackMultiplier(entity);
						float h = !bl && g == 0.0F ? 0.0F : getSeenPercent(this.center, entity);
						if (bl) {
							entity.hurtServer(this.level, this.damageSource, this.damageCalculator.getEntityDamageAmount(this, entity, h));
						}

						double e = entity instanceof LivingEntity livingEntity ? livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE) : 0.0;
						double o = (1.0 - d) * h * g * (1.0 - e);
						Vec3 vec33 = vec32.scale(o);
						entity.push(vec33);
						if (entity.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
							projectile.setOwner(this.damageSource.getEntity());
						} else if (entity instanceof Player player && !player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
							this.hitPlayers.put(player, vec33);
						}

						entity.onExplosionHit(this.source);
					}
				}
			}
		}
	}

	private void interactWithBlocks(List<BlockPos> list) {
		List<ServerExplosion.StackCollector> list2 = new ArrayList<>();
		Util.shuffle(list, this.level.random);

		for (BlockPos blockPos : list) {
			this.level.getBlockState(blockPos).onExplosionHit(this.level, blockPos, this, (itemStack, blockPosx) -> addOrAppendStack(list2, itemStack, blockPosx));
		}

		for (ServerExplosion.StackCollector stackCollector : list2) {
			Block.popResource(this.level, stackCollector.pos, stackCollector.stack);
		}
	}

	private void createFire(List<BlockPos> list) {
		for (BlockPos blockPos : list) {
			if (this.level.random.nextInt(3) == 0 && this.level.getBlockState(blockPos).isAir() && this.level.getBlockState(blockPos.below()).isSolidRender()) {
				this.level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level, blockPos));
			}
		}
	}

	public int explode() {
		this.level.gameEvent(this.source, GameEvent.EXPLODE, this.center);
		List<BlockPos> list = this.calculateExplodedPositions();
		this.hurtEntities();
		if (this.interactsWithBlocks()) {
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("explosion_blocks");
			this.interactWithBlocks(list);
			profilerFiller.pop();
		}

		if (this.fire) {
			this.createFire(list);
		}

		return list.size();
	}

	private static void addOrAppendStack(List<ServerExplosion.StackCollector> list, ItemStack itemStack, BlockPos blockPos) {
		for (ServerExplosion.StackCollector stackCollector : list) {
			stackCollector.tryMerge(itemStack);
			if (itemStack.isEmpty()) {
				return;
			}
		}

		list.add(new ServerExplosion.StackCollector(blockPos, itemStack));
	}

	private boolean interactsWithBlocks() {
		return this.blockInteraction != Explosion.BlockInteraction.KEEP;
	}

	public Map<Player, Vec3> getHitPlayers() {
		return this.hitPlayers;
	}

	@Override
	public ServerLevel level() {
		return this.level;
	}

	@Override
	public @Nullable LivingEntity getIndirectSourceEntity() {
		return Explosion.getIndirectSourceEntity(this.source);
	}

	@Override
	public @Nullable Entity getDirectSourceEntity() {
		return this.source;
	}

	public DamageSource getDamageSource() {
		return this.damageSource;
	}

	@Override
	public Explosion.BlockInteraction getBlockInteraction() {
		return this.blockInteraction;
	}

	@Override
	public boolean canTriggerBlocks() {
		if (this.blockInteraction != Explosion.BlockInteraction.TRIGGER_BLOCK) {
			return false;
		} else {
			return this.source != null && this.source.getType() == EntityType.BREEZE_WIND_CHARGE ? this.level.getGameRules().get(GameRules.MOB_GRIEFING) : true;
		}
	}

	@Override
	public boolean shouldAffectBlocklikeEntities() {
		boolean bl = this.level.getGameRules().get(GameRules.MOB_GRIEFING);
		boolean bl2 = this.source == null || this.source.getType() != EntityType.BREEZE_WIND_CHARGE && this.source.getType() != EntityType.WIND_CHARGE;
		return bl ? bl2 : this.blockInteraction.shouldAffectBlocklikeEntities() && bl2;
	}

	public boolean isSmall() {
		return this.radius < 2.0F || !this.interactsWithBlocks();
	}

	static class StackCollector {
		final BlockPos pos;
		ItemStack stack;

		StackCollector(BlockPos blockPos, ItemStack itemStack) {
			this.pos = blockPos;
			this.stack = itemStack;
		}

		public void tryMerge(ItemStack itemStack) {
			if (ItemEntity.areMergable(this.stack, itemStack)) {
				this.stack = ItemEntity.merge(this.stack, itemStack, 16);
			}
		}
	}
}
