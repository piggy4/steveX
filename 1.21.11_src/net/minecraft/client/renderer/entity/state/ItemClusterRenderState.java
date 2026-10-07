package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class ItemClusterRenderState extends EntityRenderState {
	public final ItemStackRenderState item = new ItemStackRenderState();
	public int count;
	public int seed;

	public void extractItemGroupRenderState(Entity entity, ItemStack itemStack, ItemModelResolver itemModelResolver) {
		itemModelResolver.updateForNonLiving(this.item, itemStack, ItemDisplayContext.GROUND, entity);
		this.count = getRenderedAmount(itemStack.getCount());
		this.seed = getSeedForItemStack(itemStack);
	}

	public static int getSeedForItemStack(ItemStack itemStack) {
		return itemStack.isEmpty() ? 187 : Item.getId(itemStack.getItem()) + itemStack.getDamageValue();
	}

	public static int getRenderedAmount(int i) {
		if (i <= 1) {
			return 1;
		} else if (i <= 16) {
			return 2;
		} else if (i <= 32) {
			return 3;
		} else {
			return i <= 48 ? 4 : 5;
		}
	}
}
