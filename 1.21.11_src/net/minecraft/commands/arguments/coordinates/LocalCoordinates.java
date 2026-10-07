package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public record LocalCoordinates(double left, double up, double forwards) implements Coordinates {
	public static final char PREFIX_LOCAL_COORDINATE = '^';

	@Override
	public Vec3 getPosition(CommandSourceStack commandSourceStack) {
		Vec3 vec3 = commandSourceStack.getAnchor().apply(commandSourceStack);
		return Vec3.applyLocalCoordinatesToRotation(commandSourceStack.getRotation(), new Vec3(this.left, this.up, this.forwards)).add(vec3.x, vec3.y, vec3.z);
	}

	@Override
	public Vec2 getRotation(CommandSourceStack commandSourceStack) {
		return Vec2.ZERO;
	}

	@Override
	public boolean isXRelative() {
		return true;
	}

	@Override
	public boolean isYRelative() {
		return true;
	}

	@Override
	public boolean isZRelative() {
		return true;
	}

	public static LocalCoordinates parse(StringReader stringReader) throws CommandSyntaxException {
		int i = stringReader.getCursor();
		double d = readDouble(stringReader, i);
		if (stringReader.canRead() && stringReader.peek() == ' ') {
			stringReader.skip();
			double e = readDouble(stringReader, i);
			if (stringReader.canRead() && stringReader.peek() == ' ') {
				stringReader.skip();
				double f = readDouble(stringReader, i);
				return new LocalCoordinates(d, e, f);
			} else {
				stringReader.setCursor(i);
				throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
			}
		} else {
			stringReader.setCursor(i);
			throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
		}
	}

	private static double readDouble(StringReader stringReader, int i) throws CommandSyntaxException {
		if (!stringReader.canRead()) {
			throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(stringReader);
		} else if (stringReader.peek() != '^') {
			stringReader.setCursor(i);
			throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(stringReader);
		} else {
			stringReader.skip();
			return stringReader.canRead() && stringReader.peek() != ' ' ? stringReader.readDouble() : 0.0;
		}
	}
}
