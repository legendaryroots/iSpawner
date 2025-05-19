package iskallia.ispawner.world.spawner;

import iskallia.ispawner.block.entity.SpawnerBlockEntity;
import iskallia.ispawner.block.render.SpawnerBlockRenderer;
import iskallia.ispawner.util.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;

import java.util.*;

public class SpawnerRenderer {

	protected Set<Face> faces = new HashSet<>();

	public SpawnerRenderer() {

	}

	public void refresh(SpawnerBlockEntity entity) {
		this.faces.clear();

		entity.manager.actions.forEach(entry -> {
			this.faces.add(new Face(entry.value.getPos().rotate(entity.getRotation()),
					entity.getRotation().rotate(entry.value.getSide()), entry.weight));
		});
	}

	public void render(MatrixStack matrices, VertexConsumer vertexConsumer, SpawnerBlockEntity entity) {
		new ArrayList<>(this.faces).stream()
				.sorted(Comparator.comparingInt(o -> o.weight))
				.forEach(face -> face.render(matrices, vertexConsumer, this.faces, entity.getOffset()));
	}

	public static class Face {
		private final BlockPos pos;
		private final Direction side;
		private final int weight;
		private final Color color;
		
		// Direction-specific configuration maps
		private static final Map<Direction, Vector3f> ROTATION_AXES = Map.of(
			Direction.UP, new Vector3f(1.0F, 0.0F, 0.0F),
			Direction.DOWN, new Vector3f(1.0F, 0.0F, 0.0F),
			Direction.NORTH, new Vector3f(0.0F, 1.0F, 0.0F),
			Direction.SOUTH, new Vector3f(0.0F, 1.0F, 0.0F),
			Direction.WEST, new Vector3f(0.0F, 1.0F, 0.0F),
			Direction.EAST, new Vector3f(0.0F, 1.0F, 0.0F)
		);
		
		private static final Map<Direction, Float> ROTATION_ANGLES = Map.of(
			Direction.UP, 90.0F,
			Direction.DOWN, -90.0F,
			Direction.NORTH, 0.0F,
			Direction.SOUTH, 180.0F,
			Direction.WEST, 90.0F,
			Direction.EAST, -90.0F
		);
		
		// Offsets for each direction when calculating center position
		private static final Map<Direction, float[]> POSITION_OFFSETS = Map.of(
			Direction.UP, new float[]{0.0F, 1.01F, 11.5F / 16.0F},
			Direction.DOWN, new float[]{0.0F, -0.01F, 4.5F / 16.0F},
			Direction.NORTH, new float[]{0.0F, 11.5F / 16.0F, -0.01F},
			Direction.SOUTH, new float[]{0.0F, 11.5F / 16.0F, 1.01F},
			Direction.WEST, new float[]{-0.01F, 11.5F / 16.0F, 0.0F},
			Direction.EAST, new float[]{1.01F, 11.5F / 16.0F, 0.0F}
		);
		
		// Map of directions to offset calculations (true for positive, false for negative)
		private static final Map<Direction, Boolean> CENTER_DIRECTIONS = Map.of(
			Direction.UP, true,
			Direction.DOWN, true,
			Direction.NORTH, true,
			Direction.SOUTH, false,
			Direction.WEST, false,
			Direction.EAST, true
		);

		public Face(BlockPos pos, Direction side, int weight) {
			this.pos = pos;
			this.side = side;
			this.weight = weight;
			this.color = SpawnerBlockRenderer.getColorFor(this.weight);
		}

		public void render(MatrixStack matrices, VertexConsumer vertexConsumer, Collection<Face> neighbors, BlockPos offset) {
			matrices.push();
			BlockPos p = this.pos.add(offset);
			
			// Calculate text width and center position
			float textWidth = MinecraftClient.getInstance().textRenderer.getWidth(String.valueOf(this.weight));
			double center;
			
			// Get center position based on direction
			boolean isPositiveDirection = CENTER_DIRECTIONS.get(this.side);
			if (isPositiveDirection) {
				center = (8.0D + textWidth / 2.0D) / 16.0D;
			} else {
				center = (8.0D - textWidth / 2.0D) / 16.0D;
			}
			
			// Get position offsets for this direction
			float[] posOffsets = POSITION_OFFSETS.get(this.side);
			
			// Calculate final position based on direction
			float xOffset = isPositiveDirection ? (float)(center - 0.5D / 16.0D) : (float)(center + 0.5D / 16.0D);
			matrices.translate(
				p.getX() + (this.side == Direction.WEST || this.side == Direction.EAST ? posOffsets[0] : xOffset),
				p.getY() + posOffsets[1],
				p.getZ() + (this.side == Direction.NORTH || this.side == Direction.SOUTH ? posOffsets[2] : 
					(this.side == Direction.WEST ? center + 0.5D / 16.0D : 
					(this.side == Direction.EAST ? center - 0.5D / 16.0D : posOffsets[2])))
			);
			
			// Apply rotation
			matrices.multiply(new Quaternion(
				ROTATION_AXES.get(this.side),
				ROTATION_ANGLES.get(this.side),
				true
			));

			// Draw text
			matrices.scale(-0.0625F, -0.0625F, 0.0625F);
			MinecraftClient.getInstance().textRenderer.draw(matrices, new LiteralText(String.valueOf(this.weight)), 0, 0, this.color.getRBG());
			matrices.pop();

			// Draw boundary lines
			renderBoundaryLines(matrices, vertexConsumer, neighbors, offset);
		}
		
