package net.minecraft.client.multiplayer;

import com.mojang.authlib.GameProfile;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignedMessageValidator;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PlayerInfo {
	private final GameProfile profile;
	private @Nullable Supplier<PlayerSkin> skinLookup;
	private GameType gameMode = GameType.DEFAULT_MODE;
	private int latency;
	private @Nullable Component tabListDisplayName;
	private boolean showHat = true;
	private @Nullable RemoteChatSession chatSession;
	private SignedMessageValidator messageValidator;
	private int tabListOrder;

	public PlayerInfo(GameProfile gameProfile, boolean bl) {
		this.profile = gameProfile;
		this.messageValidator = fallbackMessageValidator(bl);
	}

	private static Supplier<PlayerSkin> createSkinLookup(GameProfile gameProfile) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean bl = !minecraft.isLocalPlayer(gameProfile.id());
		return minecraft.getSkinManager().createLookup(gameProfile, bl);
	}

	public GameProfile getProfile() {
		return this.profile;
	}

	public @Nullable RemoteChatSession getChatSession() {
		return this.chatSession;
	}

	public SignedMessageValidator getMessageValidator() {
		return this.messageValidator;
	}

	public boolean hasVerifiableChat() {
		return this.chatSession != null;
	}

	protected void setChatSession(RemoteChatSession remoteChatSession) {
		this.chatSession = remoteChatSession;
		this.messageValidator = remoteChatSession.createMessageValidator(ProfilePublicKey.EXPIRY_GRACE_PERIOD);
	}

	protected void clearChatSession(boolean bl) {
		this.chatSession = null;
		this.messageValidator = fallbackMessageValidator(bl);
	}

	private static SignedMessageValidator fallbackMessageValidator(boolean bl) {
		return bl ? SignedMessageValidator.REJECT_ALL : SignedMessageValidator.ACCEPT_UNSIGNED;
	}

	public GameType getGameMode() {
		return this.gameMode;
	}

	protected void setGameMode(GameType gameType) {
		this.gameMode = gameType;
	}

	public int getLatency() {
		return this.latency;
	}

	protected void setLatency(int i) {
		this.latency = i;
	}

	public PlayerSkin getSkin() {
		if (this.skinLookup == null) {
			this.skinLookup = createSkinLookup(this.profile);
		}

		return this.skinLookup.get();
	}

	public @Nullable PlayerTeam getTeam() {
		return Minecraft.getInstance().level.getScoreboard().getPlayersTeam(this.getProfile().name());
	}

	public void setTabListDisplayName(@Nullable Component component) {
		this.tabListDisplayName = component;
	}

	public @Nullable Component getTabListDisplayName() {
		return this.tabListDisplayName;
	}

	public void setShowHat(boolean bl) {
		this.showHat = bl;
	}

	public boolean showHat() {
		return this.showHat;
	}

	public void setTabListOrder(int i) {
		this.tabListOrder = i;
	}

	public int getTabListOrder() {
		return this.tabListOrder;
	}
}
