package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public class HoldingEntityRenderState extends LivingEntityRenderState {
	public final ItemStackRenderState heldItem = new ItemStackRenderState();

	public static void extractHoldingEntityRenderState(
		LivingEntity livingEntity, HoldingEntityRenderState holdingEntityRenderState, ItemModelResolver itemModelResolver
	) {
		itemModelResolver.updateForLiving(holdingEntityRenderState.heldItem, livingEntity.getMainHandItem(), ItemDisplayContext.GROUND, livingEntity);
	}
}
