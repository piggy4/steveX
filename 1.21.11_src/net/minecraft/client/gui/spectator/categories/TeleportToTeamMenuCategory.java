package net.minecraft.client.gui.spectator.categories;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import net.minecraft.client.gui.spectator.SpectatorMenuCategory;
import net.minecraft.client.gui.spectator.SpectatorMenuItem;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

@Environment(EnvType.CLIENT)
public class TeleportToTeamMenuCategory implements SpectatorMenuCategory, SpectatorMenuItem {
	private static final Identifier TELEPORT_TO_TEAM_SPRITE = Identifier.withDefaultNamespace("spectator/teleport_to_team");
	private static final Component TELEPORT_TEXT = Component.translatable("spectatorMenu.team_teleport");
	private static final Component TELEPORT_PROMPT = Component.translatable("spectatorMenu.team_teleport.prompt");
	private final List<SpectatorMenuItem> items;

	public TeleportToTeamMenuCategory() {
		Minecraft minecraft = Minecraft.getInstance();
		this.items = createTeamEntries(minecraft, minecraft.level.getScoreboard());
	}

	private static List<SpectatorMenuItem> createTeamEntries(Minecraft minecraft, Scoreboard scoreboard) {
		return scoreboard.getPlayerTeams()
			.stream()
			.flatMap(playerTeam -> TeleportToTeamMenuCategory.TeamSelectionItem.create(minecraft, playerTeam).stream())
			.toList();
	}

	@Override
	public List<SpectatorMenuItem> getItems() {
		return this.items;
	}

	@Override
	public Component getPrompt() {
		return TELEPORT_PROMPT;
	}

	@Override
	public void selectItem(SpectatorMenu spectatorMenu) {
		spectatorMenu.selectCategory(this);
	}

	@Override
	public Component getName() {
		return TELEPORT_TEXT;
	}

	@Override
	public void renderIcon(GuiGraphics guiGraphics, float f, float g) {
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TELEPORT_TO_TEAM_SPRITE, 0, 0, 16, 16, ARGB.colorFromFloat(g, f, f, f));
	}

	@Override
	public boolean isEnabled() {
		return !this.items.isEmpty();
	}

	@Environment(EnvType.CLIENT)
	static class TeamSelectionItem implements SpectatorMenuItem {
		private final PlayerTeam team;
		private final Supplier<PlayerSkin> iconSkin;
		private final List<PlayerInfo> players;

		private TeamSelectionItem(PlayerTeam playerTeam, List<PlayerInfo> list, Supplier<PlayerSkin> supplier) {
			this.team = playerTeam;
			this.players = list;
			this.iconSkin = supplier;
		}

		public static Optional<SpectatorMenuItem> create(Minecraft minecraft, PlayerTeam playerTeam) {
			List<PlayerInfo> list = new ArrayList<>();

			for (String string : playerTeam.getPlayers()) {
				PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(string);
				if (playerInfo != null && playerInfo.getGameMode() != GameType.SPECTATOR) {
					list.add(playerInfo);
				}
			}

			if (list.isEmpty()) {
				return Optional.empty();
			}

			PlayerInfo playerInfo2 = list.get(RandomSource.create().nextInt(list.size()));
			return Optional.of(new TeleportToTeamMenuCategory.TeamSelectionItem(playerTeam, list, playerInfo2::getSkin));
		}

		@Override
		public void selectItem(SpectatorMenu spectatorMenu) {
			spectatorMenu.selectCategory(new TeleportToPlayerMenuCategory(this.players));
		}

		@Override
		public Component getName() {
			return this.team.getDisplayName();
		}

		@Override
		public void renderIcon(GuiGraphics guiGraphics, float f, float g) {
			Integer integer = this.team.getColor().getColor();
			if (integer != null) {
				float h = (integer >> 16 & 0xFF) / 255.0F;
				float i = (integer >> 8 & 0xFF) / 255.0F;
				float j = (integer & 0xFF) / 255.0F;
				guiGraphics.fill(1, 1, 15, 15, ARGB.colorFromFloat(g, h * f, i * f, j * f));
			}

			PlayerFaceRenderer.draw(guiGraphics, this.iconSkin.get(), 2, 2, 12, ARGB.colorFromFloat(g, f, f, f));
		}

		@Override
		public boolean isEnabled() {
			return true;
		}
	}
}
