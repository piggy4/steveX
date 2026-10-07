package net.minecraft.world.entity.projectile.throwableitemprojectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class ThrownLingeringPotion extends AbstractThrownPotion {
	public ThrownLingeringPotion(EntityType<? extends ThrownLingeringPotion> entityType, Level level) {
		super(entityType, level);
	}

	public ThrownLingeringPotion(Level level, LivingEntity livingEntity, ItemStack itemStack) {
		super(EntityType.LINGERING_POTION, level, livingEntity, itemStack);
	}

	public ThrownLingeringPotion(Level level, double d, double e, double f, ItemStack itemStack) {
		super(EntityType.LINGERING_POTION, level, d, e, f, itemStack);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.LINGERING_POTION;
	}

	@Override
	public void onHitAsPotion(ServerLevel serverLevel, ItemStack itemStack, HitResult hitResult) {
		AreaEffectCloud areaEffectCloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
		if (this.getOwner() instanceof LivingEntity livingEntity) {
			areaEffectCloud.setOwner(livingEntity);
		}

		areaEffectCloud.setRadius(3.0F);
		areaEffectCloud.setRadiusOnUse(-0.5F);
		areaEffectCloud.setDuration(600);
		areaEffectCloud.setWaitTime(10);
		areaEffectCloud.setRadiusPerTick(-areaEffectCloud.getRadius() / areaEffectCloud.getDuration());
		areaEffectCloud.applyComponentsFromItemStack(itemStack);
		serverLevel.addFreshEntity(areaEffectCloud);
	}
}
