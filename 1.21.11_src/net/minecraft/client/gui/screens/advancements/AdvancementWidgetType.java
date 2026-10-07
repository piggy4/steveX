package net.minecraft.client.gui.screens.advancements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public enum AdvancementWidgetType {
	OBTAINED(
		Identifier.withDefaultNamespace("advancements/box_obtained"),
		Identifier.withDefaultNamespace("advancements/task_frame_obtained"),
		Identifier.withDefaultNamespace("advancements/challenge_frame_obtained"),
		Identifier.withDefaultNamespace("advancements/goal_frame_obtained")
	),
	UNOBTAINED(
		Identifier.withDefaultNamespace("advancements/box_unobtained"),
		Identifier.withDefaultNamespace("advancements/task_frame_unobtained"),
		Identifier.withDefaultNamespace("advancements/challenge_frame_unobtained"),
		Identifier.withDefaultNamespace("advancements/goal_frame_unobtained")
	);

	private final Identifier boxSprite;
	private final Identifier taskFrameSprite;
	private final Identifier challengeFrameSprite;
	private final Identifier goalFrameSprite;

	AdvancementWidgetType(final Identifier identifier, final Identifier identifier2, final Identifier identifier3, final Identifier identifier4) {
		this.boxSprite = identifier;
		this.taskFrameSprite = identifier2;
		this.challengeFrameSprite = identifier3;
		this.goalFrameSprite = identifier4;
	}

	public Identifier boxSprite() {
		return this.boxSprite;
	}

	public Identifier frameSprite(AdvancementType advancementType) {
		return switch (advancementType) {
			case TASK -> this.taskFrameSprite;
			case CHALLENGE -> this.challengeFrameSprite;
			case GOAL -> this.goalFrameSprite;
		};
	}
}
