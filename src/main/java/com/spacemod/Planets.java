package com.spacemod;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Planetas prototipo del espacio: esferas huecas de bloques, muy lejos del punto de llegada (0, 200, 0).
 * Se construyen solas, una "rebanada" por tick, la primera vez que arranca el servidor.
 */
public final class Planets {

	public interface Painter {
		BlockState at(int dx, int dy, int dz, int r);
	}

	public record Planet(String name, int cx, int cy, int cz, int r, Painter painter) {
	}

	/** Grosor de la cascara de cada planeta (en bloques). */
	private static final int SHELL = 3;

	public static final List<Planet> ALL = List.of(
			new Planet("Marte", 300, 215, 40, 35, Planets::mars),
			new Planet("Oceano", -260, 190, 170, 45, Planets::ocean),
			new Planet("Gigante gaseoso", 60, 235, -340, 60, Planets::gasGiant),
			new Planet("Hielo", -150, 250, -260, 25, Planets::ice),
			new Planet("Luna", 200, 170, 260, 18, Planets::moon)
	);

	private int index = 0;
	private int dy = 0;
	private boolean started = false;

	/** Construye una rebanada del planeta actual. Llamar una vez por tick. */
	public void step(ServerWorld world) {
		if (index >= ALL.size()) {
			return;
		}
		Planet p = ALL.get(index);

		if (!started) {
			// Si el polo norte ya existe, este planeta esta construido (se hace de abajo hacia arriba).
			BlockPos pole = new BlockPos(p.cx(), p.cy() + p.r(), p.cz());
			if (!world.getBlockState(pole).isAir()) {
				index++;
				return;
			}
			setForced(world, p, true);
			dy = -p.r();
			started = true;
		}

		int r = p.r();
		int outer = r * r;
		int inner = (r - SHELL) * (r - SHELL);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d2 = dx * dx + dy * dy + dz * dz;
				if (d2 <= outer && d2 > inner) {
					pos.set(p.cx() + dx, p.cy() + dy, p.cz() + dz);
					world.setBlockState(pos, p.painter().at(dx, dy, dz, r), Block.NOTIFY_LISTENERS);
				}
			}
		}

		dy++;
		if (dy > r) {
			setForced(world, p, false);
			started = false;
			index++;
		}
	}

	private static void setForced(ServerWorld world, Planet p, boolean forced) {
		int x0 = (p.cx() - p.r()) >> 4;
		int x1 = (p.cx() + p.r()) >> 4;
		int z0 = (p.cz() - p.r()) >> 4;
		int z1 = (p.cz() + p.r()) >> 4;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				world.setChunkForced(x, z, forced);
			}
		}
	}

	// ---------- Pintores ----------

	private static int noise(int x, int y, int z) {
		int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return Math.floorMod(h, 100);
	}

	private static BlockState mars(int dx, int dy, int dz, int r) {
		if (Math.abs(dy) > r * 0.88) {
			return Blocks.WHITE_CONCRETE.getDefaultState();
		}
		int n = noise(dx, dy, dz);
		if (n < 25) {
			return Blocks.ORANGE_TERRACOTTA.getDefaultState();
		}
		if (n < 32) {
			return Blocks.BROWN_TERRACOTTA.getDefaultState();
		}
		return Blocks.RED_TERRACOTTA.getDefaultState();
	}

	private static BlockState ocean(int dx, int dy, int dz, int r) {
		if (Math.abs(dy) > r * 0.9) {
			return Blocks.WHITE_CONCRETE.getDefaultState();
		}
		double c = Math.sin(dx * 0.13) + Math.sin(dz * 0.11 + 1) + Math.sin(dy * 0.12 + 2);
		int n = noise(dx, dy, dz);
		if (c > 1.1) {
			return n < 50 ? Blocks.GREEN_CONCRETE.getDefaultState() : Blocks.LIME_CONCRETE.getDefaultState();
		}
		if (n < 4) {
			return Blocks.WHITE_CONCRETE.getDefaultState();
		}
		return Blocks.BLUE_CONCRETE.getDefaultState();
	}

	private static final Block[] BANDS = {
			Blocks.ORANGE_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA,
			Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.WHITE_TERRACOTTA,
			Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA
	};

	private static BlockState gasGiant(int dx, int dy, int dz, int r) {
		int band = (dy + r) / 8;
		if (noise(dx, dy, dz) < 8) {
			band++;
		}
		return BANDS[band % BANDS.length].getDefaultState();
	}

	private static BlockState ice(int dx, int dy, int dz, int r) {
		int n = noise(dx, dy, dz);
		if (n < 30) {
			return Blocks.PACKED_ICE.getDefaultState();
		}
		if (n < 45) {
			return Blocks.LIGHT_BLUE_CONCRETE.getDefaultState();
		}
		return Blocks.SNOW_BLOCK.getDefaultState();
	}

	private static BlockState moon(int dx, int dy, int dz, int r) {
		int n = noise(dx, dy, dz);
		if (n < 50) {
			return Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
		}
		if (n < 80) {
			return Blocks.ANDESITE.getDefaultState();
		}
		return Blocks.STONE.getDefaultState();
	}
}