		private void renderBoundaryLines(MatrixStack matrices, VertexConsumer vertexConsumer, Collection<Face> neighbors, BlockPos offset) {
			// A map of directions to check for each face orientation
			Map<Direction, Direction[]> directionsToCheck = Map.of(
				Direction.DOWN, new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST},
				Direction.UP, new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST},
				Direction.NORTH, new Direction[]{Direction.DOWN, Direction.UP, Direction.EAST, Direction.WEST},
				Direction.SOUTH, new Direction[]{Direction.DOWN, Direction.UP, Direction.EAST, Direction.WEST},
				Direction.WEST, new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
				Direction.EAST, new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH}
			);
			
			// Line coordinates for each face+direction combination
			Map<Direction, Map<Direction, double[]>> lineCoords = new HashMap<>();
			
			// DOWN face lines
			lineCoords.put(Direction.DOWN, Map.of(
				Direction.NORTH, new double[]{0, 0, 0, 1, 0, 0},
				Direction.SOUTH, new double[]{0, 0, 1, 1, 0, 1},
				Direction.EAST, new double[]{1, 0, 0, 1, 0, 1},
				Direction.WEST, new double[]{0, 0, 0, 0, 0, 1}
			));
			
			// UP face lines
			lineCoords.put(Direction.UP, Map.of(
				Direction.NORTH, new double[]{0, 1, 0, 1, 1, 0},
				Direction.SOUTH, new double[]{0, 1, 1, 1, 1, 1},
				Direction.EAST, new double[]{1, 1, 0, 1, 1, 1},
				Direction.WEST, new double[]{0, 1, 0, 0, 1, 1}
			));
			
			// NORTH face lines
			lineCoords.put(Direction.NORTH, Map.of(
				Direction.DOWN, new double[]{0, 0, 0, 1, 0, 0},
				Direction.UP, new double[]{0, 1, 0, 1, 1, 0},
				Direction.EAST, new double[]{1, 0, 0, 1, 1, 0},
				Direction.WEST, new double[]{0, 0, 0, 0, 1, 0}
			));
			
			// SOUTH face lines
			lineCoords.put(Direction.SOUTH, Map.of(
				Direction.DOWN, new double[]{0, 0, 1, 1, 0, 1},
				Direction.UP, new double[]{0, 1, 1, 1, 1, 1},
				Direction.EAST, new double[]{1, 0, 1, 1, 1, 1},
				Direction.WEST, new double[]{0, 0, 1, 0, 1, 1}
			));
			
			// WEST face lines
			lineCoords.put(Direction.WEST, Map.of(
				Direction.DOWN, new double[]{0, 0, 0, 0, 0, 1},
				Direction.UP, new double[]{0, 1, 0, 0, 1, 1},
				Direction.NORTH, new double[]{0, 0, 0, 0, 1, 0},
				Direction.SOUTH, new double[]{0, 0, 1, 0, 1, 1}
			));
			
			// EAST face lines
			lineCoords.put(Direction.EAST, Map.of(
				Direction.DOWN, new double[]{1, 0, 0, 1, 0, 1},
				Direction.UP, new double[]{1, 1, 0, 1, 1, 1},
				Direction.NORTH, new double[]{1, 0, 0, 1, 1, 0},
				Direction.SOUTH, new double[]{1, 0, 1, 1, 1, 1}
			));
			
			// Direction functions map - how to get neighboring position for each direction
			Map<Direction, Function<BlockPos, BlockPos>> neighborFuncs = Map.of(
				Direction.NORTH, BlockPos::north,
				Direction.SOUTH, BlockPos::south,
				Direction.EAST, BlockPos::east,
				Direction.WEST, BlockPos::west,
				Direction.UP, BlockPos::up,
				Direction.DOWN, BlockPos::down
			);
			
			// Check each direction and draw lines if needed
			for (Direction checkDir : directionsToCheck.get(this.side)) {
				BlockPos neighborPos = neighborFuncs.get(checkDir).apply(this.pos);
				if (!neighbors.contains(new Face(neighborPos, this.side, this.weight))) {
					double[] coords = lineCoords.get(this.side).get(checkDir);
					drawLine(matrices, vertexConsumer, 
						coords[0], coords[1], coords[2], 
						coords[3], coords[4], coords[5], 
						offset);
				}
			}
		}

		public void drawLine(MatrixStack matrices, VertexConsumer vertexConsumer,
		                     double x1, double y1, double z1,
		                     double x2, double y2, double z2, BlockPos offset) {
			Matrix4f matrix = matrices.peek().getModel();
			BlockPos p = this.pos.add(offset);

			vertexConsumer.vertex(matrix, p.getX() + (float)x1, p.getY() + (float)y1, p.getZ() + (float)z1)
					.color(this.color.getFRed(), this.color.getFGreen(), this.color.getFBlue(), 1.0F).next();
			vertexConsumer.vertex(matrix, p.getX() + (float)x2, p.getY() + (float)y2, p.getZ() + (float)z2)
					.color(this.color.getFRed(), this.color.getFGreen(), this.color.getFBlue(), 1.0F).next();
		}

		@Override
		public boolean equals(Object other) {
			if(this == other)return true;
			if(!(other instanceof Face))return false;
			Face face = (Face)other;
			return this.pos.equals(face.pos) && this.side == face.side && this.weight == face.weight;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.pos, this.side, this.weight);
		}
	}

}
