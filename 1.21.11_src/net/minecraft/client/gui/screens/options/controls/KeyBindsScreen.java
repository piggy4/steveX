package net.minecraft.client.gui.screens.options.controls;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class KeyBindsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("controls.keybinds.title");
	public @Nullable KeyMapping selectedKey;
	public long lastKeySelection;
	private KeyBindsList keyBindsList;
	private Button resetButton;

	public KeyBindsScreen(Screen screen, Options options) {
		super(screen, options, TITLE);
	}

	@Override
	protected void addContents() {
		this.keyBindsList = this.layout.addToContents(new KeyBindsList(this, this.minecraft));
	}

	@Override
	protected void addOptions() {
	}

	@Override
	protected void addFooter() {
		this.resetButton = Button.builder(Component.translatable("controls.resetAll"), button -> {
			for (KeyMapping keyMapping : this.options.keyMappings) {
				keyMapping.setKey(keyMapping.getDefaultKey());
			}

			this.keyBindsList.resetMappingAndUpdateButtons();
		}).build();
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		linearLayout.addChild(this.resetButton);
		linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		this.keyBindsList.updateSize(this.width, this.layout);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (this.selectedKey != null) {
			this.selectedKey.setKey(InputConstants.Type.MOUSE.getOrCreate(mouseButtonEvent.button()));
			this.selectedKey = null;
			this.keyBindsList.resetMappingAndUpdateButtons();
			return true;
		} else {
			return super.mouseClicked(mouseButtonEvent, bl);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.selectedKey != null) {
			if (keyEvent.isEscape()) {
				this.selectedKey.setKey(InputConstants.UNKNOWN);
			} else {
				this.selectedKey.setKey(InputConstants.getKey(keyEvent));
			}

			this.selectedKey = null;
			this.lastKeySelection = Util.getMillis();
			this.keyBindsList.resetMappingAndUpdateButtons();
			return true;
		} else {
			return super.keyPressed(keyEvent);
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		boolean bl = false;

		for (KeyMapping keyMapping : this.options.keyMappings) {
			if (!keyMapping.isDefault()) {
				bl = true;
				break;
			}
		}

		this.resetButton.active = bl;
	}
}
