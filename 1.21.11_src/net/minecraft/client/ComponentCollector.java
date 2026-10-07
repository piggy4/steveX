package net.minecraft.client;

import com.google.common.collect.Lists;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.FormattedText;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ComponentCollector {
	private final List<FormattedText> parts = Lists.newArrayList();

	public void append(FormattedText formattedText) {
		this.parts.add(formattedText);
	}

	public @Nullable FormattedText getResult() {
		if (this.parts.isEmpty()) {
			return null;
		} else {
			return this.parts.size() == 1 ? this.parts.get(0) : FormattedText.composite(this.parts);
		}
	}

	public FormattedText getResultOrEmpty() {
		FormattedText formattedText = this.getResult();
		return formattedText != null ? formattedText : FormattedText.EMPTY;
	}

	public void reset() {
		this.parts.clear();
	}
}
