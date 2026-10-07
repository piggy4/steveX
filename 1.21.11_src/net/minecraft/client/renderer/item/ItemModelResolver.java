package net.minecraft.client.renderer.item;

import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ItemModelResolver {
	private final Function<Identifier, ItemModel> modelGetter;
	private final Function<Identifier, ClientItem.Properties> clientProperties;

	public ItemModelResolver(ModelManager modelManager) {
		this.modelGetter = modelManager::getItemModel;
		this.clientProperties = modelManager::getItemProperties;
	}

	public void updateForLiving(ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemDisplayContext itemDisplayContext, LivingEntity livingEntity) {
		this.updateForTopItem(
			itemStackRenderState, itemStack, itemDisplayContext, livingEntity.level(), livingEntity, livingEntity.getId() + itemDisplayContext.ordinal()
		);
	}

	public void updateForNonLiving(ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemDisplayContext itemDisplayContext, Entity entity) {
		this.updateForTopItem(itemStackRenderState, itemStack, itemDisplayContext, entity.level(), null, entity.getId());
	}

	public void updateForTopItem(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemDisplayContext itemDisplayContext,
		@Nullable Level level,
		@Nullable ItemOwner itemOwner,
		int i
	) {
		itemStackRenderState.clear();
		if (!itemStack.isEmpty()) {
			itemStackRenderState.displayContext = itemDisplayContext;
			this.appendItemLayers(itemStackRenderState, itemStack, itemDisplayContext, level, itemOwner, i);
		}
	}

	public void appendItemLayers(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemDisplayContext itemDisplayContext,
		@Nullable Level level,
		@Nullable ItemOwner itemOwner,
		int i
	) {
		Identifier identifier = itemStack.get(DataComponents.ITEM_MODEL);
		if (identifier != null) {
			itemStackRenderState.setOversizedInGui(this.clientProperties.apply(identifier).oversizedInGui());
			this.modelGetter
				.apply(identifier)
				.update(itemStackRenderState, itemStack, this, itemDisplayContext, level instanceof ClientLevel clientLevel ? clientLevel : null, itemOwner, i);
		}
	}

	public boolean shouldPlaySwapAnimation(ItemStack itemStack) {
		Identifier identifier = itemStack.get(DataComponents.ITEM_MODEL);
		return identifier == null ? true : this.clientProperties.apply(identifier).handAnimationOnSwap();
	}

	public float swapAnimationScale(ItemStack itemStack) {
		Identifier identifier = itemStack.get(DataComponents.ITEM_MODEL);
		return identifier == null ? 1.0F : this.clientProperties.apply(identifier).swapAnimationScale();
	}
}
