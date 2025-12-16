package artcreator.gui;

import java.awt.BorderLayout;
import java.util.TooManyListenersException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import artcreator.creator.CreatorFactory;
import artcreator.creator.port.Creator;
import artcreator.statemachine.StateMachineFactory;
import artcreator.statemachine.port.Observer;
import artcreator.statemachine.port.State;
import artcreator.statemachine.port.State.S;
import artcreator.statemachine.port.Subject;

public class CreatorFrame extends JFrame implements Observer {

	private static final long serialVersionUID = 1L;

	// Logic connections
	private transient Creator creator = CreatorFactory.FACTORY.creator();
	private transient Subject subject = StateMachineFactory.FACTORY.subject();
	private transient Controller controller;

	// UI Components
	private JButton btnImport = new JButton("Import Image");
	private SelectableImageLabel imageLabel = new SelectableImageLabel();
	private JPanel buttonPanel = new JPanel();
	// crop-mode buttons
	private JButton btnApplyCrop = new JButton("Apply Crop");
	private JButton btnCancelCrop = new JButton("Cancel Crop");

	private JButton btnRotateLeft = new JButton("Rotate Left ↺");
	private JButton btnRotateRight = new JButton("Rotate Right ↻");
	private JButton btnMirrorH = new JButton("Mirror H");
	private JButton btnMirrorV = new JButton("Mirror V");
	private JButton btnCropCenter = new JButton("Crop");
	private JButton btnUndo = new JButton("Undo");

	public CreatorFrame() throws TooManyListenersException {
		super("ArtCreator 3D");
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(800, 600); // Slightly larger for image viewing
		this.setLocationRelativeTo(null);
		this.setLayout(new BorderLayout());

		// Observe State
		this.subject.attach(this);
		this.controller = new Controller(this, subject, creator);

		// --- Top: Buttons ---
		this.btnImport.addActionListener(this.controller);
		this.btnRotateLeft.addActionListener(this.controller);
		this.btnRotateRight.addActionListener(this.controller);
		this.btnMirrorH.addActionListener(this.controller);
		this.btnMirrorV.addActionListener(this.controller);
		this.btnCropCenter.addActionListener(this.controller);
		this.btnApplyCrop.addActionListener(this.controller);
		this.btnCancelCrop.addActionListener(this.controller);
		this.btnUndo.addActionListener(this.controller);

		this.buttonPanel.add(this.btnImport);
		this.buttonPanel.add(this.btnRotateLeft);
		this.buttonPanel.add(this.btnRotateRight);
		this.buttonPanel.add(this.btnMirrorH);
		this.buttonPanel.add(this.btnMirrorV);
		this.buttonPanel.add(this.btnCropCenter);
		this.buttonPanel.add(this.btnApplyCrop);
		this.buttonPanel.add(this.btnCancelCrop);
		this.buttonPanel.add(this.btnUndo);

		this.add(this.buttonPanel, BorderLayout.NORTH);

		// --- Center: Image Display ---
		// Place imageLabel in a panel that centers its child so image appears centered
		// in the scrollpane
		JPanel imagePanel = new JPanel(new java.awt.GridBagLayout());
		imagePanel.add(this.imageLabel);
		JScrollPane scrollPane = new JScrollPane(imagePanel);
		this.add(scrollPane, BorderLayout.CENTER);

		// Crop buttons hidden by default
		this.btnApplyCrop.setVisible(false);
		this.btnCancelCrop.setVisible(false);
	}

	/**
	 * Updates the main image view.
	 * Scales the image to fit the window height for better visibility.
	 */
	public void displayImage(Object imgObject) {
		if (imgObject instanceof java.awt.Image) {
			java.awt.Image img = (java.awt.Image) imgObject;
			this.imageLabel.setImage(img);
		} else {
			this.imageLabel.clearImage();
		}
	}

	/**
	 * Returns the crop operation string based on the current selection, or null if
	 * none.
	 * Format: "crop:x,y,w,h"
	 */
	public String getSelectionCropOperation() {
		int[] sel = this.imageLabel.getSelectionInOriginalCoords();
		if (sel == null)
			return null;
		return String.format("crop:%d,%d,%d,%d", sel[0], sel[1], sel[2], sel[3]);
	}

	public void clearSelection() {
		this.imageLabel.clearSelection();
	}

