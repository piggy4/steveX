package net.minecraft.util;

import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.SuppressForbidden;

public class DelegateDataOutput implements DataOutput {
	private final DataOutput parent;

	public DelegateDataOutput(DataOutput dataOutput) {
		this.parent = dataOutput;
	}

	@Override
	public void write(int i) throws IOException {
		this.parent.write(i);
	}

	@Override
	public void write(byte[] bs) throws IOException {
		this.parent.write(bs);
	}

	@Override
	public void write(byte[] bs, int i, int j) throws IOException {
		this.parent.write(bs, i, j);
	}

	@Override
	public void writeBoolean(boolean bl) throws IOException {
		this.parent.writeBoolean(bl);
	}

	@Override
	public void writeByte(int i) throws IOException {
		this.parent.writeByte(i);
	}

	@Override
	public void writeShort(int i) throws IOException {
		this.parent.writeShort(i);
	}

	@Override
	public void writeChar(int i) throws IOException {
		this.parent.writeChar(i);
	}

	@Override
	public void writeInt(int i) throws IOException {
		this.parent.writeInt(i);
	}

	@Override
	public void writeLong(long l) throws IOException {
		this.parent.writeLong(l);
	}

	@Override
	public void writeFloat(float f) throws IOException {
		this.parent.writeFloat(f);
	}

	@Override
	public void writeDouble(double d) throws IOException {
		this.parent.writeDouble(d);
	}

	@SuppressForbidden(reason = "Delegation is not use")
	@Override
	public void writeBytes(String string) throws IOException {
		this.parent.writeBytes(string);
	}

	@Override
	public void writeChars(String string) throws IOException {
		this.parent.writeChars(string);
	}

	@Override
	public void writeUTF(String string) throws IOException {
		this.parent.writeUTF(string);
	}
}
