package dev.mapselect.client.particle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

public class SandDriftParticle extends SpriteBillboardParticle {

	private final float yRand;
	private final float zRand;
	private float angleX;
	private float angleY;
	private float angleZ;
	private float prevAngleX;
	private float prevAngleY;
	private float prevAngleZ;
	private final float angleRandX;
	private final float angleRandY;
	private final float angleRandZ;

	protected SandDriftParticle(ClientWorld world, double x, double y, double z,
								double vx, double vy, double vz, SpriteProvider sprites) {
		super(world, x, y, z, vx, vy, vz);
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;

		this.zRand = world.random.nextFloat() * 2f - 1f;
		this.yRand = world.random.nextFloat() * 2f - 1f;

		this.angleRandX = (world.random.nextFloat() * 2f - 1f) * 0.1f;
		this.angleRandY = (world.random.nextFloat() * 2f - 1f) * 0.1f;
		this.angleRandZ = (world.random.nextFloat() * 2f - 1f) * 0.1f;

		this.maxAge = 40 + world.random.nextInt(20);
		this.gravityStrength = 0.1f + world.random.nextFloat() * 0.1f;
		this.angle = 0f;

		this.setSprite(sprites.getSprite(world.random));
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		super.tick();
		this.angle += 0.01f;

		float speed = 0.2f;
		float t = this.age / 2f + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
		this.velocityZ = Math.sin(this.zRand + t) * speed;
		this.velocityY = -0.1 + Math.sin(this.yRand + t) * speed;

		this.prevAngleX = this.angleX;
		this.prevAngleY = this.angleY;
		this.prevAngleZ = this.angleZ;
		this.angleX += this.angleRandX;
		this.angleY += this.angleRandY;
		this.angleZ += this.angleRandZ;

		if (!this.dead && this.velocityX == 0) {
			this.markDead();
		}
	}

	@Override
	public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
		Quaternionf quaternion = new Quaternionf();
		this.getRotator().setRotation(quaternion, camera, tickDelta);
		quaternion.rotateXYZ(
			MathHelper.lerp(tickDelta, this.prevAngleX, this.angleX),
			MathHelper.lerp(tickDelta, this.prevAngleY, this.angleY),
			MathHelper.lerp(tickDelta, this.prevAngleZ, this.angleZ)
		);
		this.method_60373(vertexConsumer, camera, quaternion, tickDelta);
	}

	public static class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider sprites;

		public Factory(SpriteProvider sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world,
									   double x, double y, double z,
									   double vx, double vy, double vz) {
			return new SandDriftParticle(world, x, y, z, vx, vy, vz, this.sprites);
		}
	}
}
