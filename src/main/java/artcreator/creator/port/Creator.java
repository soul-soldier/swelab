package artcreator.creator.port;

// Assuming these types exist in domain.port or act as placeholders
// In a real scenario, you would import artcreator.domain.port.ArtImage etc.

public interface Creator {

	/**
	 * Imports an image from the file system.
	 * @param path The file path to the image.
	 * @return The loaded domain image object.
	 * @throws IllegalStateException if the system is busy.
	 */
	Object importImage(String path) throws IllegalStateException;

	/**
	 * Applies geometric transformations (crop, rotate) to the current image.
	 * @param transformConfig Configuration object (e.g., specific enum or params).
	 * @return The modified domain image object.
	 * @throws IllegalStateException if no image is loaded.
	 */
	Object applyTransformation(Object transformConfig) throws IllegalStateException;

	/**
	 * Generates the art template based on material and color parameters.
	 * @param templateConfig Configuration for the template (material, size, colors).
	 * @return The final template object ready for preview/export.
	 * @throws IllegalStateException if no image is loaded.
	 */
	Object generateTemplate(Object templateConfig) throws IllegalStateException;

}