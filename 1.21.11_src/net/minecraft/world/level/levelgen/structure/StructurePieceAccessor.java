package net.minecraft.world.level.levelgen.structure;

import org.jspecify.annotations.Nullable;

public interface StructurePieceAccessor {
	void addPiece(StructurePiece structurePiece);

	@Nullable StructurePiece findCollisionPiece(BoundingBox boundingBox);
}
