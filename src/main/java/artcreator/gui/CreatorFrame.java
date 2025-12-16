package artcreator.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.util.TooManyListenersException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
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
	private JLabel imageLabel = new JLabel("No image loaded", SwingConstants.CENTER);
	private JPanel buttonPanel = new JPanel();

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
		this.buttonPanel.add(this.btnImport);
		this.add(this.buttonPanel, BorderLayout.NORTH);

		// --- Center: Image Display ---
		// ScrollPane in case image is large
		JScrollPane scrollPane = new JScrollPane(this.imageLabel);
		this.add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Updates the main image view.
	 * Scales the image to fit the window height for better visibility.
	 */
	public void displayImage(Object imgObject) {
		if (imgObject instanceof java.awt.Image) {
			java.awt.Image img = (java.awt.Image) imgObject;

			// Simple scaling for display
			int height = 400;
			int width = -1; // keep aspect ratio
			Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);

			this.imageLabel.setText(""); // remove text
			this.imageLabel.setIcon(new ImageIcon(scaled));
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