package artcreator.domain;

import artcreator.domain.impl.DomainImpl;
import artcreator.domain.port.Domain;

public class DomainFacade implements DomainFactory, Domain {

	// Instanz der eigentlichen Implementierung
	private DomainImpl domainImpl = new DomainImpl();

	@Override
	public synchronized Domain domain() {
		if (this.domainImpl == null) {
			this.domainImpl = new DomainImpl();
		}
		return this;
	}

	// --- Delegation der Methoden ---

	@Override
	public Object mkObject() {
		return this.domainImpl.mkObject();
	}

	/**
	 * Hier fehlte die Weiterleitung.
	 * Die Facade reicht den Aufruf an die Implementierung weiter.
	 */
	@Override
	public Object loadImage(String path) throws Exception {
		return this.domainImpl.loadImage(path);
	}

	@Override
	public Object transformImage(Object image, String operation) {
		return this.domainImpl.transformImage(image, operation);
	}
}