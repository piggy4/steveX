package net.minecraft.world.item;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class HangingEntityItem extends Item {
	private static final Component TOOLTIP_RANDOM_VARIANT = Component.translatable("painting.random").withStyle(ChatFormatting.GRAY);
	private final EntityType<? extends HangingEntity> type;

	public HangingEntityItem(EntityType<? extends HangingEntity> entityType, Item.Properties properties) {
		super(properties);
		this.type = entityType;
	}

	@Override
	public InteractionResult useOn(UseOnContext useOnContext) {
		BlockPos blockPos = useOnContext.getClickedPos();
		Direction direction = useOnContext.getClickedFace();
		BlockPos blockPos2 = blockPos.relative(direction);
		Player player = useOnContext.getPlayer();
		ItemStack itemStack = useOnContext.getItemInHand();
		if (player != null && !this.mayPlace(player, direction, itemStack, blockPos2)) {
			return InteractionResult.FAIL;
		}

		Level level = useOnContext.getLevel();
		HangingEntity hangingEntity;
		if (this.type == EntityType.PAINTING) {
			Optional<Painting> optional = Painting.create(level, blockPos2, direction);
			if (optional.isEmpty()) {
				return InteractionResult.CONSUME;
			}

			hangingEntity = optional.get();
		} else if (this.type == EntityType.ITEM_FRAME) {
			hangingEntity = new ItemFrame(level, blockPos2, direction);
		} else {
			if (this.type != EntityType.GLOW_ITEM_FRAME) {
				return InteractionResult.SUCCESS;
			}

			hangingEntity = new GlowItemFrame(level, blockPos2, direction);
		}

		EntityType.<HangingEntity>createDefaultStackConfig(level, itemStack, player).accept(hangingEntity);
		if (hangingEntity.survives()) {
			if (!level.isClientSide()) {
				hangingEntity.playPlacementSound();
				level.gameEvent(player, GameEvent.ENTITY_PLACE, hangingEntity.position());
				level.addFreshEntity(hangingEntity);
			}

			itemStack.shrink(1);
			return InteractionResult.SUCCESS;
		} else {
			return InteractionResult.CONSUME;
		}
	}

	protected boolean mayPlace(Player player, Direction direction, ItemStack itemStack, BlockPos blockPos) {
		return !direction.getAxis().isVertical() && player.mayUseItemAt(blockPos, direction, itemStack);
	}

	@Override
	public void appendHoverText(
		ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag
	) {
		if (this.type == EntityType.PAINTING && tooltipDisplay.shows(DataComponents.PAINTING_VARIANT)) {
			Holder<PaintingVariant> holder = itemStack.get(DataComponents.PAINTING_VARIANT);
			if (holder != null) {
				holder.value().title().ifPresent(consumer);
				holder.value().author().ifPresent(consumer);
				consumer.accept(Component.translatable("painting.dimensions", holder.value().width(), holder.value().height()));
			} else if (tooltipFlag.isCreative()) {
				consumer.accept(TOOLTIP_RANDOM_VARIANT);
			}
		}
	}
}
