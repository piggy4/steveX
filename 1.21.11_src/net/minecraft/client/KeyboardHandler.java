package net.minecraft.client;

import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.ClipboardManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.debug.DebugOptionsScreen;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.commands.VersionCommand;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class KeyboardHandler {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int DEBUG_CRASH_TIME = 10000;
	private final Minecraft minecraft;
	private final ClipboardManager clipboardManager = new ClipboardManager();
	private long debugCrashKeyTime = -1L;
	private long debugCrashKeyReportedTime = -1L;
	private long debugCrashKeyReportedCount = -1L;
	private boolean usedDebugKeyAsModifier;

	public KeyboardHandler(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	private boolean handleChunkDebugKeys(KeyEvent keyEvent) {
		switch (keyEvent.key()) {
			case 69:
				if (this.minecraft.player == null) {
					return false;
				}

				boolean bl = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_SECTION_PATHS);
				this.debugFeedback("SectionPath: " + (bl ? "shown" : "hidden"));
				return true;
			case 70:
				boolean bl3 = FogRenderer.toggleFog();
				this.debugFeedbackEnabledStatus("Fog: ", bl3);
				return true;
			case 71:
			case 72:
			case 73:
			case 74:
			case 75:
			case 77:
			case 78:
			case 80:
			case 81:
			case 82:
			case 83:
			case 84:
			default:
				return false;
			case 76:
				this.minecraft.smartCull = !this.minecraft.smartCull;
				this.debugFeedbackEnabledStatus("SmartCull: ", this.minecraft.smartCull);
				return true;
			case 79:
				if (this.minecraft.player == null) {
					return false;
				}

				boolean bl2 = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_SECTION_OCTREE);
				this.debugFeedbackEnabledStatus("Frustum culling Octree: ", bl2);
				return true;
			case 85:
				if (keyEvent.hasShiftDown()) {
					this.minecraft.levelRenderer.killFrustum();
					this.debugFeedback("Killed frustum");
				} else {
					this.minecraft.levelRenderer.captureFrustum();
					this.debugFeedback("Captured frustum");
				}

				return true;
			case 86:
				if (this.minecraft.player == null) {
					return false;
				}

				boolean bl4 = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_SECTION_VISIBILITY);
				this.debugFeedbackEnabledStatus("SectionVisibility: ", bl4);
				return true;
			case 87:
				this.minecraft.wireframe = !this.minecraft.wireframe;
				this.debugFeedbackEnabledStatus("WireFrame: ", this.minecraft.wireframe);
				return true;
		}
	}

	private void debugFeedbackEnabledStatus(String string, boolean bl) {
		this.debugFeedback(string + (bl ? "enabled" : "disabled"));
	}

	private void showDebugChat(Component component) {
		this.minecraft.gui.getChat().addMessage(component);
		this.minecraft.getNarrator().saySystemQueued(component);
	}

	private static Component decorateDebugComponent(ChatFormatting chatFormatting, Component component) {
		return Component.empty()
			.append(Component.translatable("debug.prefix").withStyle(chatFormatting, ChatFormatting.BOLD))
			.append(CommonComponents.SPACE)
			.append(component);
	}

	private void debugWarningComponent(Component component) {
		this.showDebugChat(decorateDebugComponent(ChatFormatting.RED, component));
	}

	private void debugFeedbackComponent(Component component) {
		this.showDebugChat(decorateDebugComponent(ChatFormatting.YELLOW, component));
	}

	private void debugFeedbackTranslated(String string, Object... objects) {
		this.debugFeedbackComponent(Component.translatable(string, objects));
	}

	private void debugFeedback(String string) {
		this.debugFeedbackComponent(Component.literal(string));
	}

	private boolean handleDebugKeys(KeyEvent keyEvent) {
		if (this.debugCrashKeyTime > 0L && this.debugCrashKeyTime < Util.getMillis() - 100L) {
			return true;
		}

		if (SharedConstants.DEBUG_HOTKEYS && this.handleChunkDebugKeys(keyEvent)) {
			return true;
		}

		if (SharedConstants.DEBUG_FEATURE_COUNT) {
			switch (keyEvent.key()) {
				case 76:
					FeatureCountTracker.logCounts();
					return true;
				case 82:
					FeatureCountTracker.clearCounts();
					return true;
			}
		}

		Options options = this.minecraft.options;
		boolean bl = false;
		if (options.keyDebugReloadChunk.matches(keyEvent)) {
			this.minecraft.levelRenderer.allChanged();
			this.debugFeedbackTranslated("debug.reload_chunks.message");
			bl = true;
		}

		if (options.keyDebugShowHitboxes.matches(keyEvent) && this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
			boolean bl2 = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
			this.debugFeedbackTranslated(bl2 ? "debug.show_hitboxes.on" : "debug.show_hitboxes.off");
			bl = true;
		}

		if (options.keyDebugClearChat.matches(keyEvent)) {
			this.minecraft.gui.getChat().clearMessages(false);
			bl = true;
		}

		if (options.keyDebugShowChunkBorders.matches(keyEvent) && this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
			boolean bl2 = this.minecraft.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_BORDERS);
			this.debugFeedbackTranslated(bl2 ? "debug.chunk_boundaries.on" : "debug.chunk_boundaries.off");
			bl = true;
		}

		if (options.keyDebugShowAdvancedTooltips.matches(keyEvent)) {
			options.advancedItemTooltips = !options.advancedItemTooltips;
			this.debugFeedbackTranslated(options.advancedItemTooltips ? "debug.advanced_tooltips.on" : "debug.advanced_tooltips.off");
			options.save();
			bl = true;
		}

		if (options.keyDebugCopyRecreateCommand.matches(keyEvent)) {
			if (this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
				this.copyRecreateCommand(this.minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), !keyEvent.hasShiftDown());
			}

			bl = true;
		}

		if (options.keyDebugSpectate.matches(keyEvent)) {
			if (this.minecraft.player == null || !GameModeCommand.PERMISSION_CHECK.check(this.minecraft.player.permissions())) {
				this.debugFeedbackTranslated("debug.creative_spectator.error");
			} else if (!this.minecraft.player.isSpectator()) {
				this.minecraft.player.connection.send(new ServerboundChangeGameModePacket(GameType.SPECTATOR));
			} else {
				GameType gameType = MoreObjects.firstNonNull(this.minecraft.gameMode.getPreviousPlayerMode(), GameType.CREATIVE);
				this.minecraft.player.connection.send(new ServerboundChangeGameModePacket(gameType));
			}

			bl = true;
		}

		if (options.keyDebugSwitchGameMode.matches(keyEvent) && this.minecraft.level != null && this.minecraft.screen == null) {
			if (this.minecraft.canSwitchGameMode() && GameModeCommand.PERMISSION_CHECK.check(this.minecraft.player.permissions())) {
				this.minecraft.setScreen(new GameModeSwitcherScreen());
			} else {
				this.debugFeedbackTranslated("debug.gamemodes.error");
			}

			bl = true;
		}

		if (options.keyDebugDebugOptions.matches(keyEvent)) {
			if (this.minecraft.screen instanceof DebugOptionsScreen) {
				this.minecraft.screen.onClose();
			} else if (this.minecraft.canInterruptScreen()) {
				if (this.minecraft.screen != null) {
					this.minecraft.screen.onClose();
				}

				this.minecraft.setScreen(new DebugOptionsScreen());
			}

			bl = true;
		}

		if (options.keyDebugFocusPause.matches(keyEvent)) {
			options.pauseOnLostFocus = !options.pauseOnLostFocus;
			options.save();
			this.debugFeedbackTranslated(options.pauseOnLostFocus ? "debug.pause_focus.on" : "debug.pause_focus.off");
			bl = true;
		}

		if (options.keyDebugDumpDynamicTextures.matches(keyEvent)) {
			Path path = this.minecraft.gameDirectory.toPath().toAbsolutePath();
			Path path2 = TextureUtil.getDebugTexturePath(path);
			this.minecraft.getTextureManager().dumpAllSheets(path2);
			Component component = Component.literal(path.relativize(path2).toString())
				.withStyle(ChatFormatting.UNDERLINE)
				.withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(path2)));
			this.debugFeedbackComponent(Component.translatable("debug.dump_dynamic_textures", component));
			bl = true;
		}

		if (options.keyDebugReloadResourcePacks.matches(keyEvent)) {
			this.debugFeedbackTranslated("debug.reload_resourcepacks.message");
			this.minecraft.reloadResourcePacks();
			bl = true;
		}

		if (options.keyDebugProfiling.matches(keyEvent)) {
			if (this.minecraft.debugClientMetricsStart(this::debugFeedbackComponent)) {
				this.debugFeedbackComponent(
					Component.translatable(
						"debug.profiling.start", 10, options.keyDebugModifier.getTranslatedKeyMessage(), options.keyDebugProfiling.getTranslatedKeyMessage()
					)
				);
			}

			bl = true;
		}

		if (options.keyDebugCopyLocation.matches(keyEvent) && this.minecraft.player != null && !this.minecraft.player.isReducedDebugInfo()) {
			this.debugFeedbackTranslated("debug.copy_location.message");
			this.setClipboard(
				String.format(
					Locale.ROOT,
					"/execute in %s run tp @s %.2f %.2f %.2f %.2f %.2f",
					this.minecraft.player.level().dimension().identifier(),
					this.minecraft.player.getX(),
					this.minecraft.player.getY(),
					this.minecraft.player.getZ(),
					this.minecraft.player.getYRot(),
					this.minecraft.player.getXRot()
				)
			);
			bl = true;
		}

		if (options.keyDebugDumpVersion.matches(keyEvent)) {
			this.debugFeedbackTranslated("debug.version.header");
			VersionCommand.dumpVersion(this::showDebugChat);
			bl = true;
		}

		if (options.keyDebugPofilingChart.matches(keyEvent)) {
			this.minecraft.getDebugOverlay().toggleProfilerChart();
			bl = true;
		}

		if (options.keyDebugFpsCharts.matches(keyEvent)) {
			this.minecraft.getDebugOverlay().toggleFpsCharts();
			bl = true;
		}

		if (options.keyDebugNetworkCharts.matches(keyEvent)) {
			this.minecraft.getDebugOverlay().toggleNetworkCharts();
			bl = true;
		}

		return bl;
	}

	private void copyRecreateCommand(boolean bl, boolean bl2) {
		HitResult hitResult = this.minecraft.hitResult;
		if (hitResult != null) {
			switch (hitResult.getType()) {
				case BLOCK:
					BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
					Level level = this.minecraft.player.level();
					BlockState blockState = level.getBlockState(blockPos);
					if (bl) {
						if (bl2) {
							this.minecraft.player.connection.getDebugQueryHandler().queryBlockEntityTag(blockPos, compoundTagx -> {
								this.copyCreateBlockCommand(blockState, blockPos, compoundTagx);
								this.debugFeedbackTranslated("debug.inspect.server.block");
							});
						} else {
							BlockEntity blockEntity = level.getBlockEntity(blockPos);
							CompoundTag compoundTag = blockEntity != null ? blockEntity.saveWithoutMetadata(level.registryAccess()) : null;
							this.copyCreateBlockCommand(blockState, blockPos, compoundTag);
							this.debugFeedbackTranslated("debug.inspect.client.block");
						}
					} else {
						this.copyCreateBlockCommand(blockState, blockPos, null);
						this.debugFeedbackTranslated("debug.inspect.client.block");
					}
					break;
				case ENTITY:
					Entity entity = ((EntityHitResult)hitResult).getEntity();
					Identifier identifier = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
					if (bl) {
						if (bl2) {
							this.minecraft.player.connection.getDebugQueryHandler().queryEntityTag(entity.getId(), compoundTagx -> {
								this.copyCreateEntityCommand(identifier, entity.position(), compoundTagx);
								this.debugFeedbackTranslated("debug.inspect.server.entity");
							});
						} else {
							try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
								TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
								entity.saveWithoutId(tagValueOutput);
								this.copyCreateEntityCommand(identifier, entity.position(), tagValueOutput.buildResult());
							}

							this.debugFeedbackTranslated("debug.inspect.client.entity");
						}
					} else {
						this.copyCreateEntityCommand(identifier, entity.position(), null);
						this.debugFeedbackTranslated("debug.inspect.client.entity");
					}
			}
		}
	}

	private void copyCreateBlockCommand(BlockState blockState, BlockPos blockPos, @Nullable CompoundTag compoundTag) {
		StringBuilder stringBuilder = new StringBuilder(BlockStateParser.serialize(blockState));
		if (compoundTag != null) {
			stringBuilder.append(compoundTag);
		}

		String string = String.format(Locale.ROOT, "/setblock %d %d %d %s", blockPos.getX(), blockPos.getY(), blockPos.getZ(), stringBuilder);
		this.setClipboard(string);
	}

	private void copyCreateEntityCommand(Identifier identifier, Vec3 vec3, @Nullable CompoundTag compoundTag) {
		String string2;
		if (compoundTag != null) {
			compoundTag.remove("UUID");
			compoundTag.remove("Pos");
			String string = NbtUtils.toPrettyComponent(compoundTag).getString();
			string2 = String.format(Locale.ROOT, "/summon %s %.2f %.2f %.2f %s", identifier, vec3.x, vec3.y, vec3.z, string);
		} else {
			string2 = String.format(Locale.ROOT, "/summon %s %.2f %.2f %.2f", identifier, vec3.x, vec3.y, vec3.z);
		}

		this.setClipboard(string2);
	}

	private void keyPress(long l, @KeyEvent.Action int i, KeyEvent keyEvent) {
		Window window = this.minecraft.getWindow();
		if (l == window.handle()) {
			this.minecraft.getFramerateLimitTracker().onInputReceived();
			Options options = this.minecraft.options;
			boolean bl = options.keyDebugModifier.key.getValue() == options.keyDebugOverlay.key.getValue();
			boolean bl2 = options.keyDebugModifier.isDown();
			boolean bl3 = !options.keyDebugCrash.isUnbound() && InputConstants.isKeyDown(this.minecraft.getWindow(), options.keyDebugCrash.key.getValue());
			if (this.debugCrashKeyTime > 0L) {
				if (!bl3 || !bl2) {
					this.debugCrashKeyTime = -1L;
				}
			} else if (bl3 && bl2) {
				this.usedDebugKeyAsModifier = bl;
				this.debugCrashKeyTime = Util.getMillis();
				this.debugCrashKeyReportedTime = Util.getMillis();
				this.debugCrashKeyReportedCount = 0L;
			}

			Screen screen = this.minecraft.screen;
			if (screen != null) {
				switch (keyEvent.key()) {
					case 258:
						this.minecraft.setLastInputType(InputType.KEYBOARD_TAB);
					case 259:
					case 260:
					case 261:
					default:
						break;
					case 262:
					case 263:
					case 264:
					case 265:
						this.minecraft.setLastInputType(InputType.KEYBOARD_ARROW);
				}
			}

			if (i == 1 && (!(this.minecraft.screen instanceof KeyBindsScreen) || ((KeyBindsScreen)screen).lastKeySelection <= Util.getMillis() - 20L)) {
				if (options.keyFullscreen.matches(keyEvent)) {
					window.toggleFullScreen();
					boolean bl4 = window.isFullscreen();
					options.fullscreen().set(bl4);
					options.save();
					if (this.minecraft.screen instanceof VideoSettingsScreen videoSettingsScreen) {
						videoSettingsScreen.updateFullscreenButton(bl4);
					}

					return;
				}

				if (options.keyScreenshot.matches(keyEvent)) {
					if (keyEvent.hasControlDownWithQuirk() && SharedConstants.DEBUG_PANORAMA_SCREENSHOT) {
						this.showDebugChat(this.minecraft.grabPanoramixScreenshot(this.minecraft.gameDirectory));
					} else {
						Screenshot.grab(
							this.minecraft.gameDirectory, this.minecraft.getMainRenderTarget(), component -> this.minecraft.execute(() -> this.showDebugChat(component))
						);
					}

					return;
				}
			}

			if (i != 0) {
				boolean bl4 = screen == null || !(screen.getFocused() instanceof EditBox) || !((EditBox)screen.getFocused()).canConsumeInput();
				if (bl4) {
					if (keyEvent.hasControlDownWithQuirk() && keyEvent.key() == 66 && this.minecraft.getNarrator().isActive() && options.narratorHotkey().get()) {
						boolean bl5 = options.narrator().get() == NarratorStatus.OFF;
						options.narrator().set(NarratorStatus.byId(options.narrator().get().getId() + 1));
						options.save();
						if (screen != null) {
							screen.updateNarratorStatus(bl5);
						}
					}

					LocalPlayer var21 = this.minecraft.player;
				}
			}

			if (screen != null) {
				try {
					if (i != 1 && i != 2) {
						if (i == 0 && screen.keyReleased(keyEvent)) {
							if (options.keyDebugModifier.matches(keyEvent)) {
								this.usedDebugKeyAsModifier = false;
							}

							return;
						}
					} else {
						screen.afterKeyboardAction();
						if (screen.keyPressed(keyEvent)) {
							if (this.minecraft.screen == null) {
								InputConstants.Key key = InputConstants.getKey(keyEvent);
								KeyMapping.set(key, false);
							}

							return;
						}
					}
				} catch (Throwable throwable) {
					CrashReport crashReport = CrashReport.forThrowable(throwable, "keyPressed event handler");
					screen.fillCrashDetails(crashReport);
					CrashReportCategory crashReportCategory = crashReport.addCategory("Key");
					crashReportCategory.setDetail("Key", keyEvent.key());
					crashReportCategory.setDetail("Scancode", keyEvent.scancode());
					crashReportCategory.setDetail("Mods", keyEvent.modifiers());
					throw new ReportedException(crashReport);
				}
			}

			InputConstants.Key key = InputConstants.getKey(keyEvent);
			boolean bl5 = this.minecraft.screen == null;
			boolean bl6 = bl5
				|| this.minecraft.screen instanceof PauseScreen pauseScreen && !pauseScreen.showsPauseMenu()
				|| this.minecraft.screen instanceof GameModeSwitcherScreen;
			if (bl && options.keyDebugModifier.matches(keyEvent) && i == 0) {
				if (this.usedDebugKeyAsModifier) {
					this.usedDebugKeyAsModifier = false;
				} else {
					this.minecraft.debugEntries.toggleDebugOverlay();
				}
			} else if (!bl && options.keyDebugOverlay.matches(keyEvent) && i == 1) {
				this.minecraft.debugEntries.toggleDebugOverlay();
			}

			if (i == 0) {
				KeyMapping.set(key, false);
			} else {
				boolean bl7 = false;
				if (bl6 && keyEvent.isEscape()) {
					this.minecraft.pauseGame(bl2);
					bl7 = bl2;
				} else if (bl2) {
					bl7 = this.handleDebugKeys(keyEvent);
					if (bl7 && screen instanceof DebugOptionsScreen debugOptionsScreen) {
						DebugOptionsScreen.OptionList optionList = debugOptionsScreen.getOptionList();
						if (optionList != null) {
							optionList.children().forEach(DebugOptionsScreen.AbstractOptionEntry::refreshEntry);
						}
					}
				} else if (bl6 && options.keyToggleGui.matches(keyEvent)) {
					options.hideGui = !options.hideGui;
				} else if (bl6 && options.keyToggleSpectatorShaderEffects.matches(keyEvent)) {
					this.minecraft.gameRenderer.togglePostEffect();
				}

				if (bl) {
					this.usedDebugKeyAsModifier |= bl7;
				}

				if (this.minecraft.getDebugOverlay().showProfilerChart() && !bl2) {
					int j = keyEvent.getDigit();
					if (j != -1) {
						this.minecraft.getDebugOverlay().getProfilerPieChart().profilerPieChartKeyPress(j);
					}
				}

				if (bl5 || key == options.keyDebugModifier.key) {
					if (bl7) {
						KeyMapping.set(key, false);
					} else {
						KeyMapping.set(key, true);
						KeyMapping.click(key);
					}
				}
			}
		}
	}

	private void charTyped(long l, CharacterEvent characterEvent) {
		if (l == this.minecraft.getWindow().handle()) {
			Screen screen = this.minecraft.screen;
			if (screen != null && this.minecraft.getOverlay() == null) {
				try {
					screen.charTyped(characterEvent);
				} catch (Throwable throwable) {
					CrashReport crashReport = CrashReport.forThrowable(throwable, "charTyped event handler");
					screen.fillCrashDetails(crashReport);
					CrashReportCategory crashReportCategory = crashReport.addCategory("Key");
					crashReportCategory.setDetail("Codepoint", characterEvent.codepoint());
					crashReportCategory.setDetail("Mods", characterEvent.modifiers());
					throw new ReportedException(crashReport);
				}
			}
		}
	}

	public void setup(Window window) {
		InputConstants.setupKeyboardCallbacks(window, (l, i, j, k, m) -> {
			KeyEvent keyEvent = new KeyEvent(i, j, m);
			this.minecraft.execute(() -> this.keyPress(l, k, keyEvent));
		}, (l, i, j) -> {
			CharacterEvent characterEvent = new CharacterEvent(i, j);
			this.minecraft.execute(() -> this.charTyped(l, characterEvent));
		});
	}

	public String getClipboard() {
		return this.clipboardManager.getClipboard(this.minecraft.getWindow(), (i, l) -> {
			if (i != 65545) {
				this.minecraft.getWindow().defaultErrorCallback(i, l);
			}
		});
	}

	public void setClipboard(String string) {
		if (!string.isEmpty()) {
			this.clipboardManager.setClipboard(this.minecraft.getWindow(), string);
		}
	}

	public void tick() {
		if (this.debugCrashKeyTime > 0L) {
			long l = Util.getMillis();
			long m = 10000L - (l - this.debugCrashKeyTime);
			long n = l - this.debugCrashKeyReportedTime;
			if (m < 0L) {
				if (this.minecraft.hasControlDown()) {
					Blaze3D.youJustLostTheGame();
				}

				String string = "Manually triggered debug crash";
				CrashReport crashReport = new CrashReport("Manually triggered debug crash", new Throwable("Manually triggered debug crash"));
				CrashReportCategory crashReportCategory = crashReport.addCategory("Manual crash details");
				NativeModuleLister.addCrashSection(crashReportCategory);
				throw new ReportedException(crashReport);
			}

			if (n >= 1000L) {
				if (this.debugCrashKeyReportedCount == 0L) {
					this.debugFeedbackTranslated(
						"debug.crash.message",
						this.minecraft.options.keyDebugModifier.getTranslatedKeyMessage().getString(),
						this.minecraft.options.keyDebugCrash.getTranslatedKeyMessage().getString()
					);
				} else {
					this.debugWarningComponent(Component.translatable("debug.crash.warning", Mth.ceil((float)m / 1000.0F)));
				}

				this.debugCrashKeyReportedTime = l;
				this.debugCrashKeyReportedCount++;
			}
		}
	}
}
