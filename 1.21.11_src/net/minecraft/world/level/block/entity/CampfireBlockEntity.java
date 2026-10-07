package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CampfireBlockEntity extends BlockEntity implements Clearable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int BURN_COOL_SPEED = 2;
	private static final int NUM_SLOTS = 4;
	private final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
	private final int[] cookingProgress = new int[4];
	private final int[] cookingTime = new int[4];

	public CampfireBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.CAMPFIRE, blockPos, blockState);
	}

	public static void cookTick(
		ServerLevel serverLevel,
		BlockPos blockPos,
		BlockState blockState,
		CampfireBlockEntity campfireBlockEntity,
		RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> cachedCheck
	) {
		boolean bl = false;

		for (int i = 0; i < campfireBlockEntity.items.size(); i++) {
			ItemStack itemStack = campfireBlockEntity.items.get(i);
			if (!itemStack.isEmpty()) {
				bl = true;
				campfireBlockEntity.cookingProgress[i]++;
				if (campfireBlockEntity.cookingProgress[i] >= campfireBlockEntity.cookingTime[i]) {
					SingleRecipeInput singleRecipeInput = new SingleRecipeInput(itemStack);
					ItemStack itemStack2 = cachedCheck.getRecipeFor(singleRecipeInput, serverLevel)
						.map(recipeHolder -> recipeHolder.value().assemble(singleRecipeInput, serverLevel.registryAccess()))
						.orElse(itemStack);
					if (itemStack2.isItemEnabled(serverLevel.enabledFeatures())) {
						Containers.dropItemStack(serverLevel, blockPos.getX(), blockPos.getY(), blockPos.getZ(), itemStack2);
						campfireBlockEntity.items.set(i, ItemStack.EMPTY);
						serverLevel.sendBlockUpdated(blockPos, blockState, blockState, 3);
						serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(blockState));
					}
				}
			}
		}

		if (bl) {
			setChanged(serverLevel, blockPos, blockState);
		}
	}

	public static void cooldownTick(Level level, BlockPos blockPos, BlockState blockState, CampfireBlockEntity campfireBlockEntity) {
		boolean bl = false;

		for (int i = 0; i < campfireBlockEntity.items.size(); i++) {
			if (campfireBlockEntity.cookingProgress[i] > 0) {
				bl = true;
				campfireBlockEntity.cookingProgress[i] = Mth.clamp(campfireBlockEntity.cookingProgress[i] - 2, 0, campfireBlockEntity.cookingTime[i]);
			}
		}

		if (bl) {
			setChanged(level, blockPos, blockState);
		}
	}

	public static void particleTick(Level level, BlockPos blockPos, BlockState blockState, CampfireBlockEntity campfireBlockEntity) {
		RandomSource randomSource = level.random;
		if (randomSource.nextFloat() < 0.11F) {
			for (int i = 0; i < randomSource.nextInt(2) + 2; i++) {
				CampfireBlock.makeParticles(level, blockPos, blockState.getValue(CampfireBlock.SIGNAL_FIRE), false);
			}
		}

		int i = blockState.getValue(CampfireBlock.FACING).get2DDataValue();

		for (int j = 0; j < campfireBlockEntity.items.size(); j++) {
			if (!campfireBlockEntity.items.get(j).isEmpty() && randomSource.nextFloat() < 0.2F) {
				Direction direction = Direction.from2DDataValue(Math.floorMod(j + i, 4));
				float f = 0.3125F;
				double d = blockPos.getX() + 0.5 - direction.getStepX() * 0.3125F + direction.getClockWise().getStepX() * 0.3125F;
				double e = blockPos.getY() + 0.5;
				double g = blockPos.getZ() + 0.5 - direction.getStepZ() * 0.3125F + direction.getClockWise().getStepZ() * 0.3125F;

				for (int k = 0; k < 4; k++) {
					level.addParticle(ParticleTypes.SMOKE, d, e, g, 0.0, 5.0E-4, 0.0);
				}
			}
		}
	}

	public NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items.clear();
		ContainerHelper.loadAllItems(valueInput, this.items);
		valueInput.getIntArray("CookingTimes")
			.ifPresentOrElse(
				is -> System.arraycopy(is, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, is.length)), () -> Arrays.fill(this.cookingProgress, 0)
			);
		valueInput.getIntArray("CookingTotalTimes")
			.ifPresentOrElse(is -> System.arraycopy(is, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, is.length)), () -> Arrays.fill(this.cookingTime, 0));
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		ContainerHelper.saveAllItems(valueOutput, this.items, true);
		valueOutput.putIntArray("CookingTimes", this.cookingProgress);
		valueOutput.putIntArray("CookingTotalTimes", this.cookingTime);
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, provider);
			ContainerHelper.saveAllItems(tagValueOutput, this.items, true);
			return tagValueOutput.buildResult();
		}
	}

	public boolean placeFood(ServerLevel serverLevel, @Nullable LivingEntity livingEntity, ItemStack itemStack) {
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack2 = this.items.get(i);
			if (itemStack2.isEmpty()) {
				Optional<RecipeHolder<CampfireCookingRecipe>> optional = serverLevel.recipeAccess()
					.getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(itemStack), serverLevel);
				if (optional.isEmpty()) {
					return false;
				}

				this.cookingTime[i] = optional.get().value().cookingTime();
				this.cookingProgress[i] = 0;
				this.items.set(i, itemStack.consumeAndReturn(1, livingEntity));
				serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(livingEntity, this.getBlockState()));
				this.markUpdated();
				return true;
			}
		}

		return false;
	}

	private void markUpdated() {
		this.setChanged();
		this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	public void preRemoveSideEffects(BlockPos blockPos, BlockState blockState) {
		if (this.level != null) {
			Containers.dropContents(this.level, blockPos, this.getItems());
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		super.applyImplicitComponents(dataComponentGetter);
		dataComponentGetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
	}

	@Override
	public void removeComponentsFromTag(ValueOutput valueOutput) {
		valueOutput.discard("Items");
	}
}
