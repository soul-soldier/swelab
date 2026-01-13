package artcreator.domain.port;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Result of template generation.
 * Contains a preview image (for UI) and the computed color palette.
 */
public final class TemplateResult {
	private final BufferedImage preview;
	private final Color[] palette;

	public TemplateResult(BufferedImage preview, Color[] palette) {
		this.preview = Objects.requireNonNull(preview, "preview");
		this.palette = Objects.requireNonNull(palette, "palette");
	}

	public BufferedImage getPreview() {
		return preview;
	}

	public Color[] getPalette() {
		return palette;
	}
}
