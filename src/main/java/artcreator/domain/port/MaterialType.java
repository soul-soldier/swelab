package artcreator.domain.port;

/**
 * Material types supported for template generation.
 * One of the types is explicitly "Buegelperlen" where point spacing is fixed.
 */
public enum MaterialType {
    TOOTHPICKS("Toothpicks"),
	BUEGELPERLEN("Buegelperlen"),
	PAPIER("Papier (Druck)"),
	HOLZ("Holz (Bohrschablone)");

	private final String displayName;

	MaterialType(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return this.displayName;
	}
}
