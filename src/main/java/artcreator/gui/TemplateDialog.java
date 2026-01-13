package artcreator.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import artcreator.creator.port.Creator;
import artcreator.domain.port.MaterialType;
import artcreator.domain.port.TemplateConfiguration;

/**
 * Second step UI: configure template generation and preview original vs template side-by-side.
 */
public class TemplateDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final transient Creator creator;
	private final transient BufferedImage original;

	private BufferedImage lastPreview;
	private double zoomFactor = 1.0;
	private volatile long renderSeq = 0;

	private final JLabel originalLabel = new JLabel("Original");
	private final JLabel previewLabel = new JLabel("Preview");
	private final JPanel legendPanel = new JPanel();
	private final JLabel legendTitle = new JLabel("Legend");

	private final JComboBox<MaterialType> materialBox = new JComboBox<>(MaterialType.values());
	private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(40, 5, 500, 1));
	private final JLabel heightValue = new JLabel("-");
	private final JSpinner grayLevelsSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 32, 1));
	private final JSpinner spacingSpinner = new JSpinner(new SpinnerNumberModel(12, 4, 64, 1));

	private final JButton btnGenerate = new JButton("Generate");
	private final JButton btnZoomIn = new JButton("+");
	private final JButton btnZoomOut = new JButton("-");

	public TemplateDialog(CreatorFrame owner, Creator creator, Object currentImage) {
		super(owner, "Template Generation", true);
		this.creator = creator;
		if (!(currentImage instanceof BufferedImage)) {
			throw new IllegalArgumentException("Current image must be a BufferedImage");
		}
		this.original = (BufferedImage) currentImage;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(1100, 700);
		setLocationRelativeTo(owner);
		setLayout(new BorderLayout());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				// If the owner was disabled by a caller, re-enable it.
				owner.setEnabled(true);
				owner.toFront();
			}
		});

		JPanel configPanel = buildConfigPanel();
		JPanel previewPanel = buildPreviewPanel();

		add(configPanel, BorderLayout.NORTH);
		add(previewPanel, BorderLayout.CENTER);

		// init derived height and UI
		updateDerivedHeight();
		updateSpacingEnabled();
		updateLegend();
		// Avoid scaling large images on the EDT during construction.
		originalLabel.setText("Rendering...");
		previewLabel.setText("Click Generate to create a preview");
		SwingUtilities.invokeLater(this::applyZoom);

		// listeners
		widthSpinner.addChangeListener(e -> {
			updateDerivedHeight();
			updateLegend();
		});
		grayLevelsSpinner.addChangeListener(e -> updateLegend());
		spacingSpinner.addChangeListener(e -> updateLegend());
		materialBox.addActionListener(e -> {
			updateSpacingEnabled();
			updateLegend();
		});
		btnZoomIn.addActionListener(e -> { zoomFactor = Math.min(4.0, zoomFactor + 0.25); applyZoom(); });
		btnZoomOut.addActionListener(e -> { zoomFactor = Math.max(0.25, zoomFactor - 0.25); applyZoom(); });
		btnGenerate.addActionListener(e -> handleGenerate());
	}

	private JPanel buildConfigPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		panel.add(new JLabel("Material: "));
		panel.add(materialBox);
		panel.add(new JLabel("   Width(points): "));
		panel.add(widthSpinner);
		panel.add(new JLabel("   Height(points): "));
		panel.add(heightValue);
		panel.add(new JLabel("   Gray levels: "));
		panel.add(grayLevelsSpinner);
		panel.add(new JLabel("   Point spacing: "));
		panel.add(spacingSpinner);
		panel.add(new JLabel("   "));
		panel.add(btnGenerate);
		panel.add(new JLabel("   Zoom: "));
		panel.add(btnZoomOut);
		panel.add(btnZoomIn);

		return panel;
	}

	private JPanel buildPreviewPanel() {
		JPanel panel = new JPanel(new BorderLayout());

		JScrollPane leftScroll = new JScrollPane(originalLabel);
		JScrollPane rightScroll = new JScrollPane(previewLabel);
		leftScroll.setPreferredSize(new Dimension(520, 600));
		rightScroll.setPreferredSize(new Dimension(520, 600));

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
		split.setResizeWeight(0.5);

		// Fixed legend panel on the right (outside of the preview image)
		legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
		legendPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
		legendTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
		legendPanel.add(legendTitle);

		JScrollPane legendScroll = new JScrollPane(legendPanel);
		legendScroll.setPreferredSize(new Dimension(200, 600));
		legendScroll.setMinimumSize(new Dimension(180, 200));

		panel.add(split, BorderLayout.CENTER);
		panel.add(legendScroll, BorderLayout.EAST);
		return panel;
	}

	private void updateLegend() {
		// Rebuild legend content based on current config (levels/material/spacing)
		legendPanel.removeAll();
		legendTitle.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(legendTitle);
		JLabel spacerTop = new JLabel(" ");
		spacerTop.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(spacerTop);

		TemplateConfiguration cfg;
		try {
			cfg = currentConfig();
		} catch (Exception ex) {
			legendPanel.add(new JLabel("Invalid configuration"));
			legendPanel.revalidate();
			legendPanel.repaint();
			return;
		}

		JLabel material = new JLabel("Material: " + cfg.getMaterialType());
		material.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(material);
		JLabel raster = new JLabel("Raster: " + cfg.getWidth() + " x " + cfg.getHeight());
		raster.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(raster);
		JLabel levelsLabel = new JLabel("Graustufen: " + cfg.getColorCount());
		levelsLabel.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(levelsLabel);
		JLabel spacing = new JLabel(cfg.getMaterialType() == MaterialType.BUEGELPERLEN ? "Abstand: (fix)" : ("Abstand: " + cfg.getPointSpacing()));
		spacing.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(spacing);
		JLabel spacerMid = new JLabel(" ");
		spacerMid.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(spacerMid);

		int levelCount = cfg.getColorCount();
		for (int i = 0; i < levelCount; i++) {
			int gray = (int) Math.round(i * (255.0 / (levelCount - 1)));
			JPanel row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
			row.setBorder(new EmptyBorder(2, 0, 2, 0));
			row.setAlignmentX(LEFT_ALIGNMENT);
			JPanel swatch = new JPanel();
			swatch.setPreferredSize(new Dimension(18, 18));
			swatch.setMaximumSize(new Dimension(18, 18));
			swatch.setMinimumSize(new Dimension(18, 18));
			swatch.setBackground(new java.awt.Color(gray, gray, gray));
			swatch.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK));
			row.add(swatch);
			JLabel rowLabel = new JLabel("  " + (i + 1) + " = " + gray);
			rowLabel.setAlignmentX(LEFT_ALIGNMENT);
			row.add(rowLabel);
			legendPanel.add(row);
		}

		legendPanel.revalidate();
		legendPanel.repaint();
	}

	private void updateDerivedHeight() {
		int w = (int) widthSpinner.getValue();
		double ratio = (original.getHeight() <= 0) ? 1.0 : ((double) original.getHeight() / (double) original.getWidth());
		int h = Math.max(1, (int) Math.round(w * ratio));
		heightValue.setText(String.valueOf(h));
	}

	private void updateSpacingEnabled() {
		MaterialType m = (MaterialType) materialBox.getSelectedItem();
		boolean allowSpacing = m != MaterialType.BUEGELPERLEN;
		spacingSpinner.setEnabled(allowSpacing);
	}

	private TemplateConfiguration currentConfig() {
		MaterialType m = (MaterialType) materialBox.getSelectedItem();
		int w = (int) widthSpinner.getValue();
		int h = Integer.parseInt(heightValue.getText());
		int levels = (int) grayLevelsSpinner.getValue();
		int spacing = (int) spacingSpinner.getValue();
		if (m == MaterialType.BUEGELPERLEN) {
			// ignored, but keep a value
			spacing = 16;
		}
		return new TemplateConfiguration(m, w, h, levels, spacing);
	}

	private void handleGenerate() {
		btnGenerate.setEnabled(false);
		TemplateConfiguration cfg;
		try {
			cfg = currentConfig();
		} catch (Exception ex) {
			btnGenerate.setEnabled(true);
			JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Configuration", JOptionPane.WARNING_MESSAGE);
			return;
		}

		CompletableFuture.supplyAsync(() -> creator.generateTemplate(cfg)).thenAccept(preview -> {
			SwingUtilities.invokeLater(() -> {
				btnGenerate.setEnabled(true);
				if (preview instanceof BufferedImage) {
					lastPreview = (BufferedImage) preview;
					updateLegend();
					applyZoom();
				} else {
					JOptionPane.showMessageDialog(this, "Preview is not an image.", "Error", JOptionPane.ERROR_MESSAGE);
				}
			});
		}).exceptionally(ex -> {
			SwingUtilities.invokeLater(() -> {
				btnGenerate.setEnabled(true);
				String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
				JOptionPane.showMessageDialog(this, msg, "Generation Failed", JOptionPane.WARNING_MESSAGE);
			});
			return null;
		});
	}

	private void applyZoom() {
		final long seq = ++renderSeq;

		// Original
		originalLabel.setIcon(null);
		originalLabel.setText("Rendering...");
		renderScaledAsync(original, zoomFactor, seq, originalLabel);

		// Preview
		if (lastPreview != null) {
			previewLabel.setIcon(null);
			previewLabel.setText("Rendering...");
			renderScaledAsync(lastPreview, zoomFactor, seq, previewLabel);
		} else {
			previewLabel.setIcon(null);
			previewLabel.setText("Click Generate to create a preview");
		}
	}

	private void renderScaledAsync(BufferedImage img, double zoom, long seq, JLabel target) {
		CompletableFuture.supplyAsync(() -> scaleBuffered(img, zoom)).thenAccept(scaled -> {
			SwingUtilities.invokeLater(() -> {
				if (seq != renderSeq) {
					return;
				}
				target.setIcon(new ImageIcon(scaled));
				target.setText(null);
			});
		}).exceptionally(ex -> {
			SwingUtilities.invokeLater(() -> {
				if (seq != renderSeq) {
					return;
				}
				String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
				target.setIcon(null);
				target.setText("Render failed: " + msg);
			});
			return null;
		});
	}

	private BufferedImage scaleBuffered(BufferedImage img, double zoom) {
		// Protect the UI from trying to allocate absurdly large preview bitmaps.
		final int maxDim = 2500;
		int targetW = Math.max(1, (int) Math.round(img.getWidth() * zoom));
		int targetH = Math.max(1, (int) Math.round(img.getHeight() * zoom));
		if (targetW > maxDim || targetH > maxDim) {
			double s = Math.min(maxDim / (double) targetW, maxDim / (double) targetH);
			targetW = Math.max(1, (int) Math.floor(targetW * s));
			targetH = Math.max(1, (int) Math.floor(targetH * s));
		}
		BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.drawImage(img, 0, 0, targetW, targetH, null);
		g2.dispose();
		return out;
	}
}
