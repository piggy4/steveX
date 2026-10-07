package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class LeadItem extends Item {
	public LeadItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext useOnContext) {
		Level level = useOnContext.getLevel();
		BlockPos blockPos = useOnContext.getClickedPos();
		BlockState blockState = level.getBlockState(blockPos);
		if (blockState.is(BlockTags.FENCES)) {
			Player player = useOnContext.getPlayer();
			if (!level.isClientSide() && player != null) {
				return bindPlayerMobs(player, level, blockPos);
			}
		}

		return InteractionResult.PASS;
	}

	public static InteractionResult bindPlayerMobs(Player player, Level level, BlockPos blockPos) {
		LeashFenceKnotEntity leashFenceKnotEntity = null;
		List<Leashable> list = Leashable.leashableInArea(level, Vec3.atCenterOf(blockPos), leashablex -> leashablex.getLeashHolder() == player);
		boolean bl = false;

		for (Leashable leashable : list) {
			if (leashFenceKnotEntity == null) {
				leashFenceKnotEntity = LeashFenceKnotEntity.getOrCreateKnot(level, blockPos);
				leashFenceKnotEntity.playPlacementSound();
			}

			if (leashable.canHaveALeashAttachedTo(leashFenceKnotEntity)) {
				leashable.setLeashedTo(leashFenceKnotEntity, true);
				bl = true;
			}
		}

		if (bl) {
			level.gameEvent(GameEvent.BLOCK_ATTACH, blockPos, GameEvent.Context.of(player));
			return InteractionResult.SUCCESS_SERVER;
		} else {
			return InteractionResult.PASS;
		}
	}
}
