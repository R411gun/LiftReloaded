package com.minecraftcorp.lift.bukkit.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import com.minecraftcorp.lift.bukkit.LiftPlugin;
import com.minecraftcorp.lift.bukkit.model.BukkitConfig;
import com.minecraftcorp.lift.bukkit.model.BukkitElevator;
import com.minecraftcorp.lift.bukkit.model.BukkitFloorSign;
import com.minecraftcorp.lift.common.exception.ElevatorCreateException;
import com.minecraftcorp.lift.common.exception.ElevatorUsageException;
import com.minecraftcorp.lift.common.model.Elevator;
import com.minecraftcorp.lift.common.model.Floor;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ElevatorFactory {

	public static final BukkitConfig config = BukkitConfig.INSTANCE;
	public static final LiftPlugin plugin = LiftPlugin.INSTANCE;

	public static Optional<BukkitElevator> createElevator(Block buttonBlock) {
		if (!config.isValidLiftStructureFromButton(buttonBlock)) {
			return Optional.empty();
		}

		Set<Block> baseBlocks = findBaseBlocksBelow(buttonBlock);
		if (baseBlocks.isEmpty()) {
			throw new ElevatorUsageException("There is no base block for this lift");
		}
		List<Floor> floors = createFloors(baseBlocks);
		if (floors.size() <= 1) {
			throw new ElevatorUsageException("There is no other floor");
		}
		plugin.logDebug("Found " + baseBlocks.size() + " base blocks and " + floors.size() + " floors");
		Floor startFloor = getStartFloor(buttonBlock, floors);
		BukkitElevator elevator = new BukkitElevator(baseBlocks, startFloor, floors);
		floors.stream()
				.map(Floor::getSign)
				.forEach(sign -> sign.setElevator(elevator));
		writeEmptyFloorSigns(elevator);
		return Optional.of(elevator);
	}

	public static void writeEmptyFloorSigns(Elevator elevator) {
		List<Floor> floors = elevator.getFloors();
		for (int level = 0; level < floors.size(); level++) {
			Floor current = floors.get(level);
			if (current.getSign().isEmpty()) {
				current.updateSign(floors.get(level == floors.size() - 1 ? 0 : level + 1));
			}
		}
	}

	private static Floor getStartFloor(Block buttonBlock, List<Floor> floors) {
		Optional<Floor> startFloor = floors.stream()
				.filter(floor -> floor.getButtonY() == buttonBlock.getY())
				.findFirst();
		if (!startFloor.isPresent()) {
			throw new ElevatorCreateException("Could not extract start floor from elevator's floors");
		}
		return startFloor.get();
	}

	private static List<Floor> createFloors(Set<Block> baseBlocks) {
		List<Floor> floors = new ArrayList<>();
		Optional<Block> firstBase = baseBlocks.stream().findFirst();
		if (!firstBase.isPresent()) {
			return Collections.emptyList();
		}
		int level = 1;
		World world = firstBase.get().getWorld();
		for (int y = firstBase.get().getY() + 1; y < config.getMaxHeight(); y++) {
			boolean floorFound = false;
			for (Block baseBlock : baseBlocks) {
				int x = baseBlock.getX();
				int z = baseBlock.getZ();

				Block block = world.getBlockAt(x, y, z);
				if (!isValidShaftBlock(block)) {
					break; // continue with next base block
				}
				if (config.isValidLiftStructureFromButton(block)) {
					floors.add(createFloor(block, level));
					floorFound = true;
				}
			}
			if (floorFound) {
				level++;
			}
		}
		return floors;
	}

	private static Floor createFloor(Block buttonBlock, int level) {
		BukkitFloorSign floorSign = new BukkitFloorSign(((Sign) buttonBlock.getRelative(BlockFace.UP).getState()));
		Block belowButton = buttonBlock.getRelative(BlockFace.DOWN);
		String name = config.isSign(belowButton) ? ((Sign) belowButton.getState()).getLine(1) : "";
		return new Floor(level, name, buttonBlock.getY(), floorSign);
	}

	private static Set<Block> findBaseBlocksBelow(Block startBlock) {
		World world = startBlock.getWorld();
		int x = startBlock.getX();
		int z = startBlock.getZ();
		for (int y = startBlock.getY(); y >= 0; y--) {
			Block block = world.getBlockAt(x, y, z);
			if (isValidShaftBlock(block)) {
				continue;
			}
			if (!config.isBaseBlock(block)) {
				return Collections.emptySet();
			}
			return getNeighborBaseBlocks(block, new HashSet<>());
		}
		return Collections.emptySet();
	}

	private static Set<Block> getNeighborBaseBlocks(Block baseBlock, Set<Block> blocks) {
		if (blocks.size() == config.getMaxLiftArea()) {
			plugin.logDebug("Reached limit of " + blocks.size() + " base blocks (see max lift area in config)");
			return blocks;
		}
		blocks.add(baseBlock);
		Material baseType = baseBlock.getType();
		List<BlockFace> blockFaces = Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
		for (BlockFace blockFace : blockFaces) {
			Block neighbor = baseBlock.getRelative(blockFace);
			if (!blocks.contains(neighbor) && neighbor.getType() == baseType) {
				blocks.addAll(getNeighborBaseBlocks(neighbor, blocks));
			}
		}
		return blocks;
	}

	private static boolean isValidShaftBlock(Block block){
		return !block.getType().isSolid()
				|| config.getFloorMaterials().contains(block.getType())
				|| config.isButton(block)
				|| config.isSign(block)
				|| block.getType() == Material.LADDER // TODO: Konfigurierbar machen
				|| block.getType() == Material.SNOW
				|| block.getType() == Material.TORCH
				|| block.getType() == Material.VINE
				|| block.getType() == Material.WATER
				|| block.getType() == Material.RAIL
				|| block.getType() == Material.DETECTOR_RAIL
				|| block.getType() == Material.ACTIVATOR_RAIL
				|| block.getType() == Material.POWERED_RAIL
				|| block.getType() == Material.REDSTONE_WIRE;
	}
}
