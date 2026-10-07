package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public record WorldCoordinates(WorldCoordinate x, WorldCoordinate y, WorldCoordinate z) implements Coordinates {
	public static final WorldCoordinates ZERO_ROTATION = absolute(new Vec2(0.0F, 0.0F));

	@Override
	public Vec3 getPosition(CommandSourceStack commandSourceStack) {
		Vec3 vec3 = commandSourceStack.getPosition();
		return new Vec3(this.x.get(vec3.x), this.y.get(vec3.y), this.z.get(vec3.z));
	}

	@Override
	public Vec2 getRotation(CommandSourceStack commandSourceStack) {
		Vec2 vec2 = commandSourceStack.getRotation();
		return new Vec2((float)this.x.get(vec2.x), (float)this.y.get(vec2.y));
	}

	@Override
	public boolean isXRelative() {
		return this.x.isRelative();
	}

	@Override
	public boolean isYRelative() {
		return this.y.isRelative();
	}

	@Override
	public boolean isZRelative() {
		return this.z.isRelative();
	}

	public static WorldCoordinates parseInt(StringReader stringReader) throws CommandSyntaxException {
		int i = stringReader.getCursor();
		WorldCoordinate worldCoordinate = WorldCoordinate.parseInt(stringReader);
		if (stringReader.canRead() && stringReader.peek() == ' ') {
			stringReader.skip();
			WorldCoordinate worldCoordinate2 = WorldCoordinate.parseInt(stringReader);
			if (stringReader.canRead() && stringReader.peek() == ' ') {
				stringReader.skip();
				WorldCoordinate worldCoordinate3 = WorldCoordinate.parseInt(stringReader);
				return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
			} else {
				stringReader.setCursor(i);
				throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
			}
		} else {
			stringReader.setCursor(i);
			throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
		}
	}

	public static WorldCoordinates parseDouble(StringReader stringReader, boolean bl) throws CommandSyntaxException {
		int i = stringReader.getCursor();
		WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(stringReader, bl);
		if (stringReader.canRead() && stringReader.peek() == ' ') {
			stringReader.skip();
			WorldCoordinate worldCoordinate2 = WorldCoordinate.parseDouble(stringReader, false);
			if (stringReader.canRead() && stringReader.peek() == ' ') {
				stringReader.skip();
				WorldCoordinate worldCoordinate3 = WorldCoordinate.parseDouble(stringReader, bl);
				return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
			} else {
				stringReader.setCursor(i);
				throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
			}
		} else {
			stringReader.setCursor(i);
			throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
		}
	}

	public static WorldCoordinates absolute(double d, double e, double f) {
		return new WorldCoordinates(new WorldCoordinate(false, d), new WorldCoordinate(false, e), new WorldCoordinate(false, f));
	}

	public static WorldCoordinates absolute(Vec2 vec2) {
		return new WorldCoordinates(new WorldCoordinate(false, vec2.x), new WorldCoordinate(false, vec2.y), new WorldCoordinate(true, 0.0));
	}
}
