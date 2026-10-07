package net.minecraft.client.multiplayer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.sounds.DirectionalSoundInstance;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.AmbientParticle;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientLevel extends Level implements CacheSlot.Cleaner<ClientLevel> {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Component DEFAULT_QUIT_MESSAGE = Component.translatable("multiplayer.status.quitting");
	private static final double FLUID_PARTICLE_SPAWN_OFFSET = 0.05;
	private static final int NORMAL_LIGHT_UPDATES_PER_FRAME = 10;
	private static final int LIGHT_UPDATE_QUEUE_SIZE_THRESHOLD = 1000;
	final EntityTickList tickingEntities = new EntityTickList();
	private final TransientEntitySectionManager<Entity> entityStorage = new TransientEntitySectionManager<>(Entity.class, new ClientLevel.EntityCallbacks());
	private final ClientPacketListener connection;
	private final LevelRenderer levelRenderer;
	private final LevelEventHandler levelEventHandler;
	private final ClientLevel.ClientLevelData clientLevelData;
	private final TickRateManager tickRateManager;
	private final @Nullable EndFlashState endFlashState;
	private final Minecraft minecraft = Minecraft.getInstance();
	final List<AbstractClientPlayer> players = Lists.newArrayList();
	final List<EnderDragonPart> dragonParts = Lists.newArrayList();
	private final Map<MapId, MapItemSavedData> mapData = Maps.newHashMap();
	private int skyFlashTime;
	private final Object2ObjectArrayMap<ColorResolver, BlockTintCache> tintCaches = Util.make(
		new Object2ObjectArrayMap<>(3),
		object2ObjectArrayMap -> {
			object2ObjectArrayMap.put(
				BiomeColors.GRASS_COLOR_RESOLVER, new BlockTintCache(blockPos -> this.calculateBlockTint(blockPos, BiomeColors.GRASS_COLOR_RESOLVER))
			);
			object2ObjectArrayMap.put(
				BiomeColors.FOLIAGE_COLOR_RESOLVER, new BlockTintCache(blockPos -> this.calculateBlockTint(blockPos, BiomeColors.FOLIAGE_COLOR_RESOLVER))
			);
			object2ObjectArrayMap.put(
				BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER, new BlockTintCache(blockPos -> this.calculateBlockTint(blockPos, BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER))
			);
			object2ObjectArrayMap.put(
				BiomeColors.WATER_COLOR_RESOLVER, new BlockTintCache(blockPos -> this.calculateBlockTint(blockPos, BiomeColors.WATER_COLOR_RESOLVER))
			);
		}
	);
	private final ClientChunkCache chunkSource;
	private final Deque<Runnable> lightUpdateQueue = Queues.newArrayDeque();
	private int serverSimulationDistance;
	private final BlockStatePredictionHandler blockStatePredictionHandler = new BlockStatePredictionHandler();
	private final Set<BlockEntity> globallyRenderedBlockEntities = new ReferenceOpenHashSet<>();
	private final ClientExplosionTracker explosionTracker = new ClientExplosionTracker();
	private final WorldBorder worldBorder = new WorldBorder();
	private final EnvironmentAttributeSystem environmentAttributes;
	private final int seaLevel;
	private boolean tickDayTime;
	private static final Set<Item> MARKER_PARTICLE_ITEMS = Set.of(Items.BARRIER, Items.LIGHT);

	public void handleBlockChangedAck(int i) {
		if (SharedConstants.DEBUG_BLOCK_BREAK) {
			LOGGER.debug("ACK {}", i);
		}

		this.blockStatePredictionHandler.endPredictionsUpTo(i, this);
	}

	@Override
	public void onBlockEntityAdded(BlockEntity blockEntity) {
		BlockEntityRenderer<BlockEntity, ?> blockEntityRenderer = this.minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
		if (blockEntityRenderer != null && blockEntityRenderer.shouldRenderOffScreen()) {
			this.globallyRenderedBlockEntities.add(blockEntity);
		}
	}

	public Set<BlockEntity> getGloballyRenderedBlockEntities() {
		return this.globallyRenderedBlockEntities;
	}

	public void setServerVerifiedBlockState(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i) {
		if (!this.blockStatePredictionHandler.updateKnownServerState(blockPos, blockState)) {
			super.setBlock(blockPos, blockState, i, 512);
		}
	}

	public void syncBlockState(BlockPos blockPos, BlockState blockState, Vec3 vec3) {
		BlockState blockState2 = this.getBlockState(blockPos);
		if (blockState2 != blockState) {
			this.setBlock(blockPos, blockState, 19);
			Player player = this.minecraft.player;
			if (this == player.level() && player.isColliding(blockPos, blockState)) {
				player.absSnapTo(vec3.x, vec3.y, vec3.z);
			}
		}
	}

	BlockStatePredictionHandler getBlockStatePredictionHandler() {
		return this.blockStatePredictionHandler;
	}

	@Override
	public boolean setBlock(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i, int j) {
		if (this.blockStatePredictionHandler.isPredicting()) {
			BlockState blockState2 = this.getBlockState(blockPos);
			boolean bl = super.setBlock(blockPos, blockState, i, j);
			if (bl) {
				this.blockStatePredictionHandler.retainKnownServerState(blockPos, blockState2, this.minecraft.player);
			}

			return bl;
		} else {
			return super.setBlock(blockPos, blockState, i, j);
		}
	}

	public ClientLevel(
		ClientPacketListener clientPacketListener,
		ClientLevel.ClientLevelData clientLevelData,
		ResourceKey<Level> resourceKey,
		Holder<DimensionType> holder,
		int i,
		int j,
		LevelRenderer levelRenderer,
		boolean bl,
		long l,
		int k
	) {
		super(clientLevelData, resourceKey, clientPacketListener.registryAccess(), holder, true, bl, l, 1000000);
		this.connection = clientPacketListener;
		this.chunkSource = new ClientChunkCache(this, i);
		this.tickRateManager = new TickRateManager();
		this.clientLevelData = clientLevelData;
		this.levelRenderer = levelRenderer;
		this.seaLevel = k;
		this.levelEventHandler = new LevelEventHandler(this.minecraft, this);
		this.endFlashState = holder.value().hasEndFlashes() ? new EndFlashState() : null;
		this.setRespawnData(LevelData.RespawnData.of(resourceKey, new BlockPos(8, 64, 8), 0.0F, 0.0F));
		this.serverSimulationDistance = j;
		this.environmentAttributes = this.addEnvironmentAttributeLayers(EnvironmentAttributeSystem.builder()).build();
		this.updateSkyBrightness();
		if (this.canHaveWeather()) {
			this.prepareWeather();
		}
	}

	private EnvironmentAttributeSystem.Builder addEnvironmentAttributeLayers(EnvironmentAttributeSystem.Builder builder) {
		builder.addDefaultLayers(this);
		int i = ARGB.color(204, 204, 255);
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (integer, j) -> this.getSkyFlashTime() > 0 ? ARGB.srgbLerp(0.22F, integer, i) : integer);
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (float_, ix) -> this.getSkyFlashTime() > 0 ? 1.0F : float_);
		return builder;
	}

	public void queueLightUpdate(Runnable runnable) {
		this.lightUpdateQueue.add(runnable);
	}

	public void pollLightUpdates() {
		int i = this.lightUpdateQueue.size();
		int j = i < 1000 ? Math.max(10, i / 10) : i;

		for (int k = 0; k < j; k++) {
			Runnable runnable = this.lightUpdateQueue.poll();
			if (runnable == null) {
				break;
			}

			runnable.run();
		}
	}

	public @Nullable EndFlashState endFlashState() {
		return this.endFlashState;
	}

	public void tick(BooleanSupplier booleanSupplier) {
		this.updateSkyBrightness();
		if (this.tickRateManager().runsNormally()) {
			this.getWorldBorder().tick();
			this.tickTime();
		}

		if (this.skyFlashTime > 0) {
			this.setSkyFlashTime(this.skyFlashTime - 1);
		}

		if (this.endFlashState != null) {
			this.endFlashState.tick(this.getGameTime());
			if (this.endFlashState.flashStartedThisTick() && !(this.minecraft.screen instanceof WinScreen)) {
				this.minecraft
					.getSoundManager()
					.playDelayed(
						new DirectionalSoundInstance(
							SoundEvents.WEATHER_END_FLASH,
							SoundSource.WEATHER,
							this.random,
							this.minecraft.gameRenderer.getMainCamera(),
							this.endFlashState.getXAngle(),
							this.endFlashState.getYAngle()
						),
						30
					);
			}
		}

		this.explosionTracker.tick(this);

		try (Zone zone = Profiler.get().zone("blocks")) {
			this.chunkSource.tick(booleanSupplier, true);
		}

		JvmProfiler.INSTANCE.onClientTick(this.minecraft.getFps());
		this.environmentAttributes().invalidateTickCache();
	}

	private void tickTime() {
		this.clientLevelData.setGameTime(this.clientLevelData.getGameTime() + 1L);
		if (this.tickDayTime) {
			this.clientLevelData.setDayTime(this.clientLevelData.getDayTime() + 1L);
		}
	}

	public void setTimeFromServer(long l, long m, boolean bl) {
		this.clientLevelData.setGameTime(l);
		this.clientLevelData.setDayTime(m);
		this.tickDayTime = bl;
	}

	public Iterable<Entity> entitiesForRendering() {
		return this.getEntities().getAll();
	}

	public void tickEntities() {
		this.tickingEntities.forEach(entity -> {
			if (!entity.isRemoved() && !entity.isPassenger() && !this.tickRateManager.isEntityFrozen(entity)) {
				this.guardEntityTick(this::tickNonPassenger, entity);
			}
		});
	}

	public boolean isTickingEntity(Entity entity) {
		return this.tickingEntities.contains(entity);
	}

	@Override
	public boolean shouldTickDeath(Entity entity) {
		return entity.chunkPosition().getChessboardDistance(this.minecraft.player.chunkPosition()) <= this.serverSimulationDistance;
	}

	public void tickNonPassenger(Entity entity) {
		entity.setOldPosAndRot();
		entity.tickCount++;
		Profiler.get().push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
		entity.tick();
		Profiler.get().pop();

		for (Entity entity2 : entity.getPassengers()) {
			this.tickPassenger(entity, entity2);
		}
	}

	private void tickPassenger(Entity entity, Entity entity2) {
		if (entity2.isRemoved() || entity2.getVehicle() != entity) {
			entity2.stopRiding();
		} else if (entity2 instanceof Player || this.tickingEntities.contains(entity2)) {
			entity2.setOldPosAndRot();
			entity2.tickCount++;
			entity2.rideTick();

			for (Entity entity3 : entity2.getPassengers()) {
				this.tickPassenger(entity2, entity3);
			}
		}
	}

	public void unload(LevelChunk levelChunk) {
		levelChunk.clearAllBlockEntities();
		this.chunkSource.getLightEngine().setLightEnabled(levelChunk.getPos(), false);
		this.entityStorage.stopTicking(levelChunk.getPos());
	}

	public void onChunkLoaded(ChunkPos chunkPos) {
		this.tintCaches.forEach((colorResolver, blockTintCache) -> blockTintCache.invalidateForChunk(chunkPos.x, chunkPos.z));
		this.entityStorage.startTicking(chunkPos);
	}

	public void onSectionBecomingNonEmpty(long l) {
		this.levelRenderer.onSectionBecomingNonEmpty(l);
	}

	public void clearTintCaches() {
		this.tintCaches.forEach((colorResolver, blockTintCache) -> blockTintCache.invalidateAll());
	}

	@Override
	public boolean hasChunk(int i, int j) {
		return true;
	}

	public int getEntityCount() {
		return this.entityStorage.count();
	}

	public void addEntity(Entity entity) {
		this.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
		this.entityStorage.addEntity(entity);
	}

	public void removeEntity(int i, Entity.RemovalReason removalReason) {
		Entity entity = this.getEntities().get(i);
		if (entity != null) {
			entity.setRemoved(removalReason);
			entity.onClientRemoval();
		}
	}

	@Override
	public List<Entity> getPushableEntities(Entity entity, AABB aABB) {
		LocalPlayer localPlayer = this.minecraft.player;
		return localPlayer != null && localPlayer != entity && localPlayer.getBoundingBox().intersects(aABB) && EntitySelector.pushableBy(entity).test(localPlayer)
			? List.of(localPlayer)
			: List.of();
	}

	@Override
	public @Nullable Entity getEntity(int i) {
		return this.getEntities().get(i);
	}

	public void disconnect(Component component) {
		this.connection.getConnection().disconnect(component);
	}

	public void animateTick(int i, int j, int k) {
		int l = 32;
		RandomSource randomSource = RandomSource.create();
		Block block = this.getMarkerParticleTarget();
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (int m = 0; m < 667; m++) {
			this.doAnimateTick(i, j, k, 16, randomSource, block, mutableBlockPos);
			this.doAnimateTick(i, j, k, 32, randomSource, block, mutableBlockPos);
		}
	}

	private @Nullable Block getMarkerParticleTarget() {
		if (this.minecraft.gameMode.getPlayerMode() == GameType.CREATIVE) {
			ItemStack itemStack = this.minecraft.player.getMainHandItem();
			Item item = itemStack.getItem();
			if (MARKER_PARTICLE_ITEMS.contains(item) && item instanceof BlockItem blockItem) {
				return blockItem.getBlock();
			}
		}

		return null;
	}

	public void doAnimateTick(int i, int j, int k, int l, RandomSource randomSource, @Nullable Block block, BlockPos.MutableBlockPos mutableBlockPos) {
		int m = i + this.random.nextInt(l) - this.random.nextInt(l);
		int n = j + this.random.nextInt(l) - this.random.nextInt(l);
		int o = k + this.random.nextInt(l) - this.random.nextInt(l);
		mutableBlockPos.set(m, n, o);
		BlockState blockState = this.getBlockState(mutableBlockPos);
		blockState.getBlock().animateTick(blockState, this, mutableBlockPos, randomSource);
		FluidState fluidState = this.getFluidState(mutableBlockPos);
		if (!fluidState.isEmpty()) {
			fluidState.animateTick(this, mutableBlockPos, randomSource);
			ParticleOptions particleOptions = fluidState.getDripParticle();
			if (particleOptions != null && this.random.nextInt(10) == 0) {
				boolean bl = blockState.isFaceSturdy(this, mutableBlockPos, Direction.DOWN);
				BlockPos blockPos = mutableBlockPos.below();
				this.trySpawnDripParticles(blockPos, this.getBlockState(blockPos), particleOptions, bl);
			}
		}

		if (block == blockState.getBlock()) {
			this.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, blockState), m + 0.5, n + 0.5, o + 0.5, 0.0, 0.0, 0.0);
		}

		if (!blockState.isCollisionShapeFullBlock(this, mutableBlockPos)) {
			for (AmbientParticle ambientParticle : this.environmentAttributes().getValue(EnvironmentAttributes.AMBIENT_PARTICLES, mutableBlockPos)) {
				if (ambientParticle.canSpawn(this.random)) {
					this.addParticle(
						ambientParticle.particle(),
						mutableBlockPos.getX() + this.random.nextDouble(),
						mutableBlockPos.getY() + this.random.nextDouble(),
						mutableBlockPos.getZ() + this.random.nextDouble(),
						0.0,
						0.0,
						0.0
					);
				}
			}
		}
	}

	private void trySpawnDripParticles(BlockPos blockPos, BlockState blockState, ParticleOptions particleOptions, boolean bl) {
		if (blockState.getFluidState().isEmpty()) {
			VoxelShape voxelShape = blockState.getCollisionShape(this, blockPos);
			double d = voxelShape.max(Direction.Axis.Y);
			if (d < 1.0) {
				if (bl) {
					this.spawnFluidParticle(blockPos.getX(), blockPos.getX() + 1, blockPos.getZ(), blockPos.getZ() + 1, blockPos.getY() + 1 - 0.05, particleOptions);
				}
			} else if (!blockState.is(BlockTags.IMPERMEABLE)) {
				double e = voxelShape.min(Direction.Axis.Y);
				if (e > 0.0) {
					this.spawnParticle(blockPos, particleOptions, voxelShape, blockPos.getY() + e - 0.05);
				} else {
					BlockPos blockPos2 = blockPos.below();
					BlockState blockState2 = this.getBlockState(blockPos2);
					VoxelShape voxelShape2 = blockState2.getCollisionShape(this, blockPos2);
					double f = voxelShape2.max(Direction.Axis.Y);
					if (f < 1.0 && blockState2.getFluidState().isEmpty()) {
						this.spawnParticle(blockPos, particleOptions, voxelShape, blockPos.getY() - 0.05);
					}
				}
			}
		}
	}

	private void spawnParticle(BlockPos blockPos, ParticleOptions particleOptions, VoxelShape voxelShape, double d) {
		this.spawnFluidParticle(
			blockPos.getX() + voxelShape.min(Direction.Axis.X),
			blockPos.getX() + voxelShape.max(Direction.Axis.X),
			blockPos.getZ() + voxelShape.min(Direction.Axis.Z),
			blockPos.getZ() + voxelShape.max(Direction.Axis.Z),
			d,
			particleOptions
		);
	}

	private void spawnFluidParticle(double d, double e, double f, double g, double h, ParticleOptions particleOptions) {
		this.addParticle(particleOptions, Mth.lerp(this.random.nextDouble(), d, e), h, Mth.lerp(this.random.nextDouble(), f, g), 0.0, 0.0, 0.0);
	}

	@Override
	public CrashReportCategory fillReportDetails(CrashReport crashReport) {
		CrashReportCategory crashReportCategory = super.fillReportDetails(crashReport);
		crashReportCategory.setDetail("Server brand", () -> this.minecraft.player.connection.serverBrand());
		crashReportCategory.setDetail(
			"Server type", () -> this.minecraft.getSingleplayerServer() == null ? "Non-integrated multiplayer server" : "Integrated singleplayer server"
		);
		crashReportCategory.setDetail("Tracked entity count", () -> String.valueOf(this.getEntityCount()));
		return crashReportCategory;
	}

	@Override
	public void playSeededSound(
		@Nullable Entity entity, double d, double e, double f, Holder<SoundEvent> holder, SoundSource soundSource, float g, float h, long l
	) {
		if (entity == this.minecraft.player) {
			this.playSound(d, e, f, holder.value(), soundSource, g, h, false, l);
		}
	}

	@Override
	public void playSeededSound(@Nullable Entity entity, Entity entity2, Holder<SoundEvent> holder, SoundSource soundSource, float f, float g, long l) {
		if (entity == this.minecraft.player) {
			this.minecraft.getSoundManager().play(new EntityBoundSoundInstance(holder.value(), soundSource, f, g, entity2, l));
		}
	}

	@Override
	public void playLocalSound(Entity entity, SoundEvent soundEvent, SoundSource soundSource, float f, float g) {
		this.minecraft.getSoundManager().play(new EntityBoundSoundInstance(soundEvent, soundSource, f, g, entity, this.random.nextLong()));
	}

	@Override
	public void playPlayerSound(SoundEvent soundEvent, SoundSource soundSource, float f, float g) {
		if (this.minecraft.player != null) {
			this.minecraft.getSoundManager().play(new EntityBoundSoundInstance(soundEvent, soundSource, f, g, this.minecraft.player, this.random.nextLong()));
		}
	}

	@Override
	public void playLocalSound(double d, double e, double f, SoundEvent soundEvent, SoundSource soundSource, float g, float h, boolean bl) {
		this.playSound(d, e, f, soundEvent, soundSource, g, h, bl, this.random.nextLong());
	}

	private void playSound(double d, double e, double f, SoundEvent soundEvent, SoundSource soundSource, float g, float h, boolean bl, long l) {
		double i = this.minecraft.gameRenderer.getMainCamera().position().distanceToSqr(d, e, f);
		SimpleSoundInstance simpleSoundInstance = new SimpleSoundInstance(soundEvent, soundSource, g, h, RandomSource.create(l), d, e, f);
		if (bl && i > 100.0) {
			double j = Math.sqrt(i) / 40.0;
			this.minecraft.getSoundManager().playDelayed(simpleSoundInstance, (int)(j * 20.0));
		} else {
			this.minecraft.getSoundManager().play(simpleSoundInstance);
		}
	}

	@Override
	public void createFireworks(double d, double e, double f, double g, double h, double i, List<FireworkExplosion> list) {
		if (list.isEmpty()) {
			for (int j = 0; j < this.random.nextInt(3) + 2; j++) {
				this.addParticle(ParticleTypes.POOF, d, e, f, this.random.nextGaussian() * 0.05, 0.005, this.random.nextGaussian() * 0.05);
			}
		} else {
			this.minecraft.particleEngine.add(new FireworkParticles.Starter(this, d, e, f, g, h, i, this.minecraft.particleEngine, list));
		}
	}

	@Override
	public void sendPacketToServer(Packet<?> packet) {
		this.connection.send(packet);
	}

	@Override
	public WorldBorder getWorldBorder() {
		return this.worldBorder;
	}

	@Override
	public RecipeAccess recipeAccess() {
		return this.connection.recipes();
	}

	@Override
	public TickRateManager tickRateManager() {
		return this.tickRateManager;
	}

	@Override
	public EnvironmentAttributeSystem environmentAttributes() {
		return this.environmentAttributes;
	}

	@Override
	public LevelTickAccess<Block> getBlockTicks() {
		return BlackholeTickAccess.emptyLevelList();
	}

	@Override
	public LevelTickAccess<Fluid> getFluidTicks() {
		return BlackholeTickAccess.emptyLevelList();
	}

	public ClientChunkCache getChunkSource() {
		return this.chunkSource;
	}

	@Override
	public @Nullable MapItemSavedData getMapData(MapId mapId) {
		return this.mapData.get(mapId);
	}

	public void overrideMapData(MapId mapId, MapItemSavedData mapItemSavedData) {
		this.mapData.put(mapId, mapItemSavedData);
	}

	@Override
	public Scoreboard getScoreboard() {
		return this.connection.scoreboard();
	}

	@Override
	public void sendBlockUpdated(BlockPos blockPos, BlockState blockState, BlockState blockState2, @Block.UpdateFlags int i) {
		this.levelRenderer.blockChanged(this, blockPos, blockState, blockState2, i);
	}

	@Override
	public void setBlocksDirty(BlockPos blockPos, BlockState blockState, BlockState blockState2) {
		this.levelRenderer.setBlockDirty(blockPos, blockState, blockState2);
	}

	public void setSectionDirtyWithNeighbors(int i, int j, int k) {
		this.levelRenderer.setSectionDirtyWithNeighbors(i, j, k);
	}

	public void setSectionRangeDirty(int i, int j, int k, int l, int m, int n) {
		this.levelRenderer.setSectionRangeDirty(i, j, k, l, m, n);
	}

	@Override
	public void destroyBlockProgress(int i, BlockPos blockPos, int j) {
		this.levelRenderer.destroyBlockProgress(i, blockPos, j);
	}

	@Override
	public void globalLevelEvent(int i, BlockPos blockPos, int j) {
		this.levelEventHandler.globalLevelEvent(i, blockPos, j);
	}

	@Override
	public void levelEvent(@Nullable Entity entity, int i, BlockPos blockPos, int j) {
		try {
			this.levelEventHandler.levelEvent(i, blockPos, j);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Playing level event");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Level event being played");
			crashReportCategory.setDetail("Block coordinates", CrashReportCategory.formatLocation(this, blockPos));
			crashReportCategory.setDetail("Event source", entity);
			crashReportCategory.setDetail("Event type", i);
			crashReportCategory.setDetail("Event data", j);
			throw new ReportedException(crashReport);
		}
	}

	@Override
	public void addParticle(ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i) {
		this.doAddParticle(particleOptions, particleOptions.getType().getOverrideLimiter(), false, d, e, f, g, h, i);
	}

	@Override
	public void addParticle(ParticleOptions particleOptions, boolean bl, boolean bl2, double d, double e, double f, double g, double h, double i) {
		this.doAddParticle(particleOptions, particleOptions.getType().getOverrideLimiter() || bl, bl2, d, e, f, g, h, i);
	}

	@Override
	public void addAlwaysVisibleParticle(ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i) {
		this.doAddParticle(particleOptions, false, true, d, e, f, g, h, i);
	}

	@Override
	public void addAlwaysVisibleParticle(ParticleOptions particleOptions, boolean bl, double d, double e, double f, double g, double h, double i) {
		this.doAddParticle(particleOptions, particleOptions.getType().getOverrideLimiter() || bl, true, d, e, f, g, h, i);
	}

	private void doAddParticle(ParticleOptions particleOptions, boolean bl, boolean bl2, double d, double e, double f, double g, double h, double i) {
		try {
			Camera camera = this.minecraft.gameRenderer.getMainCamera();
			ParticleStatus particleStatus = this.calculateParticleLevel(bl2);
			if (bl) {
				this.minecraft.particleEngine.createParticle(particleOptions, d, e, f, g, h, i);
			} else if (!(camera.position().distanceToSqr(d, e, f) > 1024.0)) {
				if (particleStatus != ParticleStatus.MINIMAL) {
					this.minecraft.particleEngine.createParticle(particleOptions, d, e, f, g, h, i);
				}
			}
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception while adding particle");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Particle being added");
			crashReportCategory.setDetail("ID", BuiltInRegistries.PARTICLE_TYPE.getKey(particleOptions.getType()));
			crashReportCategory.setDetail(
				"Parameters", () -> ParticleTypes.CODEC.encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), particleOptions).toString()
			);
			crashReportCategory.setDetail("Position", () -> CrashReportCategory.formatLocation(this, d, e, f));
			throw new ReportedException(crashReport);
		}
	}

	private ParticleStatus calculateParticleLevel(boolean bl) {
		ParticleStatus particleStatus = this.minecraft.options.particles().get();
		if (bl && particleStatus == ParticleStatus.MINIMAL && this.random.nextInt(10) == 0) {
			particleStatus = ParticleStatus.DECREASED;
		}

		if (particleStatus == ParticleStatus.DECREASED && this.random.nextInt(3) == 0) {
			particleStatus = ParticleStatus.MINIMAL;
		}

		return particleStatus;
	}

	@Override
	public List<AbstractClientPlayer> players() {
		return this.players;
	}

	public List<EnderDragonPart> dragonParts() {
		return this.dragonParts;
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int i, int j, int k) {
		return this.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
	}

	private int getSkyFlashTime() {
		return this.minecraft.options.hideLightningFlash().get() ? 0 : this.skyFlashTime;
	}

	@Override
	public void setSkyFlashTime(int i) {
		this.skyFlashTime = i;
	}

	@Override
	public float getShade(Direction direction, boolean bl) {
		DimensionType.CardinalLightType cardinalLightType = this.dimensionType().cardinalLightType();
		if (!bl) {
			return cardinalLightType == DimensionType.CardinalLightType.NETHER ? 0.9F : 1.0F;
		}

		return switch (direction) {
			case DOWN -> cardinalLightType == DimensionType.CardinalLightType.NETHER ? 0.9F : 0.5F;
			case UP -> cardinalLightType == DimensionType.CardinalLightType.NETHER ? 0.9F : 1.0F;
			case NORTH, SOUTH -> 0.8F;
			case WEST, EAST -> 0.6F;
		};
	}

	@Override
	public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
		BlockTintCache blockTintCache = this.tintCaches.get(colorResolver);
		return blockTintCache.getColor(blockPos);
	}

	public int calculateBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
		int i = Minecraft.getInstance().options.biomeBlendRadius().get();
		if (i == 0) {
			return colorResolver.getColor(this.getBiome(blockPos).value(), blockPos.getX(), blockPos.getZ());
		}

		int j = (i * 2 + 1) * (i * 2 + 1);
		int k = 0;
		int l = 0;
		int m = 0;
		Cursor3D cursor3D = new Cursor3D(blockPos.getX() - i, blockPos.getY(), blockPos.getZ() - i, blockPos.getX() + i, blockPos.getY(), blockPos.getZ() + i);
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		while (cursor3D.advance()) {
			mutableBlockPos.set(cursor3D.nextX(), cursor3D.nextY(), cursor3D.nextZ());
			int n = colorResolver.getColor(this.getBiome(mutableBlockPos).value(), mutableBlockPos.getX(), mutableBlockPos.getZ());
			k += (n & 0xFF0000) >> 16;
			l += (n & 0xFF00) >> 8;
			m += n & 0xFF;
		}

		return (k / j & 0xFF) << 16 | (l / j & 0xFF) << 8 | m / j & 0xFF;
	}

	@Override
	public void setRespawnData(LevelData.RespawnData respawnData) {
		this.levelData.setSpawn(this.getWorldBorderAdjustedRespawnData(respawnData));
	}

	@Override
	public LevelData.RespawnData getRespawnData() {
		return this.levelData.getRespawnData();
	}

	@Override
	public String toString() {
		return "ClientLevel";
	}

	public ClientLevel.ClientLevelData getLevelData() {
		return this.clientLevelData;
	}

	@Override
	public void gameEvent(Holder<GameEvent> holder, Vec3 vec3, GameEvent.Context context) {
	}

	protected Map<MapId, MapItemSavedData> getAllMapData() {
		return ImmutableMap.copyOf(this.mapData);
	}

	protected void addMapData(Map<MapId, MapItemSavedData> map) {
		this.mapData.putAll(map);
	}

	@Override
	protected LevelEntityGetter<Entity> getEntities() {
		return this.entityStorage.getEntityGetter();
	}

	@Override
	public String gatherChunkSourceStats() {
		return "Chunks[C] W: " + this.chunkSource.gatherStats() + " E: " + this.entityStorage.gatherStats();
	}

	@Override
	public void addDestroyBlockEffect(BlockPos blockPos, BlockState blockState) {
		if (!blockState.isAir() && blockState.shouldSpawnTerrainParticles()) {
			VoxelShape voxelShape = blockState.getShape(this, blockPos);
			double d = 0.25;
			voxelShape.forAllBoxes(
				(dx, e, f, g, h, i) -> {
					double j = Math.min(1.0, g - dx);
					double k = Math.min(1.0, h - e);
					double l = Math.min(1.0, i - f);
					int m = Math.max(2, Mth.ceil(j / 0.25));
					int n = Math.max(2, Mth.ceil(k / 0.25));
					int o = Math.max(2, Mth.ceil(l / 0.25));

					for (int p = 0; p < m; p++) {
						for (int q = 0; q < n; q++) {
							for (int r = 0; r < o; r++) {
								double s = (p + 0.5) / m;
								double t = (q + 0.5) / n;
								double u = (r + 0.5) / o;
								double v = s * j + dx;
								double w = t * k + e;
								double x = u * l + f;
								this.minecraft
									.particleEngine
									.add(new TerrainParticle(this, blockPos.getX() + v, blockPos.getY() + w, blockPos.getZ() + x, s - 0.5, t - 0.5, u - 0.5, blockState, blockPos));
							}
						}
					}
				}
			);
		}
	}

	public void addBreakingBlockEffect(BlockPos blockPos, Direction direction) {
		BlockState blockState = this.getBlockState(blockPos);
		if (blockState.getRenderShape() != RenderShape.INVISIBLE && blockState.shouldSpawnTerrainParticles()) {
			int i = blockPos.getX();
			int j = blockPos.getY();
			int k = blockPos.getZ();
			float f = 0.1F;
			AABB aABB = blockState.getShape(this, blockPos).bounds();
			double d = i + this.random.nextDouble() * (aABB.maxX - aABB.minX - 0.2F) + 0.1F + aABB.minX;
			double e = j + this.random.nextDouble() * (aABB.maxY - aABB.minY - 0.2F) + 0.1F + aABB.minY;
			double g = k + this.random.nextDouble() * (aABB.maxZ - aABB.minZ - 0.2F) + 0.1F + aABB.minZ;
			if (direction == Direction.DOWN) {
				e = j + aABB.minY - 0.1F;
			}

			if (direction == Direction.UP) {
				e = j + aABB.maxY + 0.1F;
			}

			if (direction == Direction.NORTH) {
				g = k + aABB.minZ - 0.1F;
			}

			if (direction == Direction.SOUTH) {
				g = k + aABB.maxZ + 0.1F;
			}

			if (direction == Direction.WEST) {
				d = i + aABB.minX - 0.1F;
			}

			if (direction == Direction.EAST) {
				d = i + aABB.maxX + 0.1F;
			}

			this.minecraft.particleEngine.add(new TerrainParticle(this, d, e, g, 0.0, 0.0, 0.0, blockState, blockPos).setPower(0.2F).scale(0.6F));
		}
	}

	public void setServerSimulationDistance(int i) {
		this.serverSimulationDistance = i;
	}

	public int getServerSimulationDistance() {
		return this.serverSimulationDistance;
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return this.connection.enabledFeatures();
	}

	@Override
	public PotionBrewing potionBrewing() {
		return this.connection.potionBrewing();
	}

	@Override
	public FuelValues fuelValues() {
		return this.connection.fuelValues();
	}

	@Override
	public void explode(
		@Nullable Entity entity,
		@Nullable DamageSource damageSource,
		@Nullable ExplosionDamageCalculator explosionDamageCalculator,
		double d,
		double e,
		double f,
		float g,
		boolean bl,
		Level.ExplosionInteraction explosionInteraction,
		ParticleOptions particleOptions,
		ParticleOptions particleOptions2,
		WeightedList<ExplosionParticleInfo> weightedList,
		Holder<SoundEvent> holder
	) {
	}

	@Override
	public int getSeaLevel() {
		return this.seaLevel;
	}

	@Override
	public int getClientLeafTintColor(BlockPos blockPos) {
		return Minecraft.getInstance().getBlockColors().getColor(this.getBlockState(blockPos), this, blockPos, 0);
	}

	@Override
	public void registerForCleaning(CacheSlot<ClientLevel, ?> cacheSlot) {
		this.connection.registerForCleaning(cacheSlot);
	}

	public void trackExplosionEffects(Vec3 vec3, float f, int i, WeightedList<ExplosionParticleInfo> weightedList) {
		this.explosionTracker.track(vec3, f, i, weightedList);
	}

	@Environment(EnvType.CLIENT)
	public static class ClientLevelData implements WritableLevelData {
		private final boolean hardcore;
		private final boolean isFlat;
		private LevelData.RespawnData respawnData;
		private long gameTime;
		private long dayTime;
		private boolean raining;
		private Difficulty difficulty;
		private boolean difficultyLocked;

		public ClientLevelData(Difficulty difficulty, boolean bl, boolean bl2) {
			this.difficulty = difficulty;
			this.hardcore = bl;
			this.isFlat = bl2;
		}

		@Override
		public LevelData.RespawnData getRespawnData() {
			return this.respawnData;
		}

		@Override
		public long getGameTime() {
			return this.gameTime;
		}

		@Override
		public long getDayTime() {
			return this.dayTime;
		}

		public void setGameTime(long l) {
			this.gameTime = l;
		}

		public void setDayTime(long l) {
			this.dayTime = l;
		}

		@Override
		public void setSpawn(LevelData.RespawnData respawnData) {
			this.respawnData = respawnData;
		}

		@Override
		public boolean isThundering() {
			return false;
		}

		@Override
		public boolean isRaining() {
			return this.raining;
		}

		@Override
		public void setRaining(boolean bl) {
			this.raining = bl;
		}

		@Override
		public boolean isHardcore() {
			return this.hardcore;
		}

		@Override
		public Difficulty getDifficulty() {
			return this.difficulty;
		}

		@Override
		public boolean isDifficultyLocked() {
			return this.difficultyLocked;
		}

		@Override
		public void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor levelHeightAccessor) {
			WritableLevelData.super.fillCrashReportCategory(crashReportCategory, levelHeightAccessor);
		}

		public void setDifficulty(Difficulty difficulty) {
			this.difficulty = difficulty;
		}

		public void setDifficultyLocked(boolean bl) {
			this.difficultyLocked = bl;
		}

		public double getHorizonHeight(LevelHeightAccessor levelHeightAccessor) {
			return this.isFlat ? levelHeightAccessor.getMinY() : 63.0;
		}

		public float voidDarknessOnsetRange() {
			return this.isFlat ? 1.0F : 32.0F;
		}
	}

	@Environment(EnvType.CLIENT)
	final class EntityCallbacks implements LevelCallback<Entity> {
		public void onCreated(Entity entity) {
		}

		public void onDestroyed(Entity entity) {
		}

		public void onTickingStart(Entity entity) {
			ClientLevel.this.tickingEntities.add(entity);
		}

		public void onTickingEnd(Entity entity) {
			ClientLevel.this.tickingEntities.remove(entity);
		}

		public void onTrackingStart(Entity entity) {
			switch (entity) {
				case AbstractClientPlayer abstractClientPlayer:
					ClientLevel.this.players.add(abstractClientPlayer);
					break;
				case EnderDragon enderDragon:
					ClientLevel.this.dragonParts.addAll(Arrays.asList(enderDragon.getSubEntities()));
					break;
				default:
			}
		}

		public void onTrackingEnd(Entity entity) {
			entity.unRide();
			switch (entity) {
				case AbstractClientPlayer abstractClientPlayer:
					ClientLevel.this.players.remove(abstractClientPlayer);
					break;
				case EnderDragon enderDragon:
					ClientLevel.this.dragonParts.removeAll(Arrays.asList(enderDragon.getSubEntities()));
					break;
				default:
			}
		}

		public void onSectionChange(Entity entity) {
		}
	}
}
