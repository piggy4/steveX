package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryTps implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer integratedServer = minecraft.getSingleplayerServer();
		ClientPacketListener clientPacketListener = minecraft.getConnection();
		if (clientPacketListener != null && level != null) {
			Connection connection = clientPacketListener.getConnection();
			float f = connection.getAverageSentPackets();
			float g = connection.getAverageReceivedPackets();
			TickRateManager tickRateManager = level.tickRateManager();
			String string;
			if (tickRateManager.isSteppingForward()) {
				string = " (frozen - stepping)";
			} else if (tickRateManager.isFrozen()) {
				string = " (frozen)";
			} else {
				string = "";
			}

			String string3;
			if (integratedServer != null) {
				ServerTickRateManager serverTickRateManager = integratedServer.tickRateManager();
				boolean bl = serverTickRateManager.isSprinting();
				if (bl) {
					string = " (sprinting)";
				}

				String string2 = bl ? "-" : String.format(Locale.ROOT, "%.1f", tickRateManager.millisecondsPerTick());
				string3 = String.format(
					Locale.ROOT, "Integrated server @ %.1f/%s ms%s, %.0f tx, %.0f rx", integratedServer.getCurrentSmoothedTickTime(), string2, string, f, g
				);
			} else {
				string3 = String.format(Locale.ROOT, "\"%s\" server%s, %.0f tx, %.0f rx", clientPacketListener.serverBrand(), string, f, g);
			}

			debugScreenDisplayer.addLine(string3);
		}
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
