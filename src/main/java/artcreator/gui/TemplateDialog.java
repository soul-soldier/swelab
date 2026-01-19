package artcreator.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import artcreator.creator.port.Creator;
import artcreator.domain.port.MaterialType;
import artcreator.domain.port.TemplateConfiguration;
import artcreator.domain.port.TemplateResult;

/**
 * Second step UI: configure template generation and preview original vs template side-by-side.
 */
public class TemplateDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final transient Creator creator;
	private final transient BufferedImage original;

	private BufferedImage lastPreview;
	private Color[] lastPalette;

	// Zoom factor: 1.0 = 100% size of the ORIGINAL image
	private double zoomFactor = 1.0;
	private volatile long renderSeq = 0;

	private final JLabel originalLabel = new JLabel("Original");
	private final JLabel previewLabel = new JLabel("Preview");

	// Scroll panes defined here so we can sync them
	private final JScrollPane leftScroll = new JScrollPane(originalLabel);
	private final JScrollPane rightScroll = new JScrollPane(previewLabel);

	private final JPanel legendPanel = new JPanel();
	private final JLabel legendTitle = new JLabel("Legend");

	private final JComboBox<MaterialType> materialBox = new JComboBox<>(
			new DefaultComboBoxModel<>(new MaterialType[] { MaterialType.TOOTHPICKS }));
	private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(40, 5, 500, 1));
	private final JLabel heightValue = new JLabel("-");
	private final JSpinner grayLevelsSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 32, 1));
	private final JSpinner spacingSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 64, 1));

	private final JButton btnGenerate = new JButton("Generate");
	private final JButton btnZoomIn = new JButton("+");
	private final JButton btnZoomOut = new JButton("-");
	private final JButton btnFit = new JButton("Fit");

	public TemplateDialog(CreatorFrame owner, Creator creator, Object currentImage) {
		super(owner, "Template Generation", true);
		this.creator = creator;
		if (!(currentImage instanceof BufferedImage)) {
			throw new IllegalArgumentException("Current image must be a BufferedImage");
		}
		this.original = (BufferedImage) currentImage;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		// Start with a reasonably large window
		setSize(1200, 800);
		setLocationRelativeTo(owner);
		setLayout(new BorderLayout());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				owner.setEnabled(true);
				owner.toFront();
			}
		});

		JPanel configPanel = buildConfigPanel();
		JPanel previewPanel = buildPreviewPanel();

		add(configPanel, BorderLayout.NORTH);
		add(previewPanel, BorderLayout.CENTER);

		// Calculate initial height based on aspect ratio
		updateDerivedHeight();
		updateLegend();

		originalLabel.setText("Rendering...");
		originalLabel.setHorizontalAlignment(JLabel.CENTER);
		previewLabel.setText("Click Generate to create a preview");
		previewLabel.setHorizontalAlignment(JLabel.CENTER);

		// Sync the scrolling of the two panes
		syncScrollBars(leftScroll, rightScroll);

		// Initial "Auto Fit" Zoom
		SwingUtilities.invokeLater(() -> {
			calculateInitialZoom();
			applyZoom();
		});

		// Listeners
		widthSpinner.addChangeListener(e -> {
			updateDerivedHeight();
			lastPalette = null;
			updateLegend();
		});
		grayLevelsSpinner.addChangeListener(e -> {
			lastPalette = null;
			updateLegend();
		});
		spacingSpinner.addChangeListener(e -> {
			lastPalette = null;
			updateLegend();
		});
		materialBox.addActionListener(e -> updateLegend());

		btnZoomIn.addActionListener(e -> {
			zoomFactor = Math.min(10.0, zoomFactor * 1.25);
			applyZoom();
		});
		btnZoomOut.addActionListener(e -> {
			zoomFactor = Math.max(0.05, zoomFactor * 0.8);
			applyZoom();
		});
		btnFit.addActionListener(e -> {
			calculateInitialZoom();
			applyZoom();
		});

		btnGenerate.addActionListener(e -> handleGenerate());
	}

	/**
	 * Calculates a zoom factor so the images fit entirely within the current scroll pane viewports.
	 */
	private void calculateInitialZoom() {
		int availW = (leftScroll.getWidth() > 0 ? leftScroll.getWidth() : 500) - 20;
		int availH = (leftScroll.getHeight() > 0 ? leftScroll.getHeight() : 600) - 20;

		double wRatio = (double) availW / original.getWidth();
		double hRatio = (double) availH / original.getHeight();

		// Take the smaller ratio to ensure it fits both ways
		this.zoomFactor = Math.min(wRatio, hRatio);

		// Don't let it get microscopic (min 5%)
		this.zoomFactor = Math.max(0.05, this.zoomFactor);
	}

	/**
	 * Binds the scrollbars of two JScrollPanes so moving one moves the other.
	 */
	private void syncScrollBars(JScrollPane sp1, JScrollPane sp2) {
		// Horizontal
		AdjustmentListener hListener = new SyncScroller(sp1.getHorizontalScrollBar(), sp2.getHorizontalScrollBar());
		sp1.getHorizontalScrollBar().addAdjustmentListener(hListener);
		sp2.getHorizontalScrollBar().addAdjustmentListener(hListener);

		// Vertical
		AdjustmentListener vListener = new SyncScroller(sp1.getVerticalScrollBar(), sp2.getVerticalScrollBar());
		sp1.getVerticalScrollBar().addAdjustmentListener(vListener);
		sp2.getVerticalScrollBar().addAdjustmentListener(vListener);
	}

	/**
	 * Helper class to prevent recursive scroll events
	 */
	private static class SyncScroller implements AdjustmentListener {
		private JScrollBar b1;
		private JScrollBar b2;
		private boolean isAdjusting = false;

		public SyncScroller(JScrollBar b1, JScrollBar b2) {
			this.b1 = b1;
			this.b2 = b2;
		}

		@Override
		public void adjustmentValueChanged(AdjustmentEvent e) {
			if (isAdjusting) return;
			JScrollBar source = (JScrollBar) e.getSource();
			JScrollBar target = (source == b1) ? b2 : b1;

			isAdjusting = true;
			target.setValue(source.getValue());
			isAdjusting = false;
		}
	}

	private JPanel buildConfigPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		panel.add(new JLabel("Material: "));
		panel.add(materialBox);
		panel.add(new JLabel("   Width(pts): "));
		panel.add(widthSpinner);
		panel.add(new JLabel("   Height(pts): "));
		panel.add(heightValue);
		panel.add(new JLabel("   Colors: "));
		panel.add(grayLevelsSpinner);
		panel.add(new JLabel("   Spacing: "));
		panel.add(spacingSpinner);
		panel.add(new JLabel("   "));
		panel.add(btnGenerate);

		panel.add(new JLabel("      Zoom: "));
		panel.add(btnZoomOut);
		panel.add(btnFit);
		panel.add(btnZoomIn);

		return panel;
	}

	private JPanel buildPreviewPanel() {
		JPanel panel = new JPanel(new BorderLayout());

		leftScroll.setPreferredSize(new Dimension(520, 600));
		rightScroll.setPreferredSize(new Dimension(520, 600));

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
		split.setResizeWeight(0.5);

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
		legendPanel.removeAll();
		legendTitle.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(legendTitle);

		TemplateConfiguration cfg;
		try {
			cfg = currentConfig();
		} catch (Exception ex) {
			legendPanel.add(new JLabel("Invalid configuration"));
			legendPanel.revalidate();
			legendPanel.repaint();
			return;
		}

		addLegendLabel("Material: " + cfg.getMaterialType());
		addLegendLabel("Raster: " + cfg.getWidth() + " x " + cfg.getHeight());
		addLegendLabel("Total Points: " + (cfg.getWidth() * cfg.getHeight()));
		addLegendLabel("Colors: " + cfg.getColorCount());
		addLegendLabel("Spacing: " + cfg.getPointSpacing());
		legendPanel.add(new JLabel(" "));

		if (lastPalette == null || lastPalette.length == 0) {
			addLegendLabel("Generate to see palette");
		} else {
			for (int i = 0; i < lastPalette.length; i++) {
				Color c = lastPalette[i];
				JPanel row = new JPanel();
				row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
				row.setBorder(new EmptyBorder(2, 0, 2, 0));
				row.setAlignmentX(LEFT_ALIGNMENT);

				JPanel swatch = new JPanel();
				swatch.setPreferredSize(new Dimension(18, 18));
				swatch.setMaximumSize(new Dimension(18, 18));
				swatch.setBackground(c);
				swatch.setBorder(BorderFactory.createLineBorder(Color.BLACK));

				row.add(swatch);
				String hex = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
				row.add(new JLabel("  " + (i + 1) + " = " + hex));
				legendPanel.add(row);
			}
		}

		legendPanel.revalidate();
		legendPanel.repaint();
	}

	private void addLegendLabel(String text) {
		JLabel l = new JLabel(text);
		l.setAlignmentX(LEFT_ALIGNMENT);
		legendPanel.add(l);
	}

	private void updateDerivedHeight() {
		int w = (int) widthSpinner.getValue();
		double ratio = (original.getHeight() <= 0) ? 1.0 : ((double) original.getHeight() / (double) original.getWidth());
		int h = Math.max(1, (int) Math.round(w * ratio));
		heightValue.setText(String.valueOf(h));
	}

	private TemplateConfiguration currentConfig() {
		MaterialType m = (MaterialType) materialBox.getSelectedItem();
		int w = (int) widthSpinner.getValue();
		int h = Integer.parseInt(heightValue.getText());
		int levels = (int) grayLevelsSpinner.getValue();
		int spacing = (int) spacingSpinner.getValue();
		return new TemplateConfiguration(m, w, h, levels, spacing);
	}

	private void handleGenerate() {
		btnGenerate.setEnabled(false);
		TemplateConfiguration cfg;
		try {
			cfg = currentConfig();
		} catch (Exception ex) {
			btnGenerate.setEnabled(true);
			JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Config", JOptionPane.WARNING_MESSAGE);
			return;
		}

		CompletableFuture.supplyAsync(() -> creator.generateTemplate(cfg)).thenAccept(preview -> {
			SwingUtilities.invokeLater(() -> {
				btnGenerate.setEnabled(true);
				if (preview instanceof TemplateResult) {
					TemplateResult result = (TemplateResult) preview;
					lastPreview = result.getPreview();
					lastPalette = result.getPalette();
					updateLegend();
					applyZoom();
				} else if (preview instanceof BufferedImage) {
					lastPreview = (BufferedImage) preview;
					lastPalette = null;
					updateLegend();
					applyZoom();
				} else {
					JOptionPane.showMessageDialog(this, "Unknown result type", "Error", JOptionPane.ERROR_MESSAGE);
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

	/**
	 * Main method to update the images on screen based on the zoom factor.
	 * CRITICAL: Ensures both images are rendered at the exact same pixel dimensions.
	 */
	private void applyZoom() {
		final long seq = ++renderSeq;

		// 1. Calculate the target display dimensions based on the ORIGINAL image.
		int targetW = (int) (original.getWidth() * zoomFactor);
		int targetH = (int) (original.getHeight() * zoomFactor);

		// Safety clamp
		if (targetW < 1) targetW = 1;
		if (targetH < 1) targetH = 1;

		// 2. Render Original
		originalLabel.setIcon(null);
		originalLabel.setText("Rendering...");
		renderScaledAsync(original, targetW, targetH, seq, originalLabel);

		// 3. Render Preview (forced to match Original's dimensions)
		if (lastPreview != null) {
			previewLabel.setIcon(null);
			previewLabel.setText("Rendering...");
			renderScaledAsync(lastPreview, targetW, targetH, seq, previewLabel);
		} else {
			previewLabel.setIcon(null);
			previewLabel.setText("Click Generate");
		}
	}

	private void renderScaledAsync(BufferedImage img, int w, int h, long seq, JLabel target) {
		CompletableFuture.supplyAsync(() -> scaleExact(img, w, h)).thenAccept(scaled -> {
			SwingUtilities.invokeLater(() -> {
				if (seq != renderSeq) return;
				target.setIcon(new ImageIcon(scaled));
				target.setText(null);
				// Force scrollbars to update if dimensions changed drastically
				target.revalidate();
			});
		}).exceptionally(ex -> {
			SwingUtilities.invokeLater(() -> {
				if (seq != renderSeq) return;
				target.setIcon(null);
				target.setText("Error");
			});
			return null;
		});
	}

	/**
	 * Scales an image to an exact width/height.
	 */
	private BufferedImage scaleExact(BufferedImage img, int targetW, int targetH) {
		BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.drawImage(img, 0, 0, targetW, targetH, null);
		g2.dispose();
		return out;
	}
}