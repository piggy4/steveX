package net.minecraft.server.bossevents;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

public class CustomBossEvent extends ServerBossEvent {
	private static final int DEFAULT_MAX = 100;
	private final Identifier id;
	private final Set<UUID> players = Sets.newHashSet();
	private int value;
	private int max = 100;

	public CustomBossEvent(Identifier identifier, Component component) {
		super(component, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
		this.id = identifier;
		this.setProgress(0.0F);
	}

	public Identifier getTextId() {
		return this.id;
	}

	@Override
	public void addPlayer(ServerPlayer serverPlayer) {
		super.addPlayer(serverPlayer);
		this.players.add(serverPlayer.getUUID());
	}

	public void addOfflinePlayer(UUID uUID) {
		this.players.add(uUID);
	}

	@Override
	public void removePlayer(ServerPlayer serverPlayer) {
		super.removePlayer(serverPlayer);
		this.players.remove(serverPlayer.getUUID());
	}

	@Override
	public void removeAllPlayers() {
		super.removeAllPlayers();
		this.players.clear();
	}

	public int getValue() {
		return this.value;
	}

	public int getMax() {
		return this.max;
	}

	public void setValue(int i) {
		this.value = i;
		this.setProgress(Mth.clamp((float)i / this.max, 0.0F, 1.0F));
	}

	public void setMax(int i) {
		this.max = i;
		this.setProgress(Mth.clamp((float)this.value / i, 0.0F, 1.0F));
	}

	public final Component getDisplayName() {
		return ComponentUtils.wrapInSquareBrackets(this.getName())
			.withStyle(
				style -> style.withColor(this.getColor().getFormatting())
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(this.getTextId().toString())))
					.withInsertion(this.getTextId().toString())
			);
	}

	public boolean setPlayers(Collection<ServerPlayer> collection) {
		Set<UUID> set = Sets.newHashSet();
		Set<ServerPlayer> set2 = Sets.newHashSet();

		for (UUID uUID : this.players) {
			boolean bl = false;

			for (ServerPlayer serverPlayer : collection) {
				if (serverPlayer.getUUID().equals(uUID)) {
					bl = true;
					break;
				}
			}

			if (!bl) {
				set.add(uUID);
			}
		}

		for (ServerPlayer serverPlayer2 : collection) {
			boolean bl = false;

			for (UUID uUID2 : this.players) {
				if (serverPlayer2.getUUID().equals(uUID2)) {
					bl = true;
					break;
				}
			}

			if (!bl) {
				set2.add(serverPlayer2);
			}
		}

		for (UUID uUID : set) {
			for (ServerPlayer serverPlayer3 : this.getPlayers()) {
				if (serverPlayer3.getUUID().equals(uUID)) {
					this.removePlayer(serverPlayer3);
					break;
				}
			}

			this.players.remove(uUID);
		}

		for (ServerPlayer serverPlayer2 : set2) {
			this.addPlayer(serverPlayer2);
		}

		return !set.isEmpty() || !set2.isEmpty();
	}

	public static CustomBossEvent load(Identifier identifier, CustomBossEvent.Packed packed) {
		CustomBossEvent customBossEvent = new CustomBossEvent(identifier, packed.name);
		customBossEvent.setVisible(packed.visible);
		customBossEvent.setValue(packed.value);
		customBossEvent.setMax(packed.max);
		customBossEvent.setColor(packed.color);
		customBossEvent.setOverlay(packed.overlay);
		customBossEvent.setDarkenScreen(packed.darkenScreen);
		customBossEvent.setPlayBossMusic(packed.playBossMusic);
		customBossEvent.setCreateWorldFog(packed.createWorldFog);
		packed.players.forEach(customBossEvent::addOfflinePlayer);
		return customBossEvent;
	}

	public CustomBossEvent.Packed pack() {
		return new CustomBossEvent.Packed(
			this.getName(),
			this.isVisible(),
			this.getValue(),
			this.getMax(),
			this.getColor(),
			this.getOverlay(),
			this.shouldDarkenScreen(),
			this.shouldPlayBossMusic(),
			this.shouldCreateWorldFog(),
			Set.copyOf(this.players)
		);
	}

	public void onPlayerConnect(ServerPlayer serverPlayer) {
		if (this.players.contains(serverPlayer.getUUID())) {
			this.addPlayer(serverPlayer);
		}
	}

	public void onPlayerDisconnect(ServerPlayer serverPlayer) {
		super.removePlayer(serverPlayer);
	}

	public record Packed(
		Component name,
		boolean visible,
		int value,
		int max,
		BossEvent.BossBarColor color,
		BossEvent.BossBarOverlay overlay,
		boolean darkenScreen,
		boolean playBossMusic,
		boolean createWorldFog,
		Set<UUID> players
	) {
		public static final Codec<CustomBossEvent.Packed> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ComponentSerialization.CODEC.fieldOf("Name").forGetter(CustomBossEvent.Packed::name),
					Codec.BOOL.optionalFieldOf("Visible", false).forGetter(CustomBossEvent.Packed::visible),
					Codec.INT.optionalFieldOf("Value", 0).forGetter(CustomBossEvent.Packed::value),
					Codec.INT.optionalFieldOf("Max", 100).forGetter(CustomBossEvent.Packed::max),
					BossEvent.BossBarColor.CODEC.optionalFieldOf("Color", BossEvent.BossBarColor.WHITE).forGetter(CustomBossEvent.Packed::color),
					BossEvent.BossBarOverlay.CODEC.optionalFieldOf("Overlay", BossEvent.BossBarOverlay.PROGRESS).forGetter(CustomBossEvent.Packed::overlay),
					Codec.BOOL.optionalFieldOf("DarkenScreen", false).forGetter(CustomBossEvent.Packed::darkenScreen),
					Codec.BOOL.optionalFieldOf("PlayBossMusic", false).forGetter(CustomBossEvent.Packed::playBossMusic),
					Codec.BOOL.optionalFieldOf("CreateWorldFog", false).forGetter(CustomBossEvent.Packed::createWorldFog),
					UUIDUtil.CODEC_SET.optionalFieldOf("Players", Set.of()).forGetter(CustomBossEvent.Packed::players)
				)
				.apply(instance, CustomBossEvent.Packed::new)
		);
	}
}
