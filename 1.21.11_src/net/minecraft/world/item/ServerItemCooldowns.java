package net.minecraft.world.item;

import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class ServerItemCooldowns extends ItemCooldowns {
	private final ServerPlayer player;

	public ServerItemCooldowns(ServerPlayer serverPlayer) {
		this.player = serverPlayer;
	}

	@Override
	protected void onCooldownStarted(Identifier identifier, int i) {
		super.onCooldownStarted(identifier, i);
		this.player.connection.send(new ClientboundCooldownPacket(identifier, i));
	}

	@Override
	protected void onCooldownEnded(Identifier identifier) {
		super.onCooldownEnded(identifier);
		this.player.connection.send(new ClientboundCooldownPacket(identifier, 0));
	}
}