	/**
	 * Enable or disable crop mode in the UI. While crop mode is enabled, other
	 * buttons
	 * should be considered inactive by the controller.
	 */
	public void setCropMode(boolean enabled) {
		this.btnApplyCrop.setVisible(enabled);
		this.btnCancelCrop.setVisible(enabled);
		// keep Start Crop visible but disable it while in crop mode
		this.btnCropCenter.setEnabled(!enabled);
		// visually hint selection is active by setting cursor
		this.imageLabel.setCursor(enabled ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR)
				: java.awt.Cursor.getDefaultCursor());
	}

	/**
	 * Inner label supporting click+drag selection overlay and mapping to original
	 * image coords.
	 */
	private static class SelectableImageLabel extends JLabel {
		private static final long serialVersionUID = 1L;
		private java.awt.Image image;
		private int imgW, imgH; // original image size
		private int dispW, dispH; // displayed size

		// selection in displayed coordinates
		private int sx = -1, sy = -1, ex = -1, ey = -1;

		public SelectableImageLabel() {
			super();
			setOpaque(true);
			java.awt.event.MouseAdapter ma = new java.awt.event.MouseAdapter() {
				@Override
				public void mousePressed(java.awt.event.MouseEvent e) {
					if (image == null)
						return;
					sx = clamp(e.getX(), 0, getWidth() - 1);
					sy = clamp(e.getY(), 0, getHeight() - 1);
					ex = sx;
					ey = sy;
					repaint();
				}

				@Override
				public void mouseDragged(java.awt.event.MouseEvent e) {
					if (image == null || sx < 0)
						return;
					ex = clamp(e.getX(), 0, getWidth() - 1);
					ey = clamp(e.getY(), 0, getHeight() - 1);
					repaint();
				}

				@Override
				public void mouseReleased(java.awt.event.MouseEvent e) {
					if (image == null)
						return;
					if (sx < 0)
						return;
					ex = clamp(e.getX(), 0, getWidth() - 1);
					ey = clamp(e.getY(), 0, getHeight() - 1);
					repaint();
				}
			};
			addMouseListener(ma);
			addMouseMotionListener(ma);
		}

		private int clamp(int v, int a, int b) {
			return Math.max(a, Math.min(b, v));
		}

		public void setImage(java.awt.Image img) {
			this.image = img;
			this.imgW = img.getWidth(null);
			this.imgH = img.getHeight(null);
			// compute displayed size: fit height 400 (same heuristic as before)
			int targetH = 400;
			double scale = (imgH > 0) ? ((double) targetH / imgH) : 1.0;
			this.dispH = Math.max(1, (int) Math.round(imgH * scale));
			this.dispW = Math.max(1, (int) Math.round(imgW * scale));
			this.setPreferredSize(new java.awt.Dimension(this.dispW, this.dispH));
			// create scaled instance
			this.image = img.getScaledInstance(this.dispW, this.dispH, java.awt.Image.SCALE_SMOOTH);
			// reset selection
			clearSelection();
			revalidate();
			repaint();
		}

		public void clearImage() {
			this.image = null;
			this.imgW = this.imgH = this.dispW = this.dispH = 0;
			clearSelection();
			repaint();
		}

		public void clearSelection() {
			this.sx = this.sy = this.ex = this.ey = -1;
			repaint();
		}

		@Override
		protected void paintComponent(java.awt.Graphics g) {
			super.paintComponent(g);
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
			if (this.image != null) {
				g2.drawImage(this.image, 0, 0, this);
			}
			if (sx >= 0 && ex >= 0) {
				int x = Math.min(sx, ex);
				int y = Math.min(sy, ey);
				int w = Math.abs(ex - sx);
				int h = Math.abs(ey - sy);
				g2.setColor(new java.awt.Color(0, 0, 0, 80));
				g2.fillRect(x, y, w, h);
				g2.setColor(java.awt.Color.YELLOW);
				g2.drawRect(x, y, w, h);
			}
			g2.dispose();
		}

		/**
		 * Returns selection mapped to original image coordinates as int[]{x,y,w,h}
		 * or null if no valid selection.
		 */
		public int[] getSelectionInOriginalCoords() {
			if (image == null || sx < 0)
				return null;
			int x = Math.min(sx, ex);
			int y = Math.min(sy, ey);
			int w = Math.abs(ex - sx);
			int h = Math.abs(ey - sy);
			if (w <= 0 || h <= 0)
				return null;
			double scaleX = (double) imgW / (double) dispW;
			double scaleY = (double) imgH / (double) dispH;
			int ox = (int) Math.round(x * scaleX);
			int oy = (int) Math.round(y * scaleY);
			int ow = (int) Math.round(w * scaleX);
			int oh = (int) Math.round(h * scaleY);
			// clamp
			ox = Math.max(0, Math.min(ox, imgW - 1));
			oy = Math.max(0, Math.min(oy, imgH - 1));
			ow = Math.max(1, Math.min(ow, imgW - ox));
			oh = Math.max(1, Math.min(oh, imgH - oy));
			return new int[] { ox, oy, ow, oh };
		}
	}

	@Override
	public void update(State newState) {
		// Example: Enable/Disable buttons based on state
		System.out.println("GUI: State changed to " + newState);

		// If we are processing, disable import
		this.btnImport.setEnabled(newState != S.Processing);
	}
}