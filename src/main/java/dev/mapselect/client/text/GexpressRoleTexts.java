package dev.mapselect.client.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class GexpressRoleTexts {
	private static final int[] PAINTER_COLORS = {
		0xFF4D5E,
		0xFF8C2A,
		0xFFD447,
		0x54D96B,
		0x31D6D0,
		0x4A8DFF,
		0x9A5BFF,
		0xE35BFF,
		0xFF5BC8
	};

	private GexpressRoleTexts() {}

	public static MutableText painterName(Text fallback) {
		String label = fallback == null ? "The Painter" : fallback.getString();
		label = stripLegacyFormatting(label);
		if (label == null || label.isBlank()) label = "The Painter";
		return rainbow(label);
	}

	public static MutableText rainbow(String label) {
		MutableText out = Text.empty();
		int colorIndex = 0;
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (Character.isWhitespace(c)) {
				out.append(Text.literal(Character.toString(c)));
				continue;
			}
			int color = PAINTER_COLORS[colorIndex % PAINTER_COLORS.length];
			colorIndex++;
			out.append(Text.literal(Character.toString(c)).styled(style -> style.withColor(color)));
		}
		return out;
	}

	public static int painterColor(int index) {
		return PAINTER_COLORS[Math.floorMod(index, PAINTER_COLORS.length)];
	}

	private static String stripLegacyFormatting(String label) {
		if (label == null || label.indexOf('\u00A7') < 0) return label;
		StringBuilder out = new StringBuilder(label.length());
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (c == '\u00A7' && i + 1 < label.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
