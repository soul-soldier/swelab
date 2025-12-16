package artcreator.domain.port;

public interface Domain {

	/**
	 * Loads an image from the file system.
	 * @param path Absolute path to the file.
	 * @return The loaded image object (e.g. java.awt.Image).
	 * @throws Exception if loading fails.
	 */
	Object loadImage(String path) throws Exception;

	// Existing factory method (kept for compatibility)
	Object mkObject();
}