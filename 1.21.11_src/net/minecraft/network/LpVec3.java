package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class LpVec3 {
	private static final int DATA_BITS = 15;
	private static final int DATA_BITS_MASK = 32767;
	private static final double MAX_QUANTIZED_VALUE = 32766.0;
	private static final int SCALE_BITS = 2;
	private static final int SCALE_BITS_MASK = 3;
	private static final int CONTINUATION_FLAG = 4;
	private static final int X_OFFSET = 3;
	private static final int Y_OFFSET = 18;
	private static final int Z_OFFSET = 33;
	public static final double ABS_MAX_VALUE = 1.7179869183E10;
	public static final double ABS_MIN_VALUE = 3.051944088384301E-5;

	public static boolean hasContinuationBit(int i) {
		return (i & 4) == 4;
	}

	public static Vec3 read(ByteBuf byteBuf) {
		int i = byteBuf.readUnsignedByte();
		if (i == 0) {
			return Vec3.ZERO;
		}

		int j = byteBuf.readUnsignedByte();
		long l = byteBuf.readUnsignedInt();
		long m = l << 16 | j << 8 | i;
		long n = i & 3;
		if (hasContinuationBit(i)) {
			n |= (VarInt.read(byteBuf) & 4294967295L) << 2;
		}

		return new Vec3(unpack(m >> 3) * n, unpack(m >> 18) * n, unpack(m >> 33) * n);
	}

	public static void write(ByteBuf byteBuf, Vec3 vec3) {
		double d = sanitize(vec3.x);
		double e = sanitize(vec3.y);
		double f = sanitize(vec3.z);
		double g = Mth.absMax(d, Mth.absMax(e, f));
		if (g < 3.051944088384301E-5) {
			byteBuf.writeByte(0);
		} else {
			long l = Mth.ceilLong(g);
			boolean bl = (l & 3L) != l;
			long m = bl ? l & 3L | 4L : l;
			long n = pack(d / l) << 3;
			long o = pack(e / l) << 18;
			long p = pack(f / l) << 33;
			long q = m | n | o | p;
			byteBuf.writeByte((byte)q);
			byteBuf.writeByte((byte)(q >> 8));
			byteBuf.writeInt((int)(q >> 16));
			if (bl) {
				VarInt.write(byteBuf, (int)(l >> 2));
			}
		}
	}

	private static double sanitize(double d) {
		return Double.isNaN(d) ? 0.0 : Math.clamp(d, -1.7179869183E10, 1.7179869183E10);
	}

	private static long pack(double d) {
		return Math.round((d * 0.5 + 0.5) * 32766.0);
	}

	private static double unpack(long l) {
		return Math.min(l & 32767L, 32766.0) * 2.0 / 32766.0 - 1.0;
	}
}
