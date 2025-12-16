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

	private boolean croppingMode = false;

	@Override
	public void actionPerformed(ActionEvent e) {
		String command = ((JButton) e.getSource()).getText();

		// If we're in cropping mode, only allow Apply/Cancel actions
		if (croppingMode) {
			if ("Apply Crop".equals(command)) {
				String cropOp = myView.getSelectionCropOperation();
				if (cropOp == null) {
					JOptionPane.showMessageDialog(myView, "No selection made. Please select an area before applying.",
							"No Selection", JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				handleTransformation(cropOp, true);
				return;
			} else if ("Cancel Crop".equals(command)) {
				myView.clearSelection();
				exitCropMode();
				return;
			} else {
				JOptionPane.showMessageDialog(myView, "Finish cropping first (Apply or Cancel).", "Crop In Progress",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
		}

		// Normal (non-cropping) interactions
		if ("Import Image".equals(command)) {
			handleImport();
		} else if ("Rotate Left ↺".equals(command)) {
			handleTransformation("rotate_left", false);
		} else if ("Rotate Right ↻".equals(command)) {
			handleTransformation("rotate_right", false);
		} else if ("Mirror H".equals(command)) {
			handleTransformation("mirror_horizontal", false);
		} else if ("Mirror V".equals(command)) {
			handleTransformation("mirror_vertical", false);
		} else if ("Crop".equals(command)) {
			// Enter cropping mode: user can now click-and-drag, then Apply/Cancel
			croppingMode = true;
			myView.setCropMode(true);
			return;
		} else if ("Undo".equals(command)) {
			handleUndo();
		}
	}

	private void handleTransformation(String operation, boolean exitCropModeAfter) {
		// Run logic asynchronously
		CompletableFuture.supplyAsync(() -> {
			try {
				return myModel.applyTransformation(operation);
			} catch (IllegalStateException ex) {
				// If state is invalid (no image), this is caught here
				throw ex;
			}
		}).thenAccept(modifiedImage -> {
			// Update View
			SwingUtilities.invokeLater(() -> {
				myView.displayImage(modifiedImage);
				// clear any selection after applying the operation
				myView.clearSelection();
				if (exitCropModeAfter) {
					exitCropMode();
				}
			});
		}).exceptionally(ex -> {
			SwingUtilities.invokeLater(() -> {
				String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
				JOptionPane.showMessageDialog(myView,
						msg, "Error", JOptionPane.WARNING_MESSAGE);
			});
			return null;
		});
	}

	private void exitCropMode() {
		this.croppingMode = false;
		this.myView.setCropMode(false);
		this.myView.clearSelection();
	}

	private void handleUndo() {
		CompletableFuture.supplyAsync(() -> {
			try {
				return myModel.undoLastTransformation();
			} catch (IllegalStateException ex) {
				throw ex;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}).thenAccept(restored -> {
			SwingUtilities.invokeLater(() -> {
				myView.displayImage(restored);
			});
		}).exceptionally(ex -> {
			SwingUtilities.invokeLater(() -> {
				String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
				JOptionPane.showMessageDialog(myView,
						msg, "Undo Failed", JOptionPane.WARNING_MESSAGE);
			});
			return null;
		});
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