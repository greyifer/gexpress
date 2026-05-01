package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.gui.image.ImageRenderer;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class DynamicOptionDescription implements OptionDescription {
	private final Supplier<List<Text>> lines;

	private DynamicOptionDescription(Supplier<List<Text>> lines) {
		this.lines = lines;
	}

	public static DynamicOptionDescription of(Supplier<List<Text>> lines) {
		return new DynamicOptionDescription(lines);
	}

	@Override
	public Text text() {
		List<Text> current = lines.get();
		if (current == null || current.isEmpty()) return ScreenTexts.EMPTY;
		MutableText out = Text.empty();
		for (int i = 0; i < current.size(); i++) {
			if (i > 0) out.append(Text.literal("\n"));
			out.append(current.get(i));
		}
		return out;
	}

	@Override
	public CompletableFuture<Optional<ImageRenderer>> image() {
		return CompletableFuture.completedFuture(Optional.empty());
	}
}
