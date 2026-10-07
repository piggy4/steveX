package net.minecraft.client.gui.screens.inventory;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TestInstanceBlockEditScreen extends Screen {
	private static final Component ID_LABEL = Component.translatable("test_instance_block.test_id");
	private static final Component SIZE_LABEL = Component.translatable("test_instance_block.size");
	private static final Component INCLUDE_ENTITIES_LABEL = Component.translatable("test_instance_block.entities");
	private static final Component ROTATION_LABEL = Component.translatable("test_instance_block.rotation");
	private static final int BUTTON_PADDING = 8;
	private static final int WIDTH = 316;
	private final TestInstanceBlockEntity blockEntity;
	private @Nullable EditBox idEdit;
	private @Nullable EditBox sizeXEdit;
	private @Nullable EditBox sizeYEdit;
	private @Nullable EditBox sizeZEdit;
	private @Nullable FittingMultiLineTextWidget infoWidget;
	private @Nullable Button saveButton;
	private @Nullable Button exportButton;
	private @Nullable CycleButton<Boolean> includeEntitiesButton;
	private @Nullable CycleButton<Rotation> rotationButton;

	public TestInstanceBlockEditScreen(TestInstanceBlockEntity testInstanceBlockEntity) {
		super(testInstanceBlockEntity.getBlockState().getBlock().getName());
		this.blockEntity = testInstanceBlockEntity;
	}

	@Override
	protected void init() {
		int i = this.width / 2 - 158;
		boolean bl = SharedConstants.IS_RUNNING_IN_IDE;
		int j = bl ? 3 : 2;
		int k = widgetSize(j);
		this.idEdit = new EditBox(this.font, i, 40, 316, 20, Component.translatable("test_instance_block.test_id"));
		this.idEdit.setMaxLength(128);
		Optional<ResourceKey<GameTestInstance>> optional = this.blockEntity.test();
		if (optional.isPresent()) {
			this.idEdit.setValue(optional.get().identifier().toString());
		}

		this.idEdit.setResponder(string -> this.updateTestInfo(false));
		this.addRenderableWidget(this.idEdit);
		this.infoWidget = new FittingMultiLineTextWidget(i, 70, 316, 8 * 9, Component.literal(""), this.font);
		this.addRenderableWidget(this.infoWidget);
		Vec3i vec3i = this.blockEntity.getSize();
		int l = 0;
		this.sizeXEdit = new EditBox(this.font, this.widgetX(l++, 5), 160, widgetSize(5), 20, Component.translatable("structure_block.size.x"));
		this.sizeXEdit.setMaxLength(15);
		this.addRenderableWidget(this.sizeXEdit);
		this.sizeYEdit = new EditBox(this.font, this.widgetX(l++, 5), 160, widgetSize(5), 20, Component.translatable("structure_block.size.y"));
		this.sizeYEdit.setMaxLength(15);
		this.addRenderableWidget(this.sizeYEdit);
		this.sizeZEdit = new EditBox(this.font, this.widgetX(l++, 5), 160, widgetSize(5), 20, Component.translatable("structure_block.size.z"));
		this.sizeZEdit.setMaxLength(15);
		this.addRenderableWidget(this.sizeZEdit);
		this.setSize(vec3i);
		this.rotationButton = this.addRenderableWidget(
			CycleButton.builder(TestInstanceBlockEditScreen::rotationDisplay, this.blockEntity.getRotation())
				.withValues(Rotation.values())
				.displayOnlyValue()
				.create(this.widgetX(l++, 5), 160, widgetSize(5), 20, ROTATION_LABEL, (cycleButton, rotation) -> this.updateSaveState())
		);
		this.includeEntitiesButton = this.addRenderableWidget(
			CycleButton.onOffBuilder(!this.blockEntity.ignoreEntities()).displayOnlyValue().create(this.widgetX(l++, 5), 160, widgetSize(5), 20, INCLUDE_ENTITIES_LABEL)
		);
		l = 0;
		this.addRenderableWidget(Button.builder(Component.translatable("test_instance.action.reset"), button -> {
			this.sendToServer(ServerboundTestInstanceBlockActionPacket.Action.RESET);
			this.minecraft.setScreen(null);
		}).bounds(this.widgetX(l++, j), 185, k, 20).build());
		this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("test_instance.action.save"), button -> {
			this.sendToServer(ServerboundTestInstanceBlockActionPacket.Action.SAVE);
			this.minecraft.setScreen(null);
		}).bounds(this.widgetX(l++, j), 185, k, 20).build());
		if (bl) {
			this.exportButton = this.addRenderableWidget(Button.builder(Component.literal("Export Structure"), button -> {
				this.sendToServer(ServerboundTestInstanceBlockActionPacket.Action.EXPORT);
				this.minecraft.setScreen(null);
			}).bounds(this.widgetX(l++, j), 185, k, 20).build());
		}

		this.addRenderableWidget(Button.builder(Component.translatable("test_instance.action.run"), button -> {
			this.sendToServer(ServerboundTestInstanceBlockActionPacket.Action.RUN);
			this.minecraft.setScreen(null);
		}).bounds(this.widgetX(0, 3), 210, widgetSize(3), 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onDone()).bounds(this.widgetX(1, 3), 210, widgetSize(3), 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onCancel()).bounds(this.widgetX(2, 3), 210, widgetSize(3), 20).build());
		this.updateTestInfo(true);
	}

	private void updateSaveState() {
		boolean bl = this.rotationButton.getValue() == Rotation.NONE && Identifier.tryParse(this.idEdit.getValue()) != null;
		this.saveButton.active = bl;
		if (this.exportButton != null) {
			this.exportButton.active = bl;
		}
	}

	private static Component rotationDisplay(Rotation rotation) {
		return Component.literal(switch (rotation) {
			case NONE -> "0";
			case CLOCKWISE_90 -> "90";
			case CLOCKWISE_180 -> "180";
			case COUNTERCLOCKWISE_90 -> "270";
		});
	}

	private void setSize(Vec3i vec3i) {
		this.sizeXEdit.setValue(Integer.toString(vec3i.getX()));
		this.sizeYEdit.setValue(Integer.toString(vec3i.getY()));
		this.sizeZEdit.setValue(Integer.toString(vec3i.getZ()));
	}

	private int widgetX(int i, int j) {
		int k = this.width / 2 - 158;
		float f = exactWidgetSize(j);
		return (int)(k + i * (8.0F + f));
	}

	private static int widgetSize(int i) {
		return (int)exactWidgetSize(i);
	}

	private static float exactWidgetSize(int i) {
		return (float)(316 - (i - 1) * 8) / i;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		int k = this.width / 2 - 158;
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, -1);
		guiGraphics.drawString(this.font, ID_LABEL, k, 30, -6250336);
		guiGraphics.drawString(this.font, SIZE_LABEL, k, 150, -6250336);
		guiGraphics.drawString(this.font, ROTATION_LABEL, this.rotationButton.getX(), 150, -6250336);
		guiGraphics.drawString(this.font, INCLUDE_ENTITIES_LABEL, this.includeEntitiesButton.getX(), 150, -6250336);
	}

	private void updateTestInfo(boolean bl) {
		boolean bl2 = this.sendToServer(bl ? ServerboundTestInstanceBlockActionPacket.Action.INIT : ServerboundTestInstanceBlockActionPacket.Action.QUERY);
		if (!bl2) {
			this.infoWidget.setMessage(Component.translatable("test_instance.description.invalid_id").withStyle(ChatFormatting.RED));
		}

		this.updateSaveState();
	}

	private void onDone() {
		this.sendToServer(ServerboundTestInstanceBlockActionPacket.Action.SET);
		this.onClose();
	}

	private boolean sendToServer(ServerboundTestInstanceBlockActionPacket.Action action) {
		Optional<Identifier> optional = Optional.ofNullable(Identifier.tryParse(this.idEdit.getValue()));
		Optional<ResourceKey<GameTestInstance>> optional2 = optional.map(identifier -> ResourceKey.create(Registries.TEST_INSTANCE, identifier));
		Vec3i vec3i = new Vec3i(parseSize(this.sizeXEdit.getValue()), parseSize(this.sizeYEdit.getValue()), parseSize(this.sizeZEdit.getValue()));
		boolean bl = !this.includeEntitiesButton.getValue();
		this.minecraft
			.getConnection()
			.send(new ServerboundTestInstanceBlockActionPacket(this.blockEntity.getBlockPos(), action, optional2, vec3i, this.rotationButton.getValue(), bl));
		return optional.isPresent();
	}

	public void setStatus(Component component, Optional<Vec3i> optional) {
		MutableComponent mutableComponent = Component.empty();
		this.blockEntity
			.errorMessage()
			.ifPresent(
				componentx -> mutableComponent.append(
						Component.translatable("test_instance.description.failed", Component.empty().withStyle(ChatFormatting.RED).append(componentx))
					)
					.append("\n\n")
			);
		mutableComponent.append(component);
		this.infoWidget.setMessage(mutableComponent);
		optional.ifPresent(this::setSize);
	}

	private void onCancel() {
		this.onClose();
	}

	private static int parseSize(String string) {
		try {
			return Mth.clamp(Integer.parseInt(string), 1, 48);
		} catch (NumberFormatException numberFormatException) {
			return 1;
		}
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
