package artcreator.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.CompletableFuture;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import artcreator.creator.port.Creator;
import artcreator.statemachine.port.Observer;
import artcreator.statemachine.port.State;
import artcreator.statemachine.port.Subject;

public class Controller implements ActionListener, Observer {

	private CreatorFrame myView;
	private Creator myModel;
	private Subject subject;

	public Controller(CreatorFrame view, Subject subject, Creator model) {
		this.myView = view;
		this.myModel = model;
		this.subject = subject;
		this.subject.attach(this);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String command = ((JButton) e.getSource()).getText();

		if ("Import Image".equals(command)) {
			handleImport();
		}
	}

	private void handleImport() {
		// 1. Open File Chooser (Must be on GUI Thread)
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Source Image");
		chooser.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG)", "jpg", "jpeg", "png"));

		int result = chooser.showOpenDialog(myView);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = chooser.getSelectedFile();
			String path = selectedFile.getAbsolutePath();

			// 2. Call Logic (Async to prevent freezing GUI during loading)
			CompletableFuture.supplyAsync(() -> {
				try {
					// Call the interface defined in our Sequence Diagram
					return myModel.importImage(path);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}).thenAccept(loadedImage -> {
				// 3. Update View (Must be back on Swing Thread)
				SwingUtilities.invokeLater(() -> {
					myView.displayImage(loadedImage);
				});
			}).exceptionally(ex -> {
				// Error Handling
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(myView,
							"Error importing image: " + ex.getCause().getMessage(),
							"Import Failed",
							JOptionPane.ERROR_MESSAGE);
				});
				return null;
			});
		}
	}

	@Override
	public void update(State newState) {
		// Controller Logic for state changes (if needed)
	}
}