package net.minecraft.client.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringUtil;

@Environment(EnvType.CLIENT)
public record CharacterEvent(int codepoint, @InputWithModifiers.Modifiers int modifiers) {
	public String codepointAsString() {
		return Character.toString(this.codepoint);
	}

	public boolean isAllowedChatCharacter() {
		return StringUtil.isAllowedChatCharacter(this.codepoint);
	}
}
