package net.minecraft.world;

import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public abstract class BossEvent {
	private final UUID id;
	protected Component name;
	protected float progress;
	protected BossEvent.BossBarColor color;
	protected BossEvent.BossBarOverlay overlay;
	protected boolean darkenScreen;
	protected boolean playBossMusic;
	protected boolean createWorldFog;

	public BossEvent(UUID uUID, Component component, BossEvent.BossBarColor bossBarColor, BossEvent.BossBarOverlay bossBarOverlay) {
		this.id = uUID;
		this.name = component;
		this.color = bossBarColor;
		this.overlay = bossBarOverlay;
		this.progress = 1.0F;
	}

	public UUID getId() {
		return this.id;
	}

	public Component getName() {
		return this.name;
	}

	public void setName(Component component) {
		this.name = component;
	}

	public float getProgress() {
		return this.progress;
	}

	public void setProgress(float f) {
		this.progress = f;
	}

	public BossEvent.BossBarColor getColor() {
		return this.color;
	}

	public void setColor(BossEvent.BossBarColor bossBarColor) {
		this.color = bossBarColor;
	}

	public BossEvent.BossBarOverlay getOverlay() {
		return this.overlay;
	}

	public void setOverlay(BossEvent.BossBarOverlay bossBarOverlay) {
		this.overlay = bossBarOverlay;
	}

	public boolean shouldDarkenScreen() {
		return this.darkenScreen;
	}

	public BossEvent setDarkenScreen(boolean bl) {
		this.darkenScreen = bl;
		return this;
	}

	public boolean shouldPlayBossMusic() {
		return this.playBossMusic;
	}

	public BossEvent setPlayBossMusic(boolean bl) {
		this.playBossMusic = bl;
		return this;
	}

	public BossEvent setCreateWorldFog(boolean bl) {
		this.createWorldFog = bl;
		return this;
	}

	public boolean shouldCreateWorldFog() {
		return this.createWorldFog;
	}

	public enum BossBarColor implements StringRepresentable {
		PINK("pink", ChatFormatting.RED),
		BLUE("blue", ChatFormatting.BLUE),
		RED("red", ChatFormatting.DARK_RED),
		GREEN("green", ChatFormatting.GREEN),
		YELLOW("yellow", ChatFormatting.YELLOW),
		PURPLE("purple", ChatFormatting.DARK_BLUE),
		WHITE("white", ChatFormatting.WHITE);

		public static final Codec<BossEvent.BossBarColor> CODEC = StringRepresentable.fromEnum(BossEvent.BossBarColor::values);
		private final String name;
		private final ChatFormatting formatting;

		BossBarColor(final String string2, final ChatFormatting chatFormatting) {
			this.name = string2;
			this.formatting = chatFormatting;
		}

		public ChatFormatting getFormatting() {
			return this.formatting;
		}

		public String getName() {
			return this.name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public enum BossBarOverlay implements StringRepresentable {
		PROGRESS("progress"),
		NOTCHED_6("notched_6"),
		NOTCHED_10("notched_10"),
		NOTCHED_12("notched_12"),
		NOTCHED_20("notched_20");

		public static final Codec<BossEvent.BossBarOverlay> CODEC = StringRepresentable.fromEnum(BossEvent.BossBarOverlay::values);
		private final String name;

		BossBarOverlay(final String string2) {
			this.name = string2;
		}

		public String getName() {
			return this.name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
