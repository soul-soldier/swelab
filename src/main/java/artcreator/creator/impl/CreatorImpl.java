package artcreator.creator.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import artcreator.creator.port.Creator;
import artcreator.domain.port.Domain;
import artcreator.statemachine.port.State;
import artcreator.statemachine.port.State.S;
import artcreator.statemachine.port.StateMachine;

public class CreatorImpl implements Creator {

	private final StateMachine stateMachine;
	private final Domain domain;

	// Internal storage for the current workflow
	private Object currentImage;

	public CreatorImpl(StateMachine stateMachine, Domain domain) {
		this.stateMachine = stateMachine;
		this.domain = domain;
	}

	@Override
	public Object importImage(String path) throws IllegalStateException {
		// 1. Validate State
		// Allowing import at any time to restart process, unless we are currently calculating
		if (this.stateMachine.getState() == S.Processing) {
			throw new IllegalStateException("Cannot import image while processing.");
		}

		// 2. Logic (Delegating to Domain)
		try {
			// Simulation: logic to load image
			Logger.getGlobal().log(Level.INFO, "Importing image from: {0}", path);
			this.currentImage = domain.mkObject(); // Replace with domain.loadImage(path)

			// 3. Update State
			this.stateMachine.setState(S.ImageLoaded);

			return this.currentImage;
		} catch (Exception e) {
			// Handle error, maybe revert state
			throw new RuntimeException("Failed to load image", e);
		}
	}

	@Override
	public Object applyTransformation(Object transformConfig) throws IllegalStateException {
		// 1. Validate State
		if (!this.stateMachine.getState().isSubStateOf(S.ImageLoaded) &&
				this.stateMachine.getState() != S.TemplateReady) {
			throw new IllegalStateException("No image loaded to transform.");
		}

		// 2. Logic
		Logger.getGlobal().log(Level.INFO, "Applying transformation...");
		// this.currentImage = domain.transform(this.currentImage, transformConfig);

		// 3. Update State (Stays in ImageLoaded, effectively)
		this.stateMachine.setState(S.ImageLoaded);

		return this.currentImage;
	}

	@Override
	public Object generateTemplate(Object templateConfig) throws IllegalStateException {
		// 1. Validate State
		if (!this.stateMachine.getState().isSubStateOf(S.ImageLoaded)) {
			throw new IllegalStateException("Cannot generate template without loaded image.");
		}

		// 2. State transition: Start Processing
		this.stateMachine.setState(S.Processing);

		try {
			Logger.getGlobal().log(Level.INFO, "Generating template with params: {0}", templateConfig);

			// Simulation: Heavy calculation
			Object template = domain.mkObject(); // Replace with domain.calculateTemplate(...)

			// 3. Finish State
			this.stateMachine.setState(S.TemplateReady);
			return template;

		} catch (Exception e) {
			// On error, fall back to previous state
			this.stateMachine.setState(S.ImageLoaded);
			throw e;
		}
	}
}