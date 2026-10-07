package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record BlockElement(
	Vector3fc from, Vector3fc to, Map<Direction, BlockElementFace> faces, @Nullable BlockElementRotation rotation, boolean shade, int lightEmission
) {
	private static final boolean DEFAULT_RESCALE = false;
	private static final float MIN_EXTENT = -16.0F;
	private static final float MAX_EXTENT = 32.0F;

	public BlockElement(Vector3fc vector3fc, Vector3fc vector3fc2, Map<Direction, BlockElementFace> map) {
		this(vector3fc, vector3fc2, map, null, true, 0);
	}

	@Environment(EnvType.CLIENT)
	protected static class Deserializer implements JsonDeserializer<BlockElement> {
		private static final boolean DEFAULT_SHADE = true;
		private static final int DEFAULT_LIGHT_EMISSION = 0;
		private static final String FIELD_SHADE = "shade";
		private static final String FIELD_LIGHT_EMISSION = "light_emission";
		private static final String FIELD_ROTATION = "rotation";
		private static final String FIELD_ORIGIN = "origin";
		private static final String FIELD_ANGLE = "angle";
		private static final String FIELD_X = "x";
		private static final String FIELD_Y = "y";
		private static final String FIELD_Z = "z";
		private static final String FIELD_AXIS = "axis";
		private static final String FIELD_RESCALE = "rescale";
		private static final String FIELD_FACES = "faces";
		private static final String FIELD_TO = "to";
		private static final String FIELD_FROM = "from";

		public BlockElement deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			Vector3f vector3f = getPosition(jsonObject, "from");
			Vector3f vector3f2 = getPosition(jsonObject, "to");
			BlockElementRotation blockElementRotation = this.getRotation(jsonObject);
			Map<Direction, BlockElementFace> map = this.getFaces(jsonDeserializationContext, jsonObject);
			if (jsonObject.has("shade") && !GsonHelper.isBooleanValue(jsonObject, "shade")) {
				throw new JsonParseException("Expected 'shade' to be a Boolean");
			}

			boolean bl = GsonHelper.getAsBoolean(jsonObject, "shade", true);
			int i = 0;
			if (jsonObject.has("light_emission")) {
				boolean bl2 = GsonHelper.isNumberValue(jsonObject, "light_emission");
				if (bl2) {
					i = GsonHelper.getAsInt(jsonObject, "light_emission");
				}

				if (!bl2 || i < 0 || i > 15) {
					throw new JsonParseException("Expected 'light_emission' to be an Integer between (inclusive) 0 and 15");
				}
			}

			return new BlockElement(vector3f, vector3f2, map, blockElementRotation, bl, i);
		}

		private @Nullable BlockElementRotation getRotation(JsonObject jsonObject) {
			if (!jsonObject.has("rotation")) {
				return null;
			}

			JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "rotation");
			Vector3f vector3f = getVector3f(jsonObject2, "origin");
			vector3f.mul(0.0625F);
			BlockElementRotation.RotationValue rotationValue;
			if (!jsonObject2.has("axis") && !jsonObject2.has("angle")) {
				if (!jsonObject2.has("x") && !jsonObject2.has("y") && !jsonObject2.has("z")) {
					throw new JsonParseException("Missing rotation value, expected either 'axis' and 'angle' or 'x', 'y' and 'z'");
				}

				float g = GsonHelper.getAsFloat(jsonObject2, "x", 0.0F);
				float f = GsonHelper.getAsFloat(jsonObject2, "y", 0.0F);
				float h = GsonHelper.getAsFloat(jsonObject2, "z", 0.0F);
				rotationValue = new BlockElementRotation.EulerXYZRotation(g, f, h);
			} else {
				Direction.Axis axis = this.getAxis(jsonObject2);
				float f = GsonHelper.getAsFloat(jsonObject2, "angle");
				rotationValue = new BlockElementRotation.SingleAxisRotation(axis, f);
			}

			boolean bl = GsonHelper.getAsBoolean(jsonObject2, "rescale", false);
			return new BlockElementRotation(vector3f, rotationValue, bl);
		}

		private Direction.Axis getAxis(JsonObject jsonObject) {
			String string = GsonHelper.getAsString(jsonObject, "axis");
			Direction.Axis axis = Direction.Axis.byName(string.toLowerCase(Locale.ROOT));
			if (axis == null) {
				throw new JsonParseException("Invalid rotation axis: " + string);
			} else {
				return axis;
			}
		}

		private Map<Direction, BlockElementFace> getFaces(JsonDeserializationContext jsonDeserializationContext, JsonObject jsonObject) {
			Map<Direction, BlockElementFace> map = this.filterNullFromFaces(jsonDeserializationContext, jsonObject);
			if (map.isEmpty()) {
				throw new JsonParseException("Expected between 1 and 6 unique faces, got 0");
			} else {
				return map;
			}
		}

		private Map<Direction, BlockElementFace> filterNullFromFaces(JsonDeserializationContext jsonDeserializationContext, JsonObject jsonObject) {
			Map<Direction, BlockElementFace> map = Maps.newEnumMap(Direction.class);
			JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "faces");

			for (Entry<String, JsonElement> entry : jsonObject2.entrySet()) {
				Direction direction = this.getFacing(entry.getKey());
				map.put(direction, jsonDeserializationContext.deserialize(entry.getValue(), BlockElementFace.class));
			}

			return map;
		}

		private Direction getFacing(String string) {
			Direction direction = Direction.byName(string);
			if (direction == null) {
				throw new JsonParseException("Unknown facing: " + string);
			} else {
				return direction;
			}
		}

		private static Vector3f getPosition(JsonObject jsonObject, String string) {
			Vector3f vector3f = getVector3f(jsonObject, string);
			if (!(vector3f.x() < -16.0F)
				&& !(vector3f.y() < -16.0F)
				&& !(vector3f.z() < -16.0F)
				&& !(vector3f.x() > 32.0F)
				&& !(vector3f.y() > 32.0F)
				&& !(vector3f.z() > 32.0F)) {
				return vector3f;
			} else {
				throw new JsonParseException("'" + string + "' specifier exceeds the allowed boundaries: " + vector3f);
			}
		}

		private static Vector3f getVector3f(JsonObject jsonObject, String string) {
			JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, string);
			if (jsonArray.size() != 3) {
				throw new JsonParseException("Expected 3 " + string + " values, found: " + jsonArray.size());
			}

			float[] fs = new float[3];

			for (int i = 0; i < fs.length; i++) {
				fs[i] = GsonHelper.convertToFloat(jsonArray.get(i), string + "[" + i + "]");
			}

			return new Vector3f(fs[0], fs[1], fs[2]);
		}
	}
}
