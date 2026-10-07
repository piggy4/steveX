package net.minecraft.gametest.framework;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class GameTestHelper {
	private final GameTestInfo testInfo;
	private boolean finalCheckAdded;

	public GameTestHelper(GameTestInfo gameTestInfo) {
		this.testInfo = gameTestInfo;
	}

	public GameTestAssertException assertionException(Component component) {
		return new GameTestAssertException(component, this.testInfo.getTick());
	}

	public GameTestAssertException assertionException(String string, Object... objects) {
		return this.assertionException(Component.translatableEscape(string, objects));
	}

	public GameTestAssertPosException assertionException(BlockPos blockPos, Component component) {
		return new GameTestAssertPosException(component, this.absolutePos(blockPos), blockPos, this.testInfo.getTick());
	}

	public GameTestAssertPosException assertionException(BlockPos blockPos, String string, Object... objects) {
		return this.assertionException(blockPos, Component.translatableEscape(string, objects));
	}

	public ServerLevel getLevel() {
		return this.testInfo.getLevel();
	}

	public BlockState getBlockState(BlockPos blockPos) {
		return this.getLevel().getBlockState(this.absolutePos(blockPos));
	}

	public <T extends BlockEntity> T getBlockEntity(BlockPos blockPos, Class<T> class_) {
		BlockEntity blockEntity = this.getLevel().getBlockEntity(this.absolutePos(blockPos));
		if (blockEntity == null) {
			throw this.assertionException(blockPos, "test.error.missing_block_entity");
		} else if (class_.isInstance(blockEntity)) {
			return class_.cast(blockEntity);
		} else {
			throw this.assertionException(blockPos, "test.error.wrong_block_entity", blockEntity.getType().builtInRegistryHolder().getRegisteredName());
		}
	}

	public void killAllEntities() {
		this.killAllEntitiesOfClass(Entity.class);
	}

	public void killAllEntitiesOfClass(Class<? extends Entity> class_) {
		AABB aABB = this.getBounds();
		List<? extends Entity> list = this.getLevel().getEntitiesOfClass(class_, aABB.inflate(1.0), entity -> !(entity instanceof Player));
		list.forEach(entity -> entity.kill(this.getLevel()));
	}

	public ItemEntity spawnItem(Item item, Vec3 vec3) {
		ServerLevel serverLevel = this.getLevel();
		Vec3 vec32 = this.absoluteVec(vec3);
		ItemEntity itemEntity = new ItemEntity(serverLevel, vec32.x, vec32.y, vec32.z, new ItemStack(item, 1));
		itemEntity.setDeltaMovement(0.0, 0.0, 0.0);
		serverLevel.addFreshEntity(itemEntity);
		return itemEntity;
	}

	public ItemEntity spawnItem(Item item, float f, float g, float h) {
		return this.spawnItem(item, new Vec3(f, g, h));
	}

	public ItemEntity spawnItem(Item item, BlockPos blockPos) {
		return this.spawnItem(item, blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	public <E extends Entity> E spawn(EntityType<E> entityType, BlockPos blockPos) {
		return this.spawn(entityType, Vec3.atBottomCenterOf(blockPos));
	}

	public <E extends Entity> List<E> spawn(EntityType<E> entityType, BlockPos blockPos, int i) {
		return this.spawn(entityType, Vec3.atBottomCenterOf(blockPos), i);
	}

	public <E extends Entity> List<E> spawn(EntityType<E> entityType, Vec3 vec3, int i) {
		List<E> list = new ArrayList<>();

		for (int j = 0; j < i; j++) {
			list.add(this.spawn(entityType, vec3));
		}

		return list;
	}

	public <E extends Entity> E spawn(EntityType<E> entityType, Vec3 vec3) {
		return this.spawn(entityType, vec3, null);
	}

	public <E extends Entity> E spawn(EntityType<E> entityType, Vec3 vec3, @Nullable EntitySpawnReason entitySpawnReason) {
		ServerLevel serverLevel = this.getLevel();
		E entity = entityType.create(serverLevel, EntitySpawnReason.STRUCTURE);
		if (entity == null) {
			throw this.assertionException(BlockPos.containing(vec3), "test.error.spawn_failure", entityType.builtInRegistryHolder().getRegisteredName());
		}

		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
		}

		Vec3 vec32 = this.absoluteVec(vec3);
		float f = entity.rotate(this.getTestRotation());
		entity.snapTo(vec32.x, vec32.y, vec32.z, f, entity.getXRot());
		entity.setYBodyRot(f);
		entity.setYHeadRot(f);
		if (entitySpawnReason != null && entity instanceof Mob mob2) {
			mob2.finalizeSpawn(this.getLevel(), this.getLevel().getCurrentDifficultyAt(mob2.blockPosition()), entitySpawnReason, null);
		}

		serverLevel.addFreshEntityWithPassengers(entity);
		return entity;
	}

	public <E extends Mob> E spawn(EntityType<E> entityType, int i, int j, int k, EntitySpawnReason entitySpawnReason) {
		return this.spawn(entityType, new Vec3(i, j, k), entitySpawnReason);
	}

	public void hurt(Entity entity, DamageSource damageSource, float f) {
		entity.hurtServer(this.getLevel(), damageSource, f);
	}

	public void kill(Entity entity) {
		entity.kill(this.getLevel());
	}

	public <E extends Entity> E findOneEntity(EntityType<E> entityType) {
		return this.findClosestEntity(entityType, 0, 0, 0, 2.147483647E9);
	}

	public <E extends Entity> E findClosestEntity(EntityType<E> entityType, int i, int j, int k, double d) {
		List<E> list = this.findEntities(entityType, i, j, k, d);
		if (list.isEmpty()) {
			throw this.assertionException("test.error.expected_entity_around", entityType.getDescription(), i, j, k);
		}

		if (list.size() > 1) {
			throw this.assertionException("test.error.too_many_entities", entityType.toShortString(), i, j, k, list.size());
		}

		Vec3 vec3 = this.absoluteVec(new Vec3(i, j, k));
		list.sort((entity, entity2) -> {
			double dx = entity.position().distanceTo(vec3);
			double e = entity2.position().distanceTo(vec3);
			return Double.compare(dx, e);
		});
		return list.get(0);
	}

	public <E extends Entity> List<E> findEntities(EntityType<E> entityType, int i, int j, int k, double d) {
		return this.findEntities(entityType, Vec3.atBottomCenterOf(new BlockPos(i, j, k)), d);
	}

	public <E extends Entity> List<E> findEntities(EntityType<E> entityType, Vec3 vec3, double d) {
		ServerLevel serverLevel = this.getLevel();
		Vec3 vec32 = this.absoluteVec(vec3);
		AABB aABB = this.testInfo.getStructureBounds();
		AABB aABB2 = new AABB(vec32.add(-d, -d, -d), vec32.add(d, d, d));
		return serverLevel.getEntities(entityType, aABB, entity -> entity.getBoundingBox().intersects(aABB2) && entity.isAlive());
	}

	public <E extends Entity> E spawn(EntityType<E> entityType, int i, int j, int k) {
		return this.spawn(entityType, new BlockPos(i, j, k));
	}

	public <E extends Entity> E spawn(EntityType<E> entityType, float f, float g, float h) {
		return this.spawn(entityType, new Vec3(f, g, h));
	}

	public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, BlockPos blockPos) {
		E mob = (E)this.spawn(entityType, blockPos);
		mob.removeFreeWill();
		return mob;
	}

	public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, int i, int j, int k) {
		return this.spawnWithNoFreeWill(entityType, new BlockPos(i, j, k));
	}

	public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, Vec3 vec3) {
		E mob = (E)this.spawn(entityType, vec3);
		mob.removeFreeWill();
		return mob;
	}

	public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> entityType, float f, float g, float h) {
		return this.spawnWithNoFreeWill(entityType, new Vec3(f, g, h));
	}

	public void moveTo(Mob mob, float f, float g, float h) {
		Vec3 vec3 = this.absoluteVec(new Vec3(f, g, h));
		mob.snapTo(vec3.x, vec3.y, vec3.z, mob.getYRot(), mob.getXRot());
	}

	public GameTestSequence walkTo(Mob mob, BlockPos blockPos, float f) {
		return this.startSequence().thenExecuteAfter(2, () -> {
			Path path = mob.getNavigation().createPath(this.absolutePos(blockPos), 0);
			mob.getNavigation().moveTo(path, f);
		});
	}

	public void pressButton(int i, int j, int k) {
		this.pressButton(new BlockPos(i, j, k));
	}

	public void pressButton(BlockPos blockPos) {
		this.assertBlockTag(BlockTags.BUTTONS, blockPos);
		BlockPos blockPos2 = this.absolutePos(blockPos);
		BlockState blockState = this.getLevel().getBlockState(blockPos2);
		ButtonBlock buttonBlock = (ButtonBlock)blockState.getBlock();
		buttonBlock.press(blockState, this.getLevel(), blockPos2, null);
	}

	public void useBlock(BlockPos blockPos) {
		this.useBlock(blockPos, this.makeMockPlayer(GameType.CREATIVE));
	}

	public void useBlock(BlockPos blockPos, Player player) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		this.useBlock(blockPos, player, new BlockHitResult(Vec3.atCenterOf(blockPos2), Direction.NORTH, blockPos2, true));
	}

	public void useBlock(BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		BlockState blockState = this.getLevel().getBlockState(blockPos2);
		InteractionHand interactionHand = InteractionHand.MAIN_HAND;
		InteractionResult interactionResult = blockState.useItemOn(player.getItemInHand(interactionHand), this.getLevel(), player, interactionHand, blockHitResult);
		if (!interactionResult.consumesAction()) {
			if (!(interactionResult instanceof InteractionResult.TryEmptyHandInteraction)
				|| !blockState.useWithoutItem(this.getLevel(), player, blockHitResult).consumesAction()) {
				UseOnContext useOnContext = new UseOnContext(player, interactionHand, blockHitResult);
				player.getItemInHand(interactionHand).useOn(useOnContext);
			}
		}
	}

	public LivingEntity makeAboutToDrown(LivingEntity livingEntity) {
		livingEntity.setAirSupply(0);
		livingEntity.setHealth(0.25F);
		return livingEntity;
	}

	public LivingEntity withLowHealth(LivingEntity livingEntity) {
		livingEntity.setHealth(0.25F);
		return livingEntity;
	}

	public Player makeMockPlayer(GameType gameType) {
		return new Player(this.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player")) {
			@Override
			public GameType gameMode() {
				return gameType;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
	}

	@Deprecated(forRemoval = true)
	public ServerPlayer makeMockServerPlayerInLevel() {
		CommonListenerCookie commonListenerCookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
		ServerPlayer serverPlayer = new ServerPlayer(
			this.getLevel().getServer(), this.getLevel(), commonListenerCookie.gameProfile(), commonListenerCookie.clientInformation()
		) {
			@Override
			public GameType gameMode() {
				return GameType.CREATIVE;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		this.getLevel().getServer().getPlayerList().placeNewPlayer(connection, serverPlayer, commonListenerCookie);
		return serverPlayer;
	}

	public void pullLever(int i, int j, int k) {
		this.pullLever(new BlockPos(i, j, k));
	}

	public void pullLever(BlockPos blockPos) {
		this.assertBlockPresent(Blocks.LEVER, blockPos);
		BlockPos blockPos2 = this.absolutePos(blockPos);
		BlockState blockState = this.getLevel().getBlockState(blockPos2);
		LeverBlock leverBlock = (LeverBlock)blockState.getBlock();
		leverBlock.pull(blockState, this.getLevel(), blockPos2, null);
	}

	public void pulseRedstone(BlockPos blockPos, long l) {
		this.setBlock(blockPos, Blocks.REDSTONE_BLOCK);
		this.runAfterDelay(l, () -> this.setBlock(blockPos, Blocks.AIR));
	}

	public void destroyBlock(BlockPos blockPos) {
		this.getLevel().destroyBlock(this.absolutePos(blockPos), false, null);
	}

	public void setBlock(int i, int j, int k, Block block) {
		this.setBlock(new BlockPos(i, j, k), block);
	}

	public void setBlock(int i, int j, int k, BlockState blockState) {
		this.setBlock(new BlockPos(i, j, k), blockState);
	}

	public void setBlock(BlockPos blockPos, Block block) {
		this.setBlock(blockPos, block.defaultBlockState());
	}

	public void setBlock(BlockPos blockPos, BlockState blockState) {
		this.getLevel().setBlock(this.absolutePos(blockPos), blockState, 3);
	}

	public void setBlock(BlockPos blockPos, Block block, Direction direction) {
		this.setBlock(blockPos, block.defaultBlockState(), direction);
	}

	public void setBlock(BlockPos blockPos, BlockState blockState, Direction direction) {
		BlockState blockState2 = blockState;
		if (blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
			blockState2 = blockState.setValue(HorizontalDirectionalBlock.FACING, direction);
		}

		if (blockState.hasProperty(BlockStateProperties.FACING)) {
			blockState2 = blockState.setValue(BlockStateProperties.FACING, direction);
		}

		this.getLevel().setBlock(this.absolutePos(blockPos), blockState2, 3);
	}

	public void assertBlockPresent(Block block, int i, int j, int k) {
		this.assertBlockPresent(block, new BlockPos(i, j, k));
	}

	public void assertBlockPresent(Block block, BlockPos blockPos) {
		BlockState blockState = this.getBlockState(blockPos);
		this.assertBlock(blockPos, block2 -> blockState.is(block), block2 -> Component.translatable("test.error.expected_block", block.getName(), block2.getName()));
	}

	public void assertBlockNotPresent(Block block, int i, int j, int k) {
		this.assertBlockNotPresent(block, new BlockPos(i, j, k));
	}

	public void assertBlockNotPresent(Block block, BlockPos blockPos) {
		this.assertBlock(
			blockPos, block2 -> !this.getBlockState(blockPos).is(block), block2 -> Component.translatable("test.error.unexpected_block", block.getName())
		);
	}

	public void assertBlockTag(TagKey<Block> tagKey, BlockPos blockPos) {
		this.assertBlockState(
			blockPos,
			blockState -> blockState.is(tagKey),
			blockState -> Component.translatable("test.error.expected_block_tag", Component.translationArg(tagKey.location()), blockState.getBlock().getName())
		);
	}

	public void succeedWhenBlockPresent(Block block, int i, int j, int k) {
		this.succeedWhenBlockPresent(block, new BlockPos(i, j, k));
	}

	public void succeedWhenBlockPresent(Block block, BlockPos blockPos) {
		this.succeedWhen(() -> this.assertBlockPresent(block, blockPos));
	}

	public void assertBlock(BlockPos blockPos, Predicate<Block> predicate, Function<Block, Component> function) {
		this.assertBlockState(blockPos, blockState -> predicate.test(blockState.getBlock()), blockState -> function.apply(blockState.getBlock()));
	}

	public <T extends Comparable<T>> void assertBlockProperty(BlockPos blockPos, Property<T> property, T comparable) {
		BlockState blockState = this.getBlockState(blockPos);
		boolean bl = blockState.hasProperty(property);
		if (!bl) {
			throw this.assertionException(blockPos, "test.error.block_property_missing", property.getName(), comparable);
		}

		if (!blockState.<T>getValue(property).equals(comparable)) {
			throw this.assertionException(blockPos, "test.error.block_property_mismatch", property.getName(), comparable, blockState.getValue(property));
		}
	}

	public <T extends Comparable<T>> void assertBlockProperty(BlockPos blockPos, Property<T> property, Predicate<T> predicate, Component component) {
		this.assertBlockState(blockPos, blockState -> {
			if (!blockState.hasProperty(property)) {
				return false;
			}

			T comparable = blockState.getValue(property);
			return predicate.test(comparable);
		}, blockState -> component);
	}

	public void assertBlockState(BlockPos blockPos, BlockState blockState) {
		BlockState blockState2 = this.getBlockState(blockPos);
		if (!blockState2.equals(blockState)) {
			throw this.assertionException(blockPos, "test.error.state_not_equal", blockState, blockState2);
		}
	}

	public void assertBlockState(BlockPos blockPos, Predicate<BlockState> predicate, Function<BlockState, Component> function) {
		BlockState blockState = this.getBlockState(blockPos);
		if (!predicate.test(blockState)) {
			throw this.assertionException(blockPos, function.apply(blockState));
		}
	}

	public <T extends BlockEntity> void assertBlockEntityData(BlockPos blockPos, Class<T> class_, Predicate<T> predicate, Supplier<Component> supplier) {
		T blockEntity = this.getBlockEntity(blockPos, class_);
		if (!predicate.test(blockEntity)) {
			throw this.assertionException(blockPos, supplier.get());
		}
	}

	public void assertRedstoneSignal(BlockPos blockPos, Direction direction, IntPredicate intPredicate, Supplier<Component> supplier) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		ServerLevel serverLevel = this.getLevel();
		BlockState blockState = serverLevel.getBlockState(blockPos2);
		int i = blockState.getSignal(serverLevel, blockPos2, direction);
		if (!intPredicate.test(i)) {
			throw this.assertionException(blockPos, supplier.get());
		}
	}

	public void assertEntityPresent(EntityType<?> entityType) {
		if (!this.getLevel().hasEntities(entityType, this.getBounds(), Entity::isAlive)) {
			throw this.assertionException("test.error.expected_entity_in_test", entityType.getDescription());
		}
	}

	public void assertEntityPresent(EntityType<?> entityType, int i, int j, int k) {
		this.assertEntityPresent(entityType, new BlockPos(i, j, k));
	}

	public void assertEntityPresent(EntityType<?> entityType, BlockPos blockPos) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		if (!this.getLevel().hasEntities(entityType, new AABB(blockPos2), Entity::isAlive)) {
			throw this.assertionException(blockPos, "test.error.expected_entity", entityType.getDescription());
		}
	}

	public void assertEntityPresent(EntityType<?> entityType, AABB aABB) {
		AABB aABB2 = this.absoluteAABB(aABB);
		if (!this.getLevel().hasEntities(entityType, aABB2, Entity::isAlive)) {
			throw this.assertionException(BlockPos.containing(aABB.getCenter()), "test.error.expected_entity", entityType.getDescription());
		}
	}

	public void assertEntityPresent(EntityType<?> entityType, AABB aABB, Component component) {
		AABB aABB2 = this.absoluteAABB(aABB);
		if (!this.getLevel().hasEntities(entityType, aABB2, Entity::isAlive)) {
			throw this.assertionException(BlockPos.containing(aABB.getCenter()), component);
		}
	}

	public void assertEntitiesPresent(EntityType<?> entityType, int i) {
		List<? extends Entity> list = this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);
		if (list.size() != i) {
			throw this.assertionException("test.error.expected_entity_count", i, entityType.getDescription(), list.size());
		}
	}

	public void assertEntitiesPresent(EntityType<?> entityType, BlockPos blockPos, int i, double d) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		List<? extends Entity> list = this.getEntities((EntityType<? extends Entity>)entityType, blockPos, d);
		if (list.size() != i) {
			throw this.assertionException(blockPos, "test.error.expected_entity_count", i, entityType.getDescription(), list.size());
		}
	}

	public void assertEntityPresent(EntityType<?> entityType, BlockPos blockPos, double d) {
		List<? extends Entity> list = this.getEntities((EntityType<? extends Entity>)entityType, blockPos, d);
		if (list.isEmpty()) {
			BlockPos blockPos2 = this.absolutePos(blockPos);
			throw this.assertionException(blockPos, "test.error.expected_entity", entityType.getDescription());
		}
	}

	public <T extends Entity> List<T> getEntities(EntityType<T> entityType, BlockPos blockPos, double d) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		return this.getLevel().getEntities(entityType, new AABB(blockPos2).inflate(d), Entity::isAlive);
	}

	public <T extends Entity> List<T> getEntities(EntityType<T> entityType) {
		return this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);
	}

	public void assertEntityInstancePresent(Entity entity, int i, int j, int k) {
		this.assertEntityInstancePresent(entity, new BlockPos(i, j, k));
	}

	public void assertEntityInstancePresent(Entity entity, BlockPos blockPos) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		List<? extends Entity> list = this.getLevel().getEntities(entity.getType(), new AABB(blockPos2), Entity::isAlive);
		list.stream()
			.filter(entity2 -> entity2 == entity)
			.findFirst()
			.orElseThrow(() -> this.assertionException(blockPos, "test.error.expected_entity", entity.getType().getDescription()));
	}

	public void assertItemEntityCountIs(Item item, BlockPos blockPos, double d, int i) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		List<ItemEntity> list = this.getLevel().getEntities(EntityType.ITEM, new AABB(blockPos2).inflate(d), Entity::isAlive);
		int j = 0;

		for (ItemEntity itemEntity : list) {
			ItemStack itemStack = itemEntity.getItem();
			if (itemStack.is(item)) {
				j += itemStack.getCount();
			}
		}

		if (j != i) {
			throw this.assertionException(blockPos, "test.error.expected_items_count", i, item.getName(), j);
		}
	}

	public void assertItemEntityPresent(Item item, BlockPos blockPos, double d) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		Predicate<ItemEntity> predicate = itemEntity -> itemEntity.isAlive() && itemEntity.getItem().is(item);
		if (!this.getLevel().hasEntities(EntityType.ITEM, new AABB(blockPos2).inflate(d), predicate)) {
			throw this.assertionException(blockPos, "test.error.expected_item", item.getName());
		}
	}

	public void assertItemEntityNotPresent(Item item, BlockPos blockPos, double d) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		Predicate<ItemEntity> predicate = itemEntity -> itemEntity.isAlive() && itemEntity.getItem().is(item);
		if (this.getLevel().hasEntities(EntityType.ITEM, new AABB(blockPos2).inflate(d), predicate)) {
			throw this.assertionException(blockPos, "test.error.unexpected_item", item.getName());
		}
	}

	public void assertItemEntityPresent(Item item) {
		Predicate<ItemEntity> predicate = itemEntity -> itemEntity.isAlive() && itemEntity.getItem().is(item);
		if (!this.getLevel().hasEntities(EntityType.ITEM, this.getBounds(), predicate)) {
			throw this.assertionException("test.error.expected_item", item.getName());
		}
	}

	public void assertItemEntityNotPresent(Item item) {
		Predicate<ItemEntity> predicate = itemEntity -> itemEntity.isAlive() && itemEntity.getItem().is(item);
		if (this.getLevel().hasEntities(EntityType.ITEM, this.getBounds(), predicate)) {
			throw this.assertionException("test.error.unexpected_item", item.getName());
		}
	}

	public void assertEntityNotPresent(EntityType<?> entityType) {
		List<? extends Entity> list = this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);
		if (!list.isEmpty()) {
			throw this.assertionException(list.getFirst().blockPosition(), "test.error.unexpected_entity", entityType.getDescription());
		}
	}

	public void assertEntityNotPresent(EntityType<?> entityType, int i, int j, int k) {
		this.assertEntityNotPresent(entityType, new BlockPos(i, j, k));
	}

	public void assertEntityNotPresent(EntityType<?> entityType, BlockPos blockPos) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		if (this.getLevel().hasEntities(entityType, new AABB(blockPos2), Entity::isAlive)) {
			throw this.assertionException(blockPos, "test.error.unexpected_entity", entityType.getDescription());
		}
	}

	public void assertEntityNotPresent(EntityType<?> entityType, AABB aABB) {
		AABB aABB2 = this.absoluteAABB(aABB);
		List<? extends Entity> list = this.getLevel().getEntities(entityType, aABB2, Entity::isAlive);
		if (!list.isEmpty()) {
			throw this.assertionException(list.getFirst().blockPosition(), "test.error.unexpected_entity", entityType.getDescription());
		}
	}

	public void assertEntityTouching(EntityType<?> entityType, double d, double e, double f) {
		Vec3 vec3 = new Vec3(d, e, f);
		Vec3 vec32 = this.absoluteVec(vec3);
		Predicate<? super Entity> predicate = entity -> entity.getBoundingBox().intersects(vec32, vec32);
		if (!this.getLevel().hasEntities(entityType, this.getBounds(), predicate)) {
			throw this.assertionException("test.error.expected_entity_touching", entityType.getDescription(), vec32.x(), vec32.y(), vec32.z(), d, e, f);
		}
	}

	public void assertEntityNotTouching(EntityType<?> entityType, double d, double e, double f) {
		Vec3 vec3 = new Vec3(d, e, f);
		Vec3 vec32 = this.absoluteVec(vec3);
		Predicate<? super Entity> predicate = entity -> !entity.getBoundingBox().intersects(vec32, vec32);
		if (!this.getLevel().hasEntities(entityType, this.getBounds(), predicate)) {
			throw this.assertionException("test.error.expected_entity_not_touching", entityType.getDescription(), vec32.x(), vec32.y(), vec32.z(), d, e, f);
		}
	}

	public <E extends Entity, T> void assertEntityData(BlockPos blockPos, EntityType<E> entityType, Predicate<E> predicate) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		List<E> list = this.getLevel().getEntities(entityType, new AABB(blockPos2), Entity::isAlive);
		if (list.isEmpty()) {
			throw this.assertionException(blockPos, "test.error.expected_entity", entityType.getDescription());
		}

		for (E entity : list) {
			if (!predicate.test(entity)) {
				throw this.assertionException(entity.blockPosition(), "test.error.expected_entity_data_predicate", entity.getName());
			}
		}
	}

	public <E extends Entity, T> void assertEntityData(BlockPos blockPos, EntityType<E> entityType, Function<? super E, T> function, @Nullable T object) {
		this.assertEntityData(new AABB(blockPos), entityType, function, object);
	}

	public <E extends Entity, T> void assertEntityData(AABB aABB, EntityType<E> entityType, Function<? super E, T> function, @Nullable T object) {
		List<E> list = this.getLevel().getEntities(entityType, this.absoluteAABB(aABB), Entity::isAlive);
		if (list.isEmpty()) {
			throw this.assertionException(BlockPos.containing(aABB.getBottomCenter()), "test.error.expected_entity", entityType.getDescription());
		}

		for (E entity : list) {
			T object2 = function.apply(entity);
			if (!Objects.equals(object2, object)) {
				throw this.assertionException(BlockPos.containing(aABB.getBottomCenter()), "test.error.expected_entity_data", object, object2);
			}
		}
	}

	public <E extends LivingEntity> void assertEntityIsHolding(BlockPos blockPos, EntityType<E> entityType, Item item) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		List<E> list = this.getLevel().getEntities(entityType, new AABB(blockPos2), Entity::isAlive);
		if (list.isEmpty()) {
			throw this.assertionException(blockPos, "test.error.expected_entity", entityType.getDescription());
		}

		for (E livingEntity : list) {
			if (livingEntity.isHolding(item)) {
				return;
			}
		}

		throw this.assertionException(blockPos, "test.error.expected_entity_holding", item.getName());
	}

	public <E extends Entity & InventoryCarrier> void assertEntityInventoryContains(BlockPos blockPos, EntityType<E> entityType, Item item) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		List<E> list = this.getLevel().getEntities(entityType, new AABB(blockPos2), object -> object.isAlive());
		if (list.isEmpty()) {
			throw this.assertionException(blockPos, "test.error.expected_entity", entityType.getDescription());
		}

		for (E entity : list) {
			if (entity.getInventory().hasAnyMatching(itemStack -> itemStack.is(item))) {
				return;
			}
		}

		throw this.assertionException(blockPos, "test.error.expected_entity_having", item.getName());
	}

	public void assertContainerEmpty(BlockPos blockPos) {
		BaseContainerBlockEntity baseContainerBlockEntity = this.getBlockEntity(blockPos, BaseContainerBlockEntity.class);
		if (!baseContainerBlockEntity.isEmpty()) {
			throw this.assertionException(blockPos, "test.error.expected_empty_container");
		}
	}

	public void assertContainerContainsSingle(BlockPos blockPos, Item item) {
		BaseContainerBlockEntity baseContainerBlockEntity = this.getBlockEntity(blockPos, BaseContainerBlockEntity.class);
		if (baseContainerBlockEntity.countItem(item) != 1) {
			throw this.assertionException(blockPos, "test.error.expected_container_contents_single", item.getName());
		}
	}

	public void assertContainerContains(BlockPos blockPos, Item item) {
		BaseContainerBlockEntity baseContainerBlockEntity = this.getBlockEntity(blockPos, BaseContainerBlockEntity.class);
		if (baseContainerBlockEntity.countItem(item) == 0) {
			throw this.assertionException(blockPos, "test.error.expected_container_contents", item.getName());
		}
	}

	public void assertSameBlockStates(BoundingBox boundingBox, BlockPos blockPos) {
		BlockPos.betweenClosedStream(boundingBox).forEach(blockPos2 -> {
			BlockPos blockPos3 = blockPos.offset(blockPos2.getX() - boundingBox.minX(), blockPos2.getY() - boundingBox.minY(), blockPos2.getZ() - boundingBox.minZ());
			this.assertSameBlockState(blockPos2, blockPos3);
		});
	}

	public void assertSameBlockState(BlockPos blockPos, BlockPos blockPos2) {
		BlockState blockState = this.getBlockState(blockPos);
		BlockState blockState2 = this.getBlockState(blockPos2);
		if (blockState != blockState2) {
			throw this.assertionException(blockPos, "test.error.state_not_equal", blockState2, blockState);
		}
	}

	public void assertAtTickTimeContainerContains(long l, BlockPos blockPos, Item item) {
		this.runAtTickTime(l, () -> this.assertContainerContainsSingle(blockPos, item));
	}

	public void assertAtTickTimeContainerEmpty(long l, BlockPos blockPos) {
		this.runAtTickTime(l, () -> this.assertContainerEmpty(blockPos));
	}

	public <E extends Entity, T> void succeedWhenEntityData(BlockPos blockPos, EntityType<E> entityType, Function<E, T> function, T object) {
		this.succeedWhen(() -> this.assertEntityData(blockPos, entityType, function, object));
	}

	public <E extends Entity> void assertEntityProperty(E entity, Predicate<E> predicate, Component component) {
		if (!predicate.test(entity)) {
			throw this.assertionException(entity.blockPosition(), "test.error.entity_property", entity.getName(), component);
		}
	}

	public <E extends Entity, T> void assertEntityProperty(E entity, Function<E, T> function, T object, Component component) {
		T object2 = function.apply(entity);
		if (!object2.equals(object)) {
			throw this.assertionException(entity.blockPosition(), "test.error.entity_property_details", entity.getName(), component, object2, object);
		}
	}

	public void assertLivingEntityHasMobEffect(LivingEntity livingEntity, Holder<MobEffect> holder, int i) {
		MobEffectInstance mobEffectInstance = livingEntity.getEffect(holder);
		if (mobEffectInstance == null || mobEffectInstance.getAmplifier() != i) {
			throw this.assertionException("test.error.expected_entity_effect", livingEntity.getName(), PotionContents.getPotionDescription(holder, i));
		}
	}

	public void succeedWhenEntityPresent(EntityType<?> entityType, int i, int j, int k) {
		this.succeedWhenEntityPresent(entityType, new BlockPos(i, j, k));
	}

	public void succeedWhenEntityPresent(EntityType<?> entityType, BlockPos blockPos) {
		this.succeedWhen(() -> this.assertEntityPresent(entityType, blockPos));
	}

	public void succeedWhenEntityNotPresent(EntityType<?> entityType, int i, int j, int k) {
		this.succeedWhenEntityNotPresent(entityType, new BlockPos(i, j, k));
	}

	public void succeedWhenEntityNotPresent(EntityType<?> entityType, BlockPos blockPos) {
		this.succeedWhen(() -> this.assertEntityNotPresent(entityType, blockPos));
	}

	public void succeed() {
		this.testInfo.succeed();
	}

	private void ensureSingleFinalCheck() {
		if (this.finalCheckAdded) {
			throw new IllegalStateException("This test already has final clause");
		}

		this.finalCheckAdded = true;
	}

	public void succeedIf(Runnable runnable) {
		this.ensureSingleFinalCheck();
		this.testInfo.createSequence().thenWaitUntil(0L, runnable).thenSucceed();
	}

	public void succeedWhen(Runnable runnable) {
		this.ensureSingleFinalCheck();
		this.testInfo.createSequence().thenWaitUntil(runnable).thenSucceed();
	}

	public void succeedOnTickWhen(int i, Runnable runnable) {
		this.ensureSingleFinalCheck();
		this.testInfo.createSequence().thenWaitUntil(i, runnable).thenSucceed();
	}

	public void runAtTickTime(long l, Runnable runnable) {
		this.testInfo.setRunAtTickTime(l, runnable);
	}

	public void runAfterDelay(long l, Runnable runnable) {
		this.runAtTickTime(this.testInfo.getTick() + l, runnable);
	}

	public void randomTick(BlockPos blockPos) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		ServerLevel serverLevel = this.getLevel();
		serverLevel.getBlockState(blockPos2).randomTick(serverLevel, blockPos2, serverLevel.random);
	}

	public void tickBlock(BlockPos blockPos) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		ServerLevel serverLevel = this.getLevel();
		serverLevel.getBlockState(blockPos2).tick(serverLevel, blockPos2, serverLevel.random);
	}

	public void tickPrecipitation(BlockPos blockPos) {
		BlockPos blockPos2 = this.absolutePos(blockPos);
		ServerLevel serverLevel = this.getLevel();
		serverLevel.tickPrecipitation(blockPos2);
	}

	public void tickPrecipitation() {
		AABB aABB = this.getRelativeBounds();
		int i = (int)Math.floor(aABB.maxX);
		int j = (int)Math.floor(aABB.maxZ);
		int k = (int)Math.floor(aABB.maxY);

		for (int l = (int)Math.floor(aABB.minX); l < i; l++) {
			for (int m = (int)Math.floor(aABB.minZ); m < j; m++) {
				this.tickPrecipitation(new BlockPos(l, k, m));
			}
		}
	}

	public int getHeight(Heightmap.Types types, int i, int j) {
		BlockPos blockPos = this.absolutePos(new BlockPos(i, 0, j));
		return this.relativePos(this.getLevel().getHeightmapPos(types, blockPos)).getY();
	}

	public void fail(Component component, BlockPos blockPos) {
		throw this.assertionException(blockPos, component);
	}

	public void fail(Component component, Entity entity) {
		throw this.assertionException(entity.blockPosition(), component);
	}

	public void fail(Component component) {
		throw this.assertionException(component);
	}

	public void fail(String string) {
		throw this.assertionException(Component.literal(string));
	}

	public void failIf(Runnable runnable) {
		this.testInfo.createSequence().thenWaitUntil(runnable).thenFail(() -> this.assertionException("test.error.fail"));
	}

	public void failIfEver(Runnable runnable) {
		LongStream.range(this.testInfo.getTick(), this.testInfo.getTimeoutTicks()).forEach(l -> this.testInfo.setRunAtTickTime(l, runnable::run));
	}

	public GameTestSequence startSequence() {
		return this.testInfo.createSequence();
	}

	public BlockPos absolutePos(BlockPos blockPos) {
		BlockPos blockPos2 = this.testInfo.getTestOrigin();
		BlockPos blockPos3 = blockPos2.offset(blockPos);
		return StructureTemplate.transform(blockPos3, Mirror.NONE, this.testInfo.getRotation(), blockPos2);
	}

	public BlockPos relativePos(BlockPos blockPos) {
		BlockPos blockPos2 = this.testInfo.getTestOrigin();
		Rotation rotation = this.testInfo.getRotation().getRotated(Rotation.CLOCKWISE_180);
		BlockPos blockPos3 = StructureTemplate.transform(blockPos, Mirror.NONE, rotation, blockPos2);
		return blockPos3.subtract(blockPos2);
	}

	public AABB absoluteAABB(AABB aABB) {
		Vec3 vec3 = this.absoluteVec(aABB.getMinPosition());
		Vec3 vec32 = this.absoluteVec(aABB.getMaxPosition());
		return new AABB(vec3, vec32);
	}

	public AABB relativeAABB(AABB aABB) {
		Vec3 vec3 = this.relativeVec(aABB.getMinPosition());
		Vec3 vec32 = this.relativeVec(aABB.getMaxPosition());
		return new AABB(vec3, vec32);
	}

	public Vec3 absoluteVec(Vec3 vec3) {
		Vec3 vec32 = Vec3.atLowerCornerOf(this.testInfo.getTestOrigin());
		return StructureTemplate.transform(vec32.add(vec3), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getTestOrigin());
	}

	public Vec3 relativeVec(Vec3 vec3) {
		Vec3 vec32 = Vec3.atLowerCornerOf(this.testInfo.getTestOrigin());
		return StructureTemplate.transform(vec3.subtract(vec32), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getTestOrigin());
	}

	public Rotation getTestRotation() {
		return this.testInfo.getRotation();
	}

	public Direction getTestDirection() {
		return this.testInfo.getRotation().rotate(Direction.SOUTH);
	}

	public Direction getAbsoluteDirection(Direction direction) {
		return this.getTestRotation().rotate(direction);
	}

	public void assertTrue(boolean bl, Component component) {
		if (!bl) {
			throw this.assertionException(component);
		}
	}

	public void assertTrue(boolean bl, String string) {
		this.assertTrue(bl, Component.literal(string));
	}

	public <N> void assertValueEqual(N object, N object2, String string) {
		this.assertValueEqual(object, object2, Component.literal(string));
	}

	public <N> void assertValueEqual(N object, N object2, Component component) {
		if (!object.equals(object2)) {
			throw this.assertionException("test.error.value_not_equal", component, object, object2);
		}
	}

	public void assertFalse(boolean bl, Component component) {
		this.assertTrue(!bl, component);
	}

	public void assertFalse(boolean bl, String string) {
		this.assertFalse(bl, Component.literal(string));
	}

	public long getTick() {
		return this.testInfo.getTick();
	}

	public AABB getBounds() {
		return this.testInfo.getStructureBounds();
	}

	public AABB getRelativeBounds() {
		AABB aABB = this.testInfo.getStructureBounds();
		Rotation rotation = this.testInfo.getRotation();
		switch (rotation) {
			case COUNTERCLOCKWISE_90:
			case CLOCKWISE_90:
				return new AABB(0.0, 0.0, 0.0, aABB.getZsize(), aABB.getYsize(), aABB.getXsize());
			default:
				return new AABB(0.0, 0.0, 0.0, aABB.getXsize(), aABB.getYsize(), aABB.getZsize());
		}
	}

	public void forEveryBlockInStructure(Consumer<BlockPos> consumer) {
		AABB aABB = this.getRelativeBounds().contract(1.0, 1.0, 1.0);
		BlockPos.MutableBlockPos.betweenClosedStream(aABB).forEach(consumer);
	}

	public void onEachTick(Runnable runnable) {
		LongStream.range(this.testInfo.getTick(), this.testInfo.getTimeoutTicks()).forEach(l -> this.testInfo.setRunAtTickTime(l, runnable::run));
	}

	public void placeAt(Player player, ItemStack itemStack, BlockPos blockPos, Direction direction) {
		BlockPos blockPos2 = this.absolutePos(blockPos.relative(direction));
		BlockHitResult blockHitResult = new BlockHitResult(Vec3.atCenterOf(blockPos2), direction, blockPos2, false);
		UseOnContext useOnContext = new UseOnContext(player, InteractionHand.MAIN_HAND, blockHitResult);
		itemStack.useOn(useOnContext);
	}

	public void setBiome(ResourceKey<Biome> resourceKey) {
		AABB aABB = this.getBounds();
		BlockPos blockPos = BlockPos.containing(aABB.minX, aABB.minY, aABB.minZ);
		BlockPos blockPos2 = BlockPos.containing(aABB.maxX, aABB.maxY, aABB.maxZ);
		Either<Integer, CommandSyntaxException> either = FillBiomeCommand.fill(
			this.getLevel(), blockPos, blockPos2, this.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(resourceKey)
		);
		if (either.right().isPresent()) {
			throw this.assertionException("test.error.set_biome");
		}
	}
}
