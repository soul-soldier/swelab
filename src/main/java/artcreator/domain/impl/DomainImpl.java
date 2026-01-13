package artcreator.domain.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import artcreator.domain.port.TemplateConfiguration;
import artcreator.domain.port.TemplateResult;

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

		String op = operation.toLowerCase();
		switch (op) {
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
			case "crop_center":
				// center crop: take half width/height centered
				int cw = Math.max(1, src.getWidth() / 2);
				int ch = Math.max(1, src.getHeight() / 2);
				int cx = Math.max(0, (src.getWidth() - cw) / 2);
				int cy = Math.max(0, (src.getHeight() - ch) / 2);
				return src.getSubimage(cx, cy, cw, ch);
			default:
				// support crop with explicit params: "crop:x,y,w,h"
				if (op.startsWith("crop:")) {
					try {
						String[] parts = operation.substring(operation.indexOf(':') + 1).split(",");
						if (parts.length == 4) {
							int x = Integer.parseInt(parts[0].trim());
							int y = Integer.parseInt(parts[1].trim());
							int w2 = Integer.parseInt(parts[2].trim());
							int h2 = Integer.parseInt(parts[3].trim());
							// clamp
							x = Math.max(0, Math.min(x, src.getWidth() - 1));
							y = Math.max(0, Math.min(y, src.getHeight() - 1));
							w2 = Math.max(1, Math.min(w2, src.getWidth() - x));
							h2 = Math.max(1, Math.min(h2, src.getHeight() - y));
							return src.getSubimage(x, y, w2, h2);
						}
					} catch (Exception ex) {
						throw new IllegalArgumentException("Invalid crop parameters: " + ex.getMessage(), ex);
					}
				}
				throw new IllegalArgumentException("Unknown transformation: " + operation);
		}
	}

	public Object generateTemplate(Object imageObj, Object templateConfig) {
		if (!(imageObj instanceof BufferedImage)) {
			throw new IllegalArgumentException("Invalid image object provided.");
		}
		if (!(templateConfig instanceof TemplateConfiguration)) {
			throw new IllegalArgumentException("Invalid template configuration.");
		}
		BufferedImage src = (BufferedImage) imageObj;
		TemplateConfiguration cfg = (TemplateConfiguration) templateConfig;

		// 1) Scale image to raster resolution
		BufferedImage scaled = scale(src, cfg.getWidth(), cfg.getHeight());

		// 2) Reduce color palette to cfg.colorCount main colors, then assign each point
		// to its nearest palette color.
		QuantizedRaster qr = quantizeToPalette(scaled, cfg.getColorCount());
		BufferedImage preview = renderToothpickPreview(cfg, qr);
		return new TemplateResult(preview, qr.palette);
	}

	private BufferedImage scale(BufferedImage src, int targetW, int targetH) {
		BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, targetW, targetH);
		g2.drawImage(src, 0, 0, targetW, targetH, null);
		g2.dispose();
		return out;
	}

	private static final class QuantizedRaster {
		private final int width;
		private final int height;
		private final int[] assignment; // length = width*height, palette index per cell
		private final Color[] palette;

		private QuantizedRaster(int width, int height, int[] assignment, Color[] palette) {
			this.width = width;
			this.height = height;
			this.assignment = assignment;
			this.palette = palette;
		}
	}

	private QuantizedRaster quantizeToPalette(BufferedImage scaled, int requestedColors) {
		int w = scaled.getWidth();
		int h = scaled.getHeight();
		int pixelCount = w * h;
		int[] rgb = new int[pixelCount];
		scaled.getRGB(0, 0, w, h, rgb, 0, w);

		int k = Math.max(2, Math.min(32, requestedColors));
		k = Math.min(k, pixelCount);

		// Sample pixels to keep k-means fast for larger rasters.
		int maxSamples = 10_000;
		int sampleCount = Math.min(pixelCount, maxSamples);
		int[] samples = new int[sampleCount];
		if (sampleCount == pixelCount) {
			System.arraycopy(rgb, 0, samples, 0, sampleCount);
		} else {
			Random rnd = new Random(1337);
			for (int i = 0; i < sampleCount; i++) {
				samples[i] = rgb[rnd.nextInt(pixelCount)];
			}
		}

		int[] centers = kmeansPlusPlus(samples, k);
		centers = kmeans(samples, centers, 15);

		Color[] palette = new Color[centers.length];
		for (int i = 0; i < centers.length; i++) {
			palette[i] = new Color((centers[i] >> 16) & 0xFF, (centers[i] >> 8) & 0xFF, centers[i] & 0xFF);
		}

		int[] assignment = new int[pixelCount];
		for (int i = 0; i < pixelCount; i++) {
			assignment[i] = nearestCenterIndex(rgb[i], centers);
		}

		return new QuantizedRaster(w, h, assignment, palette);
	}

	private int[] kmeansPlusPlus(int[] samples, int k) {
		Random rnd = new Random(1337);
		List<Integer> centers = new ArrayList<>();
		centers.add(samples[rnd.nextInt(samples.length)]);

		double[] dist2 = new double[samples.length];
		for (int c = 1; c < k; c++) {
			int[] currentCenters = toIntArray(centers);
			double sum = 0.0;
			for (int i = 0; i < samples.length; i++) {
				int rgb = samples[i];
				int nearest = nearestCenterIndex(rgb, currentCenters);
				int centerRgb = currentCenters[nearest];
				double d = colorDist2(rgb, centerRgb);
				dist2[i] = d;
				sum += d;
			}
			if (sum <= 0.0) {
				centers.add(samples[rnd.nextInt(samples.length)]);
				continue;
			}
			double r = rnd.nextDouble() * sum;
			double acc = 0.0;
			int chosen = samples[rnd.nextInt(samples.length)];
			for (int i = 0; i < samples.length; i++) {
				acc += dist2[i];
				if (acc >= r) {
					chosen = samples[i];
					break;
				}
			}
			centers.add(chosen);
		}
		return toIntArray(centers);
	}

	private int[] kmeans(int[] samples, int[] centers, int maxIters) {
		int k = centers.length;
		int[] assignments = new int[samples.length];
		for (int iter = 0; iter < maxIters; iter++) {
			long[] sumR = new long[k];
			long[] sumG = new long[k];
			long[] sumB = new long[k];
			int[] count = new int[k];

			boolean changed = false;
			for (int i = 0; i < samples.length; i++) {
				int rgb = samples[i];
				int idx = nearestCenterIndex(rgb, centers);
				if (assignments[i] != idx) {
					assignments[i] = idx;
					changed = true;
				}
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;
				sumR[idx] += r;
				sumG[idx] += g;
				sumB[idx] += b;
				count[idx]++;
			}

			for (int c = 0; c < k; c++) {
				if (count[c] == 0) {
					continue;
				}
				int r = (int) Math.round(sumR[c] / (double) count[c]);
				int g = (int) Math.round(sumG[c] / (double) count[c]);
				int b = (int) Math.round(sumB[c] / (double) count[c]);
				centers[c] = ((clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b));
			}

			if (!changed && iter > 0) {
				break;
			}
		}
		return centers;
	}

	private int nearestCenterIndex(int rgb, int[] centers) {
		int best = 0;
		double bestD = Double.POSITIVE_INFINITY;
		for (int i = 0; i < centers.length; i++) {
			double d = colorDist2(rgb, centers[i]);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	private double colorDist2(int a, int b) {
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		int dr = ar - br;
		int dg = ag - bg;
		int db = ab - bb;
		return (double) dr * dr + (double) dg * dg + (double) db * db;
	}

	private int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private int[] toIntArray(List<Integer> list) {
		int[] out = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			out[i] = list.get(i);
		}
		return out;
	}

	private BufferedImage renderToothpickPreview(TemplateConfiguration cfg, QuantizedRaster qr) {
		int rasterW = qr.width;
		int rasterH = qr.height;

		// MVP toggle: keep grid/raster drawing code, but don't show it for now.
		final boolean showRasterLines = false;

		// Preview rendering for Toothpicks: keep a square raster cell, and show the
		// toothpick position as a colored circle in the center. pointSpacing controls
		// the gap BETWEEN cells.
		int cellSize = 12;
		int gap = Math.max(0, cfg.getPointSpacing());
		int step = cellSize + gap;
		int padding = 8;
		int outW = padding + (rasterW * cellSize) + Math.max(0, (rasterW - 1) * gap) + padding;
		int outH = padding + (rasterH * cellSize) + Math.max(0, (rasterH - 1) * gap) + padding;

		BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = out.createGraphics();
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, outW, outH);

		// Draw pixel raster (no numbers, no legend)
		for (int y = 0; y < rasterH; y++) {
			for (int x = 0; x < rasterW; x++) {
				int paletteIndex = qr.assignment[y * rasterW + x];
				Color color = qr.palette[Math.max(0, Math.min(qr.palette.length - 1, paletteIndex))];
				int px = padding + x * step;
				int py = padding + y * step;
				// Keep the raster squares uncolored; the toothpick marker carries the color.
				g2.setColor(Color.WHITE);
				g2.fillRect(px, py, cellSize, cellSize);
				if (showRasterLines) {
					g2.setColor(Color.LIGHT_GRAY);
					g2.drawRect(px, py, cellSize, cellSize);
				}

				// Toothpick placement marker: centered colored circle with contrasting outline.
				// Make the marker take up most of the cell.
				int markerDiameter = Math.max(4, (int) Math.round(cellSize * 0.90));
				int cx = px + (cellSize / 2);
				int cy = py + (cellSize / 2);
				int mx = cx - (markerDiameter / 2);
				int my = cy - (markerDiameter / 2);
				g2.setColor(color);
				g2.fillOval(mx, my, markerDiameter, markerDiameter);
				// Use a consistent outline so all circles look the same size.
				g2.setColor(Color.BLACK);
				g2.drawOval(mx, my, markerDiameter, markerDiameter);
			}
		}

		g2.dispose();
		return out;
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