package edu.cg;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class SeamsCarver extends ImageProcessor {

  // MARK: Fields
  private int numOfSeams;
  private ResizeOperation resizeOp;
  boolean[][] imageMask;

  // Our fields:
  private Integer[][] greyscale;
  private Integer[][] indices;
  private Long[][] cost;
  private Integer[][] minCostPaths;
  int numOfFoundSeams;
  private Integer[][] seams;
  boolean[][] workingMask;

  public SeamsCarver(
      Logger logger, BufferedImage workingImage, int outWidth, RGBWeights
      rgbWeights, boolean[][] imageMask) {
    super((s) -> logger.log("Seam carving: " + s), workingImage, rgbWeights,
        outWidth, workingImage.getHeight());

    numOfSeams = Math.abs(this.outWidth - this.inWidth);
    this.imageMask = imageMask;
    if (this.inWidth < 2 || this.inHeight < 2) {
      throw new RuntimeException("Can not apply seam carving: workingImage is too small");
    }
    if (numOfSeams > this.inWidth / 2) {
      throw new RuntimeException("Can not apply seam carving: too many seams...");
    }

    // Setting resizeOp with the appropriate method reference:
    if (this.outWidth > this.inWidth) {
      resizeOp = this::increaseImageWidth;
    } else if (this.outWidth < inWidth){
      resizeOp = this::reduceImageWidth;
    } else {
      resizeOp = this::duplicateWorkingImage;
    }

    // Our additional fields initialization:
    numOfFoundSeams = 0;
    initGreyscale();
    initWorkingMask();
    initIndices();
    initCostAndMinCostPaths();
    findSeams();
  }

  private void initWorkingMask() {
    workingMask = new boolean[inHeight][inWidth];
    for (int y = 0; y < inHeight; ++y) {
      for (int x = 0; x < inWidth; ++x) {
        workingMask[y][x] = imageMask[y][x];
      }
    }
  }

  private void initGreyscale() {
    BufferedImage greyscaleBufferedImage = greyscale();
    greyscale = new Integer[inHeight][inWidth];

    forEach((y, x) -> {
      Color pixelColor = new Color(greyscaleBufferedImage.getRGB(x, y));
      greyscale[y][x] = pixelColor.getRed();
    });
  }

  private void initIndices() {
    indices = new Integer[inHeight][inWidth];
    forEach((y, x) -> {
      indices[y][x] = x;
    });
  }

  private void initCostAndMinCostPaths() {
    minCostPaths = new Integer[inHeight][currentWidth()];
    cost = new Long[inHeight][inWidth];

    for (int y = 0; y < inHeight; ++y) {
      for (int x = 0; x < currentWidth(); ++x) {
        if (y == 0) { // Initialize first row.
          cost[y][x] = pixelEnergy(y, x);
          minCostPaths[y][x] = x;
          continue;
        }

        ForwardLookingCost forwardCost = calculateForwardLookingCost(y, x);

        // Use MAX_VALUE at the sides, in order to skip the relevant m.
        long mUpperLeft = (x == 0) ? Integer.MAX_VALUE : cost[y - 1][x - 1];
        long mUp = cost[y - 1][x];
        long mUpperRight = (x + 1 == currentWidth()) ? Integer.MAX_VALUE : cost[y - 1][x + 1];

        long costLeft = mUpperLeft + forwardCost.cLeft;
        long costUp = mUp + forwardCost.cUp;
        long costRight = mUpperRight + forwardCost.cRight;

        long minCost = Math.min(costLeft, Math.min(costUp, costRight));
        int minCostIndex = minCost == costLeft ? x - 1 : minCost == costRight ? x + 1 : x;

        cost[y][x] = pixelEnergy(y, x) + minCost;
        minCostPaths[y][x] = minCostIndex;
      }
    }
  }

  private boolean isIndexInBoundary(int index, int length) {
    return index >= 0 && index < length;
  }

  private long pixelEnergy(int y, int x) {
    int newX = isIndexInBoundary(x + 1, currentWidth()) ? x + 1 : x - 1;
    long e1 = (long)Math.abs(greyscale[y][x] - greyscale[y][newX]);

    int newY = isIndexInBoundary(y + 1, inHeight)  ? y + 1 : y - 1;
    long e2 = (long)Math.abs(greyscale[y][x] - greyscale[newY][x]);

    long e3 = workingMask[y][x] ? Integer.MAX_VALUE : 0L;
    return e1 + e2 + e3;
  }

  // Helper class to calculate a pixel's forward looking cost.
  private static class ForwardLookingCost {
    public long cLeft;
    public long cUp;
    public long cRight;

    public ForwardLookingCost(long cLeft, long cUp, long cRight) {
      this.cLeft = cLeft;
      this.cUp = cUp;
      this.cRight = cRight;
    }
  }

  private ForwardLookingCost calculateForwardLookingCost(int y, int x) {
    long upDelta = 0;
    if ((x + 1 < currentWidth()) && (x > 0)) {
      upDelta = Math.abs(greyscale[y][x + 1] - greyscale[y][x - 1]);
    }
    long upperLeftDelta = (x != 0)
        ? Math.abs(greyscale[y - 1][x] - greyscale[y][x - 1]) : 0;
    long upperRightDelta = (x + 1 != currentWidth())
        ? Math.abs(greyscale[y - 1][x] - greyscale[y][x + 1]) : 0;
    return new ForwardLookingCost(upDelta + upperLeftDelta, upDelta, upDelta + upperRightDelta);
  }

  public BufferedImage resize() {
    return resizeOp.resize();
  }

  private BufferedImage createResizedImageFromIndices() {
    workingMask = new boolean[inHeight][outWidth];
    BufferedImage output = newEmptyOutputSizedImage();

    forEachHeight(y -> {
      for (int x = 0; x < outWidth; ++x) {
        int originalX = indices[y][x];
        workingMask[y][x] = imageMask[y][originalX];
        output.setRGB(x, y, workingImage.getRGB(originalX, y));
      }
    });
    return output;
  }

  private int currentWidth() {
    return inWidth - numOfFoundSeams;
  }

  private void recFindSeam(Integer[] seam, Integer[] curIndicesSeam, int y, int x) {
    if (y < 0) {
      return;
    }
    int minX = minCostPaths[y][x];
    curIndicesSeam[y] = minX;
    seam[y] = indices[y][minX];
    recFindSeam(seam, curIndicesSeam,y - 1, minX);
  }

  private void findSeam(Integer[] seam, Integer[] curIndicesSeam) {
    // Fill the last index of seam:
    int y = inHeight - 1, x = 0;
    // Find the min cost of the last row:
    for (int curX = 1; curX < currentWidth(); ++curX) {
      if (cost[y][x] > cost[y][curX]) {
        x = curX;
      }
    }
    curIndicesSeam[y] = x;
    seam[y] = indices[y][x];

    // Fill the rest of the seam array:
    recFindSeam(seam, curIndicesSeam,y - 1, x);
  }

  private void deleteSeam(Integer[] seam) {
    forEachHeight(y -> {
      for (int x = seam[y]; x < currentWidth() - 1; ++x) {
        // Shift all matrices to the left in order to delete the seam:
        indices[y][x] = indices[y][x + 1];
        greyscale[y][x] = greyscale[y][x + 1];
        workingMask[y][x] = workingMask[y][x + 1];
      }
    });
  }

  private void findSeams() {
    seams = new Integer[numOfSeams][inHeight];

    while (numOfFoundSeams < numOfSeams) {
      Integer[] seam = seams[numOfFoundSeams];
      Integer[] curIndicesSeam =  new Integer[inHeight];
      findSeam(seam, curIndicesSeam);

      deleteSeam(curIndicesSeam);
      ++numOfFoundSeams;

      initCostAndMinCostPaths();
    }
  }

  private BufferedImage reduceImageWidth() {
    return createResizedImageFromIndices();
  }

  private void rotateSeams() {
    Integer[][] newSeams = new Integer[inHeight][numOfSeams];
    for (int k = 0; k < numOfSeams; ++k) {
      for (int y = 0; y < inHeight; ++y) {
        newSeams[y][k] = seams[k][y];
      }
    }
    seams = newSeams;
  }

  private void sortRotatedSeams() {
    for (int y = 0; y < inHeight; ++y) {
      Arrays.sort(seams[y]);
    }
  }

  private BufferedImage increaseImageWidth() {
    logger.log("Increasing the image by seam carving...");
    rotateSeams();       // We need to rotate in order to treat the seams as rows.
    sortRotatedSeams();  // We need to sort in order to duplicate in order.

    initIndices();
    Integer[][] newIndices = new Integer[inHeight][outWidth];
    for (int y = 0; y < inHeight; ++y) {
      Integer[] seam = seams[y];
      int numOfHandledSeams = 0;

      for (int newIndicesX = 0; newIndicesX < outWidth; ++newIndicesX) {
        int indicesX = newIndicesX - numOfHandledSeams;
        int curSeam = numOfHandledSeams < numOfSeams ? seam[numOfHandledSeams] : -1;

        if (curSeam >= 0 && indicesX == curSeam) {
          newIndices[y][newIndicesX] = curSeam;
          newIndices[y][newIndicesX + 1] = curSeam;
          ++newIndicesX;
          ++numOfHandledSeams;
        } else {
          newIndices[y][newIndicesX] = indices[y][indicesX];
        }
      }
    }

    indices = newIndices;
    return createResizedImageFromIndices();
  }

  public BufferedImage showSeams(int seamColorRGB) {
    BufferedImage output = duplicateWorkingImage();
    for (int i = 0; i < numOfSeams; ++i) {
      for (int y = 0; y < inHeight; ++y) {
        int x = seams[i][y];
        output.setRGB(x, y, seamColorRGB);
      }
    }
    return output;
  }

  public boolean[][] getMaskAfterSeamCarving() {
    return workingMask;
  }

  // MARK: An inner interface for functional programming.
  @FunctionalInterface
  interface ResizeOperation {
    BufferedImage resize();
  }
}

