package net.minecraft.client;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record GuiMessage(int addedTime, Component content, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag) {
	private static final int MESSAGE_TAG_MARGIN_LEFT = 4;

	public List<FormattedCharSequence> splitLines(Font font, int i) {
		if (this.tag != null && this.tag.icon() != null) {
			i -= this.tag.icon().width + 4 + 2;
		}

		return ComponentRenderUtils.wrapComponents(this.content, i, font);
	}

	@Environment(EnvType.CLIENT)
	public record Line(int addedTime, FormattedCharSequence content, @Nullable GuiMessageTag tag, boolean endOfEntry) {
		public int getTagIconLeft(Font font) {
			return font.width(this.content) + 4;
		}
	}
}
