package artcreator.creator.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import artcreator.domain.port.Domain;
import artcreator.statemachine.port.StateMachine;

public class CreatorImpl {

	public CreatorImpl(StateMachine stateMachine, Domain domain) {
		// TODO Auto-generated constructor stub
	}

	public void sysop(String str) {
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		logger.log(Level.INFO, str);
	}
}
