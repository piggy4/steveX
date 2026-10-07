package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

@Environment(EnvType.CLIENT)
public abstract class AbstractHorseRenderer<T extends AbstractHorse, S extends EquineRenderState, M extends EntityModel<? super S>>
	extends AgeableMobRenderer<T, S, M> {
	public AbstractHorseRenderer(EntityRendererProvider.Context context, M entityModel, M entityModel2) {
		super(context, entityModel, entityModel2, 0.75F);
	}

	public void extractRenderState(T abstractHorse, S equineRenderState, float f) {
		super.extractRenderState(abstractHorse, equineRenderState, f);
		equineRenderState.saddle = abstractHorse.getItemBySlot(EquipmentSlot.SADDLE).copy();
		equineRenderState.bodyArmorItem = abstractHorse.getBodyArmorItem().copy();
		equineRenderState.isRidden = abstractHorse.isVehicle();
		equineRenderState.eatAnimation = abstractHorse.getEatAnim(f);
		equineRenderState.standAnimation = abstractHorse.getStandAnim(f);
		equineRenderState.feedingAnimation = abstractHorse.getMouthAnim(f);
		equineRenderState.animateTail = abstractHorse.tailCounter > 0;
	}
}
