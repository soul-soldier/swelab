package artcreator.creator;

import artcreator.creator.impl.CreatorImpl;
import artcreator.creator.port.Creator;
import artcreator.domain.DomainFactory;
import artcreator.statemachine.StateMachineFactory;
import artcreator.statemachine.port.StateMachine;

public class CreatorFacade implements CreatorFactory, Creator {

	private CreatorImpl creatorImpl;
	private StateMachine stateMachine;

	@Override
	public Creator creator() {
		if (this.creatorImpl == null) {
			this.stateMachine = StateMachineFactory.FACTORY.stateMachine();
			this.creatorImpl = new CreatorImpl(stateMachine, DomainFactory.FACTORY.domain());
		}
		return this;
	}

	// --- Delegation Methods ---

	@Override
	public Object importImage(String path) {
		return this.creatorImpl.importImage(path);
	}

	@Override
	public Object applyTransformation(Object transformConfig) {
		return this.creatorImpl.applyTransformation(transformConfig);
	}

	@Override
	public Object generateTemplate(Object templateConfig) {
		return this.creatorImpl.generateTemplate(templateConfig);
	}
}