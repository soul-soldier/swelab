package artcreator.creator.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

import artcreator.creator.port.Creator;
import artcreator.domain.port.Domain;
import artcreator.statemachine.port.State.S;
import artcreator.statemachine.port.StateMachine;

public class CreatorImpl implements Creator {

	private final StateMachine stateMachine;
	private final Domain domain;

	// We hold the current image in the logic
	private Object currentImage;
	// Keep up to 3 transformations in history for undo
	private final Deque<Object> transformationHistory = new ArrayDeque<>();
	private static final int MAX_HISTORY_SIZE = 3;

	public CreatorImpl(StateMachine stateMachine, Domain domain) {
		this.stateMachine = stateMachine;
		this.domain = domain;
	}

	@Override
	public Object importImage(String path) throws IllegalStateException {
		// 1. Validation (as per Sequence Diagram)
		if (this.stateMachine.getState() == S.Processing) {
			throw new IllegalStateException("System is busy processing. Cannot import now.");
		}

		try {
			// 2. Delegation to Domain
			Logger.getGlobal().log(Level.INFO, "Loading image from: {0}", path);
			Object newImage = domain.loadImage(path);

			// 3. State Update
			this.currentImage = newImage;
			this.stateMachine.setState(S.ImageLoaded);

			return this.currentImage;

		} catch (Exception e) {
			// Logging and re-throwing
			Logger.getGlobal().log(Level.SEVERE, "Import failed", e);
			throw new RuntimeException("Could not import image: " + e.getMessage(), e);
		}
	}

	@Override
	public Object applyTransformation(Object config) throws IllegalStateException {
		// 1. Validate State (Must have an image loaded)
		// We check if we are in ImageLoaded OR TemplateReady (as you might want to adjust after generation)
		// But strictly per diagram: ImageLoaded
		if (!this.stateMachine.getState().isSubStateOf(S.ImageLoaded)) {
			throw new IllegalStateException("No image loaded to transform.");
		}

		if (this.currentImage == null) {
			throw new IllegalStateException("Internal Error: Image reference is null despite valid state.");
		}

		String operation = (String) config; // "rotate_left" or "rotate_right"

		try {
			// 2. Save current image to history (maintaining up to MAX_HISTORY_SIZE entries)
			this.transformationHistory.push(this.currentImage);
			if (this.transformationHistory.size() > MAX_HISTORY_SIZE) {
				this.transformationHistory.removeLast();
			}
			Logger.getGlobal().log(Level.INFO, "Applying transformation: {0}", operation);
			Object modifiedImage = domain.transformImage(this.currentImage, operation);

			// 3. Update Internal State
			this.currentImage = modifiedImage;

			// 4. Trigger UI Update (Notify observers)
			// Even though the enum S.ImageLoaded hasn't changed, re-setting it forces notify()
			this.stateMachine.setState(S.ImageLoaded);

			return this.currentImage;

		} catch (Exception e) {
			Logger.getGlobal().log(Level.SEVERE, "Transformation failed", e);
			throw new RuntimeException("Transformation failed: " + e.getMessage());
		}
	}

	@Override
	public Object undoLastTransformation() throws IllegalStateException {
		if (this.transformationHistory.isEmpty()) {
			throw new IllegalStateException("No transformation to undo.");
		}
		try {
			Object restored = this.transformationHistory.pop();
			this.currentImage = restored;
			// notify UI/state
			this.stateMachine.setState(S.ImageLoaded);
			return this.currentImage;
		} catch (Exception e) {
			throw new RuntimeException("Undo failed: " + e.getMessage(), e);
		}
	}

	@Override
	public Object generateTemplate(Object config) { return null; }
}