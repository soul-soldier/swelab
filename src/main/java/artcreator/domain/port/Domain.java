package artcreator.domain.port;

public interface Domain {

	Object mkObject();

	// Loads file from disk
	Object loadImage(String path) throws Exception;

	/**
	 * Applies a transformation to the given image.
	 * @param image The current image object (e.g. BufferedImage).
	 * @param operation The operation to perform (e.g. "rotate_left", "rotate_right").
	 * @return The new, modified image object.
	 */
	Object transformImage(Object image, String operation);
}