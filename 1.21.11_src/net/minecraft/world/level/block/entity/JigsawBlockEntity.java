package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class JigsawBlockEntity extends BlockEntity {
	public static final Codec<ResourceKey<StructureTemplatePool>> POOL_CODEC = ResourceKey.codec(Registries.TEMPLATE_POOL);
	public static final Identifier EMPTY_ID = Identifier.withDefaultNamespace("empty");
	private static final int DEFAULT_PLACEMENT_PRIORITY = 0;
	private static final int DEFAULT_SELECTION_PRIORITY = 0;
	public static final String TARGET = "target";
	public static final String POOL = "pool";
	public static final String JOINT = "joint";
	public static final String PLACEMENT_PRIORITY = "placement_priority";
	public static final String SELECTION_PRIORITY = "selection_priority";
	public static final String NAME = "name";
	public static final String FINAL_STATE = "final_state";
	public static final String DEFAULT_FINAL_STATE = "minecraft:air";
	private Identifier name = EMPTY_ID;
	private Identifier target = EMPTY_ID;
	private ResourceKey<StructureTemplatePool> pool = Pools.EMPTY;
	private JigsawBlockEntity.JointType joint = JigsawBlockEntity.JointType.ROLLABLE;
	private String finalState = "minecraft:air";
	private int placementPriority = 0;
	private int selectionPriority = 0;

	public JigsawBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.JIGSAW, blockPos, blockState);
	}

	public Identifier getName() {
		return this.name;
	}

	public Identifier getTarget() {
		return this.target;
	}

	public ResourceKey<StructureTemplatePool> getPool() {
		return this.pool;
	}

	public String getFinalState() {
		return this.finalState;
	}

	public JigsawBlockEntity.JointType getJoint() {
		return this.joint;
	}

	public int getPlacementPriority() {
		return this.placementPriority;
	}

	public int getSelectionPriority() {
		return this.selectionPriority;
	}

	public void setName(Identifier identifier) {
		this.name = identifier;
	}

	public void setTarget(Identifier identifier) {
		this.target = identifier;
	}

	public void setPool(ResourceKey<StructureTemplatePool> resourceKey) {
		this.pool = resourceKey;
	}

	public void setFinalState(String string) {
		this.finalState = string;
	}

	public void setJoint(JigsawBlockEntity.JointType jointType) {
		this.joint = jointType;
	}

	public void setPlacementPriority(int i) {
		this.placementPriority = i;
	}

	public void setSelectionPriority(int i) {
		this.selectionPriority = i;
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.store("name", Identifier.CODEC, this.name);
		valueOutput.store("target", Identifier.CODEC, this.target);
		valueOutput.store("pool", POOL_CODEC, this.pool);
		valueOutput.putString("final_state", this.finalState);
		valueOutput.store("joint", JigsawBlockEntity.JointType.CODEC, this.joint);
		valueOutput.putInt("placement_priority", this.placementPriority);
		valueOutput.putInt("selection_priority", this.selectionPriority);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.name = valueInput.read("name", Identifier.CODEC).orElse(EMPTY_ID);
		this.target = valueInput.read("target", Identifier.CODEC).orElse(EMPTY_ID);
		this.pool = valueInput.read("pool", POOL_CODEC).orElse(Pools.EMPTY);
		this.finalState = valueInput.getStringOr("final_state", "minecraft:air");
		this.joint = valueInput.read("joint", JigsawBlockEntity.JointType.CODEC).orElseGet(() -> StructureTemplate.getDefaultJointType(this.getBlockState()));
		this.placementPriority = valueInput.getIntOr("placement_priority", 0);
		this.selectionPriority = valueInput.getIntOr("selection_priority", 0);
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	public void generate(ServerLevel serverLevel, int i, boolean bl) {
		BlockPos blockPos = this.getBlockPos().relative(this.getBlockState().getValue(JigsawBlock.ORIENTATION).front());
		Registry<StructureTemplatePool> registry = serverLevel.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		Holder<StructureTemplatePool> holder = registry.getOrThrow(this.pool);
		JigsawPlacement.generateJigsaw(serverLevel, holder, this.target, i, blockPos, bl);
	}

	public enum JointType implements StringRepresentable {
		ROLLABLE("rollable"),
		ALIGNED("aligned");

		public static final StringRepresentable.EnumCodec<JigsawBlockEntity.JointType> CODEC = StringRepresentable.fromEnum(JigsawBlockEntity.JointType::values);
		private final String name;

		JointType(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public Component getTranslatedName() {
			return Component.translatable("jigsaw_block.joint." + this.name);
		}
	}
}
