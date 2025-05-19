package iskallia.ispawner.block.entity;

import iskallia.ispawner.init.ModBlocks;
import iskallia.ispawner.init.ModConfigs;
import iskallia.ispawner.inventory.SimpleInventory;
import iskallia.ispawner.item.nbt.SpawnData;
import iskallia.ispawner.nbt.NBTConstants;
import iskallia.ispawner.screen.handler.SurvivalSpawnerScreenHandler;
import iskallia.ispawner.world.spawner.SpawnerAction;
import iskallia.ispawner.world.spawner.SpawnerSettings;
import me.shedaniel.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

public class SurvivalSpawnerBlockEntity extends SpawnerBlockEntity implements ExtendedMenuProvider {

	// Cache for spawn action directions - no need to recreate this array for every position
	private static final Direction[] ALL_DIRECTIONS = new Direction[] {
		Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN
	};
	
	// Flag to track if we've initialized the spawn actions after rotation changes
	private BlockRotation lastRotation = null;

	public SimpleInventory input = new SimpleInventory(1) {
		@Override
		public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
			return ModConfigs.SURVIVAL_SPAWNER.isWhitelisted(stack);
		}
	};

	public SurvivalSpawnerBlockEntity() {
		super(ModBlocks.Entities.SURVIVAL_SPAWNER);
		this.input.addListener(this);
	}

	public SimpleInventory getInput() {
		return this.input;
	}

	@Override
	public void tick() {
		super.tick();

		SpawnerSettings newConfig = ModConfigs.SURVIVAL_SPAWNER.defaultSettings.copy();
		newConfig.setMode(this.manager.settings.getMode());

		if(!this.manager.settings.equals(newConfig)) {
			this.manager.settings = newConfig;
			this.sendClientUpdates();
		}

		if (this.inventory.isEmpty()) {
			for (int i = 0; i < this.input.size(); i++) {
				ItemStack stack = this.input.getStack(i);
				if (stack.isEmpty()) continue;
				if (!this.input.canInsert(i, stack, Direction.NORTH)) continue;

				OptionalInt emptySlot = this.inventory.getEmptySlot();

				if (emptySlot.isPresent()) {
					ItemStack newStack = new ItemStack(stack.getItem());
					newStack.setCount(new SpawnData(stack).getCharges());
					this.inventory.setStack(emptySlot.getAsInt(), newStack);
					stack.decrement(1);
					this.input.setStack(i, stack);
					break;
				}
			}
		}

		// Only rebuild the action list if it's empty OR if the rotation has changed
		BlockRotation currentRotation = this.getReverseRotation();
		if(this.manager.actions.isEmpty() || currentRotation != lastRotation) {
			this.lastRotation = currentRotation;
			initializeSpawnActions(currentRotation);
		}
	}
	
	/**
	 * Initialize all possible spawn actions in the spawner's area of effect.
	 * This is separated into its own method for clarity and to avoid recreating
	 * objects in the tick method.
	 */
	private void initializeSpawnActions(BlockRotation rotation) {
		// Clear existing actions first
		this.manager.actions.clear();
		
		// Reuse the hit position offset for all actions
		Vec3d hitPosOffset = new Vec3d(0.5D, 1.0D, 0.5D);
		Vec3d rotatedHitPos = SpawnerAction.rotate(rotation, hitPosOffset);
		Direction rotatedDirection = rotation.rotate(Direction.UP);
		
		// Calculate and add all spawn positions
		for(int y = -2; y <= 1; y++) {
			for(int x = -4; x <= 4; x++) {
				for(int z = -4; z <= 4; z++) {
					// Calculate weight based on max distance from center
					int weight = 4 - Math.max(Math.abs(x), Math.abs(z)) + 1;
					
					// Create and add the spawn action with a single BlockPos object
					BlockPos pos = new BlockPos(x, y, z).rotate(rotation);
					this.manager.addAction(new SpawnerAction(
						pos,
						rotatedDirection,
						rotatedHitPos,
						Hand.MAIN_HAND,
						ALL_DIRECTIONS), weight);
				}
			}
		}
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
		return new SurvivalSpawnerScreenHandler(syncId, inv, this);
	}

	@Override
	public void saveExtraData(PacketByteBuf buf) {
		buf.writeBlockPos(this.getPos());
	}

	@Override
	public void onChargeUsed(ItemStack stack, int index) {
		stack.decrement(1);
		this.inventory.setStack(index, stack);
	}

	@Override
	public CompoundTag write(CompoundTag tag, UpdateType type) {
		CompoundTag nbt = super.write(tag, type);
		nbt.put("Input", this.input.writeToNBT());
		return nbt;
	}

	@Override
	public void read(BlockState state, CompoundTag tag, UpdateType type) {
		super.read(state, tag, type);

		if(tag.contains("Input", NBTConstants.COMPOUND)) {
			this.input.readFromNBT(tag.getCompound("Input"));
		}
	}

}
