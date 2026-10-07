package net.minecraft.client.gui.screens.worldselection;

import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface CreateWorldCallback {
	boolean create(
		CreateWorldScreen createWorldScreen, LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess, PrimaryLevelData primaryLevelData, @Nullable Path path
	);
}
