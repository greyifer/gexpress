package dev.mapselect.voice;

public final class VoicePitchShifter {
	private VoicePitchShifter() {}

	public static short[] shift(short[] input, float pitch) {
		if (input == null || input.length <= 1) return input;
		float clampedPitch = Math.max(0.5F, Math.min(2.0F, pitch));
		if (Math.abs(clampedPitch - 1.0F) <= 0.03F) return input;

		short[] out = new short[input.length];
		int max = input.length - 1;
		double source = 0.0D;
		for (int i = 0; i < out.length; i++) {
			while (source >= max) source -= max;
			int a = Math.max(0, Math.min(max, (int) source));
			int b = a >= max ? 0 : a + 1;
			double t = source - Math.floor(source);
			out[i] = (short) Math.round(input[a] * (1.0D - t) + input[b] * t);
			source += clampedPitch;
		}
		return out;
	}
}
