package artcreator.domain.impl;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class DomainImpl {

	public Object mkObject() { return null; }

	public Object loadImage(String path) throws Exception {
		File file = new File(path);
		if (!file.exists()) throw new java.io.FileNotFoundException("File not found: " + path);
		return ImageIO.read(file);
	}

	public Object transformImage(Object imageObj, String operation) {
		if (!(imageObj instanceof BufferedImage)) {
			throw new IllegalArgumentException("Invalid image object provided.");
		}

		BufferedImage src = (BufferedImage) imageObj;

		switch (operation.toLowerCase()) {
			case "rotate_left":
				return rotate(src, -90);
			case "rotate_right":
				return rotate(src, 90);
			case "mirror":
				// default mirror horizontally
				return mirror(src, true);
			case "mirror_horizontal":
				return mirror(src, true);
			case "mirror_vertical":
				return mirror(src, false);
			default:
				throw new IllegalArgumentException("Unknown transformation: " + operation);
		}
	}

	/**
	 * Rotates the image by 90 or -90 degrees.
	 * Swaps width and height and ensures the image stays within bounds.
	 */
	private BufferedImage rotate(BufferedImage src, int angleDegrees) {
		int w = src.getWidth();
		int h = src.getHeight();

		// 1. Create a new image with SWAPPED dimensions
		//    (Width becomes Height, Height becomes Width)
		BufferedImage dest = new BufferedImage(h, w, src.getType());

		Graphics2D g2d = dest.createGraphics();

		// 2. Perform the correct translation based on direction
		if (angleDegrees == 90) {
			// ROTATE RIGHT (Clockwise)
			// Move origin to Top-Right corner (h, 0)
			g2d.translate(h, 0);
			g2d.rotate(Math.toRadians(90));
		} else if (angleDegrees == -90) {
			// ROTATE LEFT (Counter-Clockwise)
			// Move origin to Bottom-Left corner (0, w)
			g2d.translate(0, w);
			g2d.rotate(Math.toRadians(-90));
		}

		// 3. Draw the image
		// Since we moved the origin (Translate) and turned the paper (Rotate),
		// we can now just draw the image at (0,0) and it will land correctly.
		g2d.drawImage(src, 0, 0, null);
		g2d.dispose();

		return dest;
	}

	/**
	 * Mirrors the image horizontally or vertically.
	 * 
	 * @param src        source image
	 * @param horizontal true = horizontal mirror (left-right), false = vertical
	 *                   (top-bottom)
	 */
	private BufferedImage mirror(BufferedImage src, boolean horizontal) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage dest = new BufferedImage(w, h, src.getType());
		Graphics2D g2d = dest.createGraphics();
		if (horizontal) {
			// flip left-right: translate to right edge then scale X by -1
			g2d.translate(w, 0);
			g2d.scale(-1, 1);
		} else {
			// flip top-bottom: translate to bottom then scale Y by -1
			g2d.translate(0, h);
			g2d.scale(1, -1);
		}
		g2d.drawImage(src, 0, 0, null);
		g2d.dispose();
		return dest;
	}
}