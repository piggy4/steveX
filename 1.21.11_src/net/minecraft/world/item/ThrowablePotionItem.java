package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.level.Level;

public abstract class ThrowablePotionItem extends PotionItem implements ProjectileItem {
	public static float PROJECTILE_SHOOT_POWER = 0.5F;

	public ThrowablePotionItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(this::createPotion, serverLevel, itemStack, player, -20.0F, PROJECTILE_SHOOT_POWER, 1.0F);
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		itemStack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	protected abstract AbstractThrownPotion createPotion(ServerLevel serverLevel, LivingEntity livingEntity, ItemStack itemStack);

	protected abstract AbstractThrownPotion createPotion(Level level, Position position, ItemStack itemStack);

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
		return this.createPotion(level, position, itemStack);
	}

	@Override
	public ProjectileItem.DispenseConfig createDispenseConfig() {
		return ProjectileItem.DispenseConfig.builder()
			.uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
			.power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
			.build();
	}
}
