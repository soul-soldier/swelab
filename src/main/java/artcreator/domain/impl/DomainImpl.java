package artcreator.domain.impl;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class DomainImpl {

	public Object mkObject() {
		return null;
	}

	public Object loadImage(String path) throws Exception {
		File file = new File(path);
		if (!file.exists()) {
			throw new java.io.FileNotFoundException("File not found: " + path);
		}
		BufferedImage img = ImageIO.read(file);
		if (img == null) {
			throw new IllegalArgumentException("File is not a valid image.");
		}
		return img;
	}
}