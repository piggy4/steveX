package net.minecraft.client.data.models;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.world.item.Item;

@Environment(EnvType.CLIENT)
public interface ItemModelOutput {
	default void accept(Item item, ItemModel.Unbaked unbaked) {
		this.accept(item, unbaked, ClientItem.Properties.DEFAULT);
	}

	void accept(Item item, ItemModel.Unbaked unbaked, ClientItem.Properties properties);

	void copy(Item item, Item item2);
}
