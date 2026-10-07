package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.level.BaseCommandBlock;

@Environment(EnvType.CLIENT)
public class MinecartCommandBlockEditScreen extends AbstractCommandBlockEditScreen {
	private final MinecartCommandBlock minecart;

	public MinecartCommandBlockEditScreen(MinecartCommandBlock minecartCommandBlock) {
		this.minecart = minecartCommandBlock;
	}

	@Override
	public BaseCommandBlock getCommandBlock() {
		return this.minecart.getCommandBlock();
	}

	@Override
	int getPreviousY() {
		return 150;
	}

	@Override
	protected void init() {
		super.init();
		this.commandEdit.setValue(this.getCommandBlock().getCommand());
	}

	@Override
	protected void populateAndSendPacket() {
		this.minecraft
			.getConnection()
			.send(new ServerboundSetCommandMinecartPacket(this.minecart.getId(), this.commandEdit.getValue(), this.minecart.getCommandBlock().isTrackOutput()));
	}
}
