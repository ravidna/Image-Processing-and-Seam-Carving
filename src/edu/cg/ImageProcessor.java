package edu.cg;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class ImageProcessor extends FunctioalForEachLoops {

  // MARK: fields
  public final Logger logger;
  public final BufferedImage workingImage;
  public final RGBWeights rgbWeights;
  public final int inWidth;
  public final int inHeight;
  public final int workingImageType;
  public final int outWidth;
  public final int outHeight;

  // MARK: constructors
  public ImageProcessor(
      Logger logger,
      BufferedImage workingImage,
      RGBWeights rgbWeights,
      int outWidth,
      int outHeight) {
    super(); // Initializing for each loops...

    if (!validateRgbWeights(rgbWeights)) {
      throw new IllegalArgumentException();
    }

    this.logger = logger;
    this.workingImage = workingImage;
    this.rgbWeights = rgbWeights;

    inWidth = workingImage.getWidth();
    inHeight = workingImage.getHeight();

    workingImageType = workingImage.getType();
    this.outWidth = outWidth;
    this.outHeight = outHeight;
    setForEachInputParameters();
  }

  public ImageProcessor(Logger logger, BufferedImage workingImage, RGBWeights rgbWeights) {
    this(logger, workingImage, rgbWeights, workingImage.getWidth(), workingImage.getHeight());
  }

  private boolean validateRgbWeights(RGBWeights rgbWeights) {
    return isValidRgbWeight(rgbWeights.redWeight)
        && isValidRgbWeight(rgbWeights.greenWeight)
        && isValidRgbWeight(rgbWeights.blueWeight)
        && rgbWeights.weightsAmount > 0;
  }

  private boolean isValidRgbWeight(int weight) {
    return weight >= 0 && weight <= 100;
  }

  // MARK: change picture hue - example
  public BufferedImage changeHue() {
    logger.log("Prepareing for hue changing...");

    int r = rgbWeights.redWeight;
    int g = rgbWeights.greenWeight;
    int b = rgbWeights.blueWeight;
    int max = rgbWeights.maxWeight;

    BufferedImage ans = newEmptyInputSizedImage();

    forEach(
        (y, x) -> {
          Color c = new Color(workingImage.getRGB(x, y));
          int red = r * c.getRed() / max;
          int green = g * c.getGreen() / max;
          int blue = b * c.getBlue() / max;
          Color color = new Color(red, green, blue);
          ans.setRGB(x, y, color.getRGB());
        });

    logger.log("Changing hue done!");

    return ans;
  }

  public final void setForEachInputParameters() {
    setForEachParameters(inWidth, inHeight);
  }

  public final void setForEachOutputParameters() {
    setForEachParameters(outWidth, outHeight);
  }

  public final BufferedImage newEmptyInputSizedImage() {
    return newEmptyImage(inWidth, inHeight);
  }

  public final BufferedImage newEmptyOutputSizedImage() {
    return newEmptyImage(outWidth, outHeight);
  }

  public final BufferedImage newEmptyImage(int width, int height) {
    return new BufferedImage(width, height, workingImageType);
  }

  // A helper method that deep copies the current working image.
  public final BufferedImage duplicateWorkingImage() {
    BufferedImage output = newEmptyInputSizedImage();
    setForEachInputParameters();
    forEach((y, x) -> output.setRGB(x, y, workingImage.getRGB(x, y)));

    return output;
  }

  public BufferedImage greyscale() {
    logger.log("Prepareing for greyscaling");

    int r = rgbWeights.redWeight;
    int g = rgbWeights.greenWeight;
    int b = rgbWeights.blueWeight;
    int amount = rgbWeights.weightsAmount;

    BufferedImage ans = newEmptyInputSizedImage();

    forEach(
        (y, x) -> {
          Color pixelColor = new Color(workingImage.getRGB(x, y));
          int red = r * pixelColor.getRed();
          int green = g * pixelColor.getGreen();
          int blue = b * pixelColor.getBlue();
          int greyMean = (red + green + blue) / amount;

          Color greyColor = new Color(greyMean, greyMean, greyMean);
          ans.setRGB(x, y, greyColor.getRGB());
        });

    logger.log("Greyscailing done!");
    return ans;
  }

  public BufferedImage nearestNeighbor() {
    logger.log("Preparing for nearest neighbor...");

    BufferedImage ans = newEmptyOutputSizedImage();
    setForEachOutputParameters(); // Set the forEach to work on the desired parameters.

    forEach(
        (y, x) -> {
          float xRatio = (float) inWidth / (float) outWidth;
          float yRatio = (float) inHeight / (float) outHeight;
          int nearestX = Math.round(x * xRatio);
          int nearestY = Math.round(y * yRatio);

          Color nearestNeighborColor = new Color(workingImage.getRGB(nearestX, nearestY));
          ans.setRGB(x, y, nearestNeighborColor.getRGB());
        });

    logger.log("Nearest neighbor done!");
    return ans;
  }
}
