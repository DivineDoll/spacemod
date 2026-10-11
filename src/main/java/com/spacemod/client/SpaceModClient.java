package com.spacemod.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.spacemod.SpaceMod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Dibuja la Tierra de Minecraft justo debajo de ti en el espacio, hecha de BLOQUES (un planeta
 * cubico/pixelado) en vez de una esfera lisa.
 *
 * Truco: la Tierra "real" esta a miles de bloques, mas alla del plano lejano de la camara, asi que se
 * dibuja a una distancia fija y mas pequena con el mismo tamano aparente. Al bajar, la distancia virtual
 * se acorta y el planeta ocupa un angulo cada vez mayor.
 */
@Environment(EnvType.CLIENT)
public class SpaceModClient implements ClientModInitializer {
	/** Distancia a la que se dibuja el planeta (dentro del plano lejano de la camara). */
	private static final double DRAW_DISTANCE = 220;

	/** Radio del planeta en bloques-voxel. Mas alto = mas detalle, pero mas caro de dibujar. */
	private static final int N = 14;

	/** Caras visibles del planeta: 4 vertices (x,y,z) + color RGB por cara. */
	private static float[] faceVerts;
	private static int[] faceColors;
	private static int faceCount;

	@Override
	public void onInitializeClient() {
		buildPlanet();
		WorldRenderEvents.LAST.register(SpaceModClient::render);
	}

	// ---------- Modelo del planeta ----------

	private static boolean solid(int x, int y, int z) {
		double dx = x + 0.5;
		double dy = y + 0.5;
		double dz = z + 0.5;
		return dx * dx + dy * dy + dz * dz <= (double) N * N;
	}

	private static void buildPlanet() {
		List<float[]> verts = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		for (int x = -N; x < N; x++) {
			for (int y = -N; y < N; y++) {
				for (int z = -N; z < N; z++) {
					if (!solid(x, y, z)) {
						continue;
					}
					int base = blockColor(x, y, z);
					// 6 caras; solo las que dan al vacio. Sombreado estilo Minecraft.
					addFace(verts, colors, x, y, z, 0, 1, 0, base, 1.0f);   // arriba
					addFace(verts, colors, x, y, z, 0, -1, 0, base, 0.5f);  // abajo
					addFace(verts, colors, x, y, z, 0, 0, 1, base, 0.8f);   // sur
					addFace(verts, colors, x, y, z, 0, 0, -1, base, 0.8f);  // norte
					addFace(verts, colors, x, y, z, 1, 0, 0, base, 0.6f);   // este
					addFace(verts, colors, x, y, z, -1, 0, 0, base, 0.6f);  // oeste
				}
			}
		}

		faceCount = verts.size();
		faceVerts = new float[faceCount * 12];
		faceColors = new int[faceCount];
		for (int i = 0; i < faceCount; i++) {
			System.arraycopy(verts.get(i), 0, faceVerts, i * 12, 12);
			faceColors[i] = colors.get(i);
		}
	}

	private static void addFace(List<float[]> verts, List<Integer> colors,
			int x, int y, int z, int nx, int ny, int nz, int base, float shade) {
		if (solid(x + nx, y + ny, z + nz)) {
			return;
		}

		float x0 = x;
		float y0 = y;
		float z0 = z;
		float x1 = x + 1;
		float y1 = y + 1;
		float z1 = z + 1;
		float[] v;

		if (ny == 1) {
			v = new float[] { x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0 };
		} else if (ny == -1) {
			v = new float[] { x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1 };
		} else if (nz == 1) {
			v = new float[] { x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1 };
		} else if (nz == -1) {
			v = new float[] { x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0 };
		} else if (nx == 1) {
			v = new float[] { x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1 };
		} else {
			v = new float[] { x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0 };
		}

		int r = (int) (((base >> 16) & 255) * shade);
		int g = (int) (((base >> 8) & 255) * shade);
		int b = (int) ((base & 255) * shade);
		verts.add(v);
		colors.add((r << 16) | (g << 8) | b);
	}

	private static int hash(int x, int y, int z) {
		int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return Math.floorMod(h, 100);
	}

	/** Color de bloque: agua, cesped, arena, piedra, nieve... con variacion por bloque (efecto pixelado). */
	private static int blockColor(int x, int y, int z) {
		double cx = x + 0.5;
		double cy = y + 0.5;
		double cz = z + 0.5;
		double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
		double lat = Math.asin(cy / len);
		double lon = Math.atan2(cz, cx);

		int rgb;
		double land = Math.sin(lon * 2 + 1) * Math.cos(lat * 2)
				+ Math.sin(lon * 3 + lat * 3) * 0.6
				+ Math.sin(lat * 5 + lon) * 0.4;
		int n = hash(x, y, z);

		if (Math.abs(lat) > 1.2) {
			rgb = 0xF5FAFA; // nieve
		} else if (land > 1.0) {
			rgb = n < 60 ? 0x7D7D7D : 0x6B6B6B; // montanas de piedra
		} else if (land > 0.7 && Math.abs(lat) < 0.8) {
			rgb = n < 70 ? 0xDBCF8E : 0xCFC283; // arena
		} else if (land > 0.2) {
			rgb = n < 65 ? 0x5D9C3A : 0x4F8A30; // cesped
		} else {
			rgb = n < 70 ? 0x3F76E4 : 0x3566CC; // agua
		}

		// Algunas "nubes": bloques blancos sueltos.
		if (n > 93) {
			rgb = 0xF0F0F0;
		}
		return rgb;
	}

	// ---------- Dibujo ----------

	private static void render(WorldRenderContext ctx) {
		ClientWorld world = ctx.world();
		if (world == null || world.getRegistryKey() != SpaceMod.SPACE) {
			return;
		}

		// El planeta esta FIJO en el mundo: la direccion y la distancia salen de su posicion real.
		Vec3d toEarth = SpaceMod.EARTH_CENTER.subtract(ctx.camera().getPos());
		double dist = toEarth.length();
		if (dist <= SpaceMod.EARTH_RADIUS + 1) {
			return;
		}

		double scale = DRAW_DISTANCE / dist;
		double drawnRadius = SpaceMod.EARTH_RADIUS * scale;
		float s = (float) (drawnRadius / N);
		Vec3d pos = toEarth.multiply(scale);

		MatrixStack matrices = ctx.matrixStack();
		matrices.push();
		matrices.translate(pos.x, pos.y, pos.z);
		matrices.scale(s, s, s);
		Matrix4f mat = matrices.peek().getPositionMatrix();

		float fogStart = RenderSystem.getShaderFogStart();
		float fogEnd = RenderSystem.getShaderFogEnd();
		RenderSystem.setShaderFogStart(1.0E9F);
		RenderSystem.setShaderFogEnd(1.0E9F + 1.0F);
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.enableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.disableBlend();

		BufferBuilder buf = Tessellator.getInstance().getBuffer();
		buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		for (int i = 0; i < faceCount; i++) {
			int c = faceColors[i];
			int r = (c >> 16) & 255;
			int g = (c >> 8) & 255;
			int b = c & 255;
			int o = i * 12;
			for (int k = 0; k < 4; k++) {
				buf.vertex(mat, faceVerts[o + k * 3], faceVerts[o + k * 3 + 1], faceVerts[o + k * 3 + 2])
						.color(r, g, b, 255).next();
			}
		}

		BufferRenderer.drawWithGlobalProgram(buf.end());

		RenderSystem.enableCull();
		RenderSystem.setShaderFogStart(fogStart);
		RenderSystem.setShaderFogEnd(fogEnd);
		matrices.pop();
	}
}
