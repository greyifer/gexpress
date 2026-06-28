package dev.mapselect.client.hud;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public final class AnimatedCounterText {
	private final AnimatedNumber current = new AnimatedNumber();
	private final AnimatedNumber required = new AnimatedNumber();
	private float colorImpulse;
	private int targetCurrent;
	private int targetRequired;
	private boolean initialized;

	public void setTarget(int currentValue, int requiredValue) {
		int nextCurrent = Math.max(0, currentValue);
		int nextRequired = Math.max(0, requiredValue);
		if (!initialized) {
			initialized = true;
			targetCurrent = nextCurrent;
			targetRequired = nextRequired;
			current.reset(nextCurrent);
			required.reset(nextRequired);
			return;
		}
		if (nextCurrent != targetCurrent || nextRequired != targetRequired) {
			boolean favorable = nextCurrent > targetCurrent || nextRequired < targetRequired;
			colorImpulse = favorable ? 0.6F : -0.6F;
			targetCurrent = nextCurrent;
			targetRequired = nextRequired;
		}
		current.setTarget(nextCurrent);
		required.setTarget(nextRequired);
	}

	public void reset() {
		initialized = false;
		colorImpulse = 0.0F;
		targetCurrent = 0;
		targetRequired = 0;
		current.reset(0);
		required.reset(0);
	}

	public void tick() {
		current.tick();
		required.tick();
		colorImpulse = MathHelper.lerp(0.10F, colorImpulse, 0.0F);
		if (Math.abs(colorImpulse) < 0.01F) colorImpulse = 0.0F;
	}

	public int width(TextRenderer renderer, String prefix) {
		if (renderer == null) return 0;
		String label = prefix == null ? "" : prefix;
		return renderer.getWidth(label) + current.width(renderer) + renderer.getWidth("/") + required.width(renderer);
	}

	public int render(DrawContext context, TextRenderer renderer, String prefix, int x, int y, int baseRgb,
			int alpha, float tickDelta) {
		if (context == null || renderer == null) return x;
		int color = animatedColor(baseRgb, alpha);
		String label = prefix == null ? "" : prefix;
		context.drawTextWithShadow(renderer, label, x, y, color);
		x += renderer.getWidth(label);
		x += current.render(context, renderer, x, y, color, tickDelta);
		context.drawTextWithShadow(renderer, "/", x, y, color);
		x += renderer.getWidth("/");
		x += required.render(context, renderer, x, y, color, tickDelta);
		return x;
	}

	private int animatedColor(int baseRgb, int alpha) {
		int clampedAlpha = Math.max(0, Math.min(255, alpha));
		int base = baseRgb & 0xFFFFFF;
		int target = colorImpulse >= 0.0F ? 0x87FF8C : 0xFF7777;
		float amount = Math.min(1.0F, Math.abs(colorImpulse));
		int r = blend((base >> 16) & 0xFF, (target >> 16) & 0xFF, amount);
		int g = blend((base >> 8) & 0xFF, (target >> 8) & 0xFF, amount);
		int b = blend(base & 0xFF, target & 0xFF, amount);
		return (clampedAlpha << 24) | (r << 16) | (g << 8) | b;
	}

	private static int blend(int from, int to, float amount) {
		return Math.max(0, Math.min(255, Math.round(MathHelper.lerp(amount, from, to))));
	}

	private static final class AnimatedNumber {
		private int previous;
		private int target;
		private float progress;
		private float lastProgress;

		private void reset(int targetValue) {
			target = Math.max(0, targetValue);
			previous = target;
			progress = 1.0F;
			lastProgress = 1.0F;
		}

		private void setTarget(int targetValue) {
			int next = Math.max(0, targetValue);
			if (next == target) return;
			previous = progress >= 0.5F ? target : previous;
			target = next;
			progress = 0.0F;
			lastProgress = 0.0F;
		}

		private void tick() {
			lastProgress = progress;
			progress = MathHelper.lerp(0.18F, progress, 1.0F);
			if (progress > 0.99F) {
				progress = 1.0F;
				previous = target;
			}
		}

		private int width(TextRenderer renderer) {
			return digitCount() * digitWidth(renderer);
		}

		private int render(DrawContext context, TextRenderer renderer, int x, int y, int color, float tickDelta) {
			int startX = x;
			int digits = digitCount();
			float renderedProgress = MathHelper.clamp(MathHelper.lerp(tickDelta, lastProgress, progress), 0.0F, 1.0F);
			boolean increasing = target >= previous;
			for (int i = 0; i < digits; i++) {
				int place = pow10(digits - i - 1);
				int oldDigit = digitAt(previous, place);
				int newDigit = digitAt(target, place);
				boolean oldLeading = previous < place && place > 1;
				boolean newLeading = target < place && place > 1;
				drawDigit(context, renderer, oldLeading ? -1 : oldDigit, newLeading ? -1 : newDigit,
					increasing, renderedProgress, x, y, color);
				x += digitWidth(renderer);
			}
			return x - startX;
		}

		private int digitCount() {
			int valueMax = Math.max(previous, target);
			int digits = 1;
			while (valueMax >= 10) {
				valueMax /= 10;
				digits++;
			}
			return digits;
		}

		private static void drawDigit(DrawContext context, TextRenderer renderer, int oldDigit, int newDigit,
				boolean increasing, float progress, int x, int y, int color) {
			int lineHeight = renderer.fontHeight + 2;
			int alpha = (color >>> 24) & 0xFF;
			int rgb = color & 0xFFFFFF;
			if (alpha <= 0) return;
			if (progress >= 1.0F || oldDigit == newDigit) {
				drawDigitText(context, renderer, newDigit, x, y, color);
				return;
			}
			int oldY = Math.round(y + (increasing ? -progress : progress) * lineHeight);
			int newY = Math.round(y + (increasing ? 1.0F - progress : progress - 1.0F) * lineHeight);
			drawDigitText(context, renderer, oldDigit, x, oldY, withAlpha(rgb, Math.round(alpha * (1.0F - progress))));
			drawDigitText(context, renderer, newDigit, x, newY, withAlpha(rgb, Math.round(alpha * progress)));
		}

		private static int digitWidth(TextRenderer renderer) {
			return renderer.getWidth("0");
		}

		private static int digitAt(int value, int place) {
			return (value / Math.max(1, place)) % 10;
		}

		private static int pow10(int exponent) {
			int value = 1;
			for (int i = 0; i < exponent; i++) value *= 10;
			return value;
		}

		private static int withAlpha(int rgb, int alpha) {
			int clamped = Math.max(0, Math.min(255, alpha));
			return (clamped << 24) | (rgb & 0xFFFFFF);
		}

		private static void drawDigitText(DrawContext context, TextRenderer renderer, int digit, int x, int y, int color) {
			if (digit < 0) return;
			if (((color >>> 24) & 0xFF) < 4) return;
			context.drawTextWithShadow(renderer, String.valueOf(digit), x, y, color);
		}
	}
}
