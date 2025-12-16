package artcreator.creator.impl;

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

	// Stub implementations for other methods to satisfy interface
	@Override
	public Object applyTransformation(Object config) { return null; }
	@Override
	public Object generateTemplate(Object config) { return null; }
}