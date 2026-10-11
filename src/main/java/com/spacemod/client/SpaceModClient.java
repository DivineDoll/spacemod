package com.spacemod.client;

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
 * Dibuja la Tierra de Minecraft en el cielo del espacio.
 *
 * Truco: la Tierra real esta a miles de bloques (mas lejos que el plano lejano de la camara), asi que
 * se dibuja a una distancia fija y mas pequena, con el mismo tamano aparente. Al acercarte, el angulo
 * que ocupa crece, y se ve cada vez mas grande.
 */
@Environment(EnvType.CLIENT)
public class SpaceModClient implements ClientModInitializer {
	/** Distancia a la que se dibuja la esfera (dentro del plano lejano de la camara). */
	private static final double DRAW_DISTANCE = 220;

	private static final int LAT = 32;
	private static final int LON = 64;

	@Override
	public void onInitializeClient() {
		WorldRenderEvents.LAST.register(SpaceModClient::render);
	}

	private static void render(WorldRenderContext ctx) {
		ClientWorld world = ctx.world();
		if (world == null || world.getRegistryKey() != SpaceMod.SPACE) {
			return;
		}

		Vec3d toEarth = SpaceMod.EARTH_CENTER.subtract(ctx.camera().getPos());
		double dist = toEarth.length();
		if (dist <= SpaceMod.EARTH_RADIUS + 1) {
			return;
		}

		double scale = DRAW_DISTANCE / dist;
		float radius = (float) (SpaceMod.EARTH_RADIUS * scale);
		Vec3d pos = toEarth.multiply(scale);

		MatrixStack matrices = ctx.matrixStack();
		matrices.push();
		matrices.translate(pos.x, pos.y, pos.z);
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

		for (int i = 0; i < LAT; i++) {
			double lat0 = Math.PI * i / LAT - Math.PI / 2;
			double lat1 = Math.PI * (i + 1) / LAT - Math.PI / 2;
			double latMid = (lat0 + lat1) / 2;
			for (int j = 0; j < LON; j++) {
				double lon0 = 2 * Math.PI * j / LON;
				double lon1 = 2 * Math.PI * (j + 1) / LON;
				double lonMid = (lon0 + lon1) / 2;

				int[] c = earthColor(latMid, lonMid);

				vertex(buf, mat, lat0, lon0, radius, c);
				vertex(buf, mat, lat1, lon0, radius, c);
				vertex(buf, mat, lat1, lon1, radius, c);
				vertex(buf, mat, lat0, lon1, radius, c);
			}
		}

		BufferRenderer.drawWithGlobalProgram(buf.end());

		RenderSystem.enableCull();
		RenderSystem.setShaderFogStart(fogStart);
		RenderSystem.setShaderFogEnd(fogEnd);
		matrices.pop();
	}

	private static void vertex(BufferBuilder buf, Matrix4f mat, double lat, double lon, float radius, int[] c) {
		float x = (float) (Math.cos(lat) * Math.cos(lon)) * radius;
		float y = (float) Math.sin(lat) * radius;
		float z = (float) (Math.cos(lat) * Math.sin(lon)) * radius;
		buf.vertex(mat, x, y, z).color(c[0], c[1], c[2], 255).next();
	}

	/** Color de un trozo de la Tierra: oceanos, continentes, desiertos, hielo en los polos y nubes. */
	private static int[] earthColor(double lat, double lon) {
		int r;
		int g;
		int b;

		double land = Math.sin(lon * 3 + 1) * Math.cos(lat * 2)
				+ Math.sin(lon * 5 + lat * 4) * 0.5
				+ Math.sin(lat * 7 + lon * 2) * 0.3;

		if (Math.abs(lat) > 1.25) {
			r = 235;
			g = 240;
			b = 250;
		} else if (land > 0.8 && Math.abs(lat) < 0.7) {
			r = 205;
			g = 175;
			b = 110;
		} else if (land > 0.35) {
			r = 55;
			g = 140;
			b = 55;
		} else {
			r = 25;
			g = 75;
			b = 190;
		}

		double cloud = Math.sin(lon * 9 + lat * 3) * Math.sin(lat * 11 - lon * 4);
		if (cloud > 0.55) {
			r = (r + 255) / 2;
			g = (g + 255) / 2;
			b = (b + 255) / 2;
		}

		// Iluminacion fija para que se vea redonda: mas claro hacia el "sol".
		double nx = Math.cos(lat) * Math.cos(lon);
		double ny = Math.sin(lat);
		double nz = Math.cos(lat) * Math.sin(lon);
		double dot = nx * 0.5 + ny * 0.4 + nz * -0.77;
		double light = 0.35 + 0.65 * Math.max(0, dot);

		return new int[] {
				(int) Math.min(255, r * light),
				(int) Math.min(255, g * light),
				(int) Math.min(255, b * light)
		};
	}
}
