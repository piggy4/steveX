package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

public class SimulationChunkTracker extends ChunkTracker {
	public static final int MAX_LEVEL = 33;
	protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
	private final TicketStorage ticketStorage;

	public SimulationChunkTracker(TicketStorage ticketStorage) {
		super(34, 16, 256);
		this.ticketStorage = ticketStorage;
		ticketStorage.setSimulationChunkUpdatedListener(this::update);
		this.chunks.defaultReturnValue((byte)33);
	}

	@Override
	protected int getLevelFromSource(long l) {
		return this.ticketStorage.getTicketLevelAt(l, true);
	}

	public int getLevel(ChunkPos chunkPos) {
		return this.getLevel(chunkPos.toLong());
	}

	@Override
	protected int getLevel(long l) {
		return this.chunks.get(l);
	}

	@Override
	protected void setLevel(long l, int i) {
		if (i >= 33) {
			this.chunks.remove(l);
		} else {
			this.chunks.put(l, (byte)i);
		}
	}

	public void runAllUpdates() {
		this.runUpdates(Integer.MAX_VALUE);
	}
}
