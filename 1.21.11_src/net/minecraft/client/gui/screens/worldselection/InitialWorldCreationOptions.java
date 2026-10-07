package net.minecraft.client.gui.screens.worldselection;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record InitialWorldCreationOptions(
	WorldCreationUiState.SelectedGameMode selectedGameMode, GameRuleMap gameRuleOverwrites, @Nullable ResourceKey<FlatLevelGeneratorPreset> flatLevelPreset
) {
}
