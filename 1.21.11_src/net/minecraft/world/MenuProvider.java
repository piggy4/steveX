package net.minecraft.world;

import net.fabricmc.fabric.api.screenhandler.v1.FabricScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuConstructor;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.screenhandler.v1.FabricScreenHandlerFactory} injected by mod fabric-screen-handler-api-v1</p>
 */
public interface MenuProvider extends MenuConstructor, FabricScreenHandlerFactory {
	Component getDisplayName();
}
