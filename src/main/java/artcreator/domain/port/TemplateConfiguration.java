package artcreator.domain.port;

import java.util.Objects;

public final class TemplateConfiguration {
	private final MaterialType materialType;
	/** target raster width (number of points horizontally) */
	private final int width;
	/** target raster height (number of points vertically) */
	private final int height;
	/** number of grayscale steps to quantize to */
	private final int colorCount;
    /** spacing (gap) between points in the preview rendering (pixels). */
	private final int pointSpacing;

	public TemplateConfiguration(MaterialType materialType, int width, int height, int colorCount, int pointSpacing) {
		this.materialType = Objects.requireNonNull(materialType, "materialType");
		this.width = width;
		this.height = height;
		this.colorCount = colorCount;
		this.pointSpacing = pointSpacing;
		validate();
	}

	private void validate() {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("width/height must be > 0");
		}
		if (colorCount < 2 || colorCount > 32) {
			throw new IllegalArgumentException("colorCount must be in [2, 32]");
		}
        if (pointSpacing < 0) {
            throw new IllegalArgumentException("pointSpacing must be >= 0");
		}
	}

	public MaterialType getMaterialType() {
		return materialType;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getColorCount() {
		return colorCount;
	}

	public int getPointSpacing() {
		return pointSpacing;
	}
}
