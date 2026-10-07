package net.minecraft.client.gui.screens.worldselection;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.function.ToIntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class OptimizeWorldScreen extends Screen {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ToIntFunction<ResourceKey<Level>> DIMENSION_COLORS = Util.make(new Reference2IntOpenHashMap<>(), reference2IntOpenHashMap -> {
		reference2IntOpenHashMap.put(Level.OVERWORLD, -13408734);
		reference2IntOpenHashMap.put(Level.NETHER, -10075085);
		reference2IntOpenHashMap.put(Level.END, -8943531);
		reference2IntOpenHashMap.defaultReturnValue(-2236963);
	});
	private final BooleanConsumer callback;
	private final WorldUpgrader upgrader;

	public static @Nullable OptimizeWorldScreen create(
		Minecraft minecraft, BooleanConsumer booleanConsumer, DataFixer dataFixer, LevelStorageSource.LevelStorageAccess levelStorageAccess, boolean bl
	) {
		try {
			WorldOpenFlows worldOpenFlows = minecraft.createWorldOpenFlows();
			PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorageAccess);

			try (WorldStem worldStem = worldOpenFlows.loadWorldStem(levelStorageAccess.getDataTag(), false, packRepository)) {
				WorldData worldData = worldStem.worldData();
				RegistryAccess.Frozen frozen = worldStem.registries().compositeAccess();
				levelStorageAccess.saveDataTag(frozen, worldData);
				return new OptimizeWorldScreen(booleanConsumer, dataFixer, levelStorageAccess, worldData, bl, frozen);
			}
		} catch (Exception exception) {
			LOGGER.warn("Failed to load datapacks, can't optimize world", exception);
			return null;
		}
	}

	private OptimizeWorldScreen(
		BooleanConsumer booleanConsumer,
		DataFixer dataFixer,
		LevelStorageSource.LevelStorageAccess levelStorageAccess,
		WorldData worldData,
		boolean bl,
		RegistryAccess registryAccess
	) {
		super(Component.translatable("optimizeWorld.title", worldData.getLevelSettings().levelName()));
		this.callback = booleanConsumer;
		this.upgrader = new WorldUpgrader(levelStorageAccess, dataFixer, worldData, registryAccess, bl, false);
	}

	@Override
	protected void init() {
		super.init();
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> {
			this.upgrader.cancel();
			this.callback.accept(false);
		}).bounds(this.width / 2 - 100, this.height / 4 + 150, 200, 20).build());
	}

	@Override
	public void tick() {
		if (this.upgrader.isFinished()) {
			this.callback.accept(true);
		}
	}

	@Override
	public void onClose() {
		this.callback.accept(false);
	}

	@Override
	public void removed() {
		this.upgrader.cancel();
		this.upgrader.close();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, -1);
		int k = this.width / 2 - 150;
		int l = this.width / 2 + 150;
		int m = this.height / 4 + 100;
		int n = m + 10;
		guiGraphics.drawCenteredString(this.font, this.upgrader.getStatus(), this.width / 2, m - 9 - 2, -6250336);
		if (this.upgrader.getTotalChunks() > 0) {
			guiGraphics.fill(k - 1, m - 1, l + 1, n + 1, -16777216);
			guiGraphics.drawString(this.font, Component.translatable("optimizeWorld.info.converted", this.upgrader.getConverted()), k, 40, -6250336);
			guiGraphics.drawString(this.font, Component.translatable("optimizeWorld.info.skipped", this.upgrader.getSkipped()), k, 40 + 9 + 3, -6250336);
			guiGraphics.drawString(this.font, Component.translatable("optimizeWorld.info.total", this.upgrader.getTotalChunks()), k, 40 + (9 + 3) * 2, -6250336);
			int o = 0;

			for (ResourceKey<Level> resourceKey : this.upgrader.levels()) {
				int p = Mth.floor(this.upgrader.dimensionProgress(resourceKey) * (l - k));
				guiGraphics.fill(k + o, m, k + o + p, n, DIMENSION_COLORS.applyAsInt(resourceKey));
				o += p;
			}

			int q = this.upgrader.getConverted() + this.upgrader.getSkipped();
			Component component = Component.translatable("optimizeWorld.progress.counter", q, this.upgrader.getTotalChunks());
			Component component2 = Component.translatable("optimizeWorld.progress.percentage", Mth.floor(this.upgrader.getProgress() * 100.0F));
			guiGraphics.drawCenteredString(this.font, component, this.width / 2, m + 2 * 9 + 2, -6250336);
			guiGraphics.drawCenteredString(this.font, component2, this.width / 2, m + (n - m) / 2 - 9 / 2, -6250336);
		}
	}
}
