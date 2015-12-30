import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * A pure Java implementation of the Directional Cubic Convoluted Interpolation image scaling algorithm.
 */
public class Dcci {

    /**
     * Evaluates the sum of the RGB channel differences between two RGB values.
     *
     * <p>This is equal to |a.r - b.r| + |a.g - b.g| + |a.b - b.b|
     *
     * @param rgbA a RGB integer where each 8 bits represent one channel
     * @param rgbB an RGB integer where each 8 bits represent one channel
     * @return an integer in the range [0, 765]
     */
    protected static int getRGBChannelsDifferenceSum(int rgbA, int rgbB) {
        int differenceSum = 0;
        for (short offset = 0; offset <= 16; offset += 8) {
            differenceSum += Math.abs(((rgbA >> offset) & 0xFF) - ((rgbB >> offset) & 0xFF));
        }
        return differenceSum;
    }

    /**
     * Evaluates the up-right diagonal strength. Uses the sum of all RGB channels differences.
     *
     * @param image a BufferedImage object
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return an integer that represents the edge strength in this direction
     */
    private static int evaluateD1(BufferedImage image, final int x, final int y) {
        int d1 = 0;
        for (int cY = y - 3; cY <= y + 1; cY += 2) {
            for (int cX = x - 1; cX <= x + 3; cX += 2) {
                d1 += getRGBChannelsDifferenceSum(image.getRGB(cX, cY), image.getRGB(cX - 2, cY + 2));
            }
        }
        return d1;
    }

    /**
     * Evaluates the down-right diagonal strength. Uses the sum of all RGB channels differences.
     *
     * @param image a BufferedImage object
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return an integer that represents the edge strength in this direction
     */
    private static int evaluateD2(BufferedImage image, final int x, final int y) {
        int d2 = 0;
        for (int cY = y - 3; cY <= y + 1; cY += 2) {
            for (int cX = x - 3; cX <= x + 1; cX += 2) {
                d2 += getRGBChannelsDifferenceSum(image.getRGB(cX, cY), image.getRGB(cX + 2, cY + 2));
            }
        }
        return d2;
    }

    /**
     * Scales an image to twice its original width minus one and twice its original height minus one using Directional
     * Cubic Convoluted Interpolation.
     *
     * <p>Using Java built-in nearest neighbor, scale the original image to the final size of twice the original
     * dimensions minus one. This is a very cheap and fast way to preserve the alpha channel "good enough" and dispose
     * the pixels the way they need to be.
     *
     * <p>Interpolate is called in every pixel that had been over-simplistically interpolated by nearest neighbor to
     * interpolate it using Directional Cubic Convoluted Interpolation.
     *
     * @param original the original BufferedImage, must be at least one pixel
     * @return a BufferedImage whose size is twice the original dimensions minus one
     */
    private static BufferedImage scale(BufferedImage original) {
        int newWidth = original.getWidth() * 2 - 1;
        int newHeight = original.getHeight() * 2 - 1;
        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        // Fix interpolation
        for (int absoluteIndex = 1; absoluteIndex < result.getHeight() * result.getWidth(); absoluteIndex += 2) {
            interpolate(result, absoluteIndex % result.getWidth(), absoluteIndex / result.getHeight());
        }
        return result;
    }

    /**
     * Returns the eight bits that correspond to a given channel of the provided RGB integer.
     *
     * 0 maps to Red 1 maps to Green 2 maps to Blue
     */
    protected static int getChannel(int rgb, int channel) {
        return (rgb >> getShiftForChannel(channel)) & 0xFF;
    }

    private static int getShiftForChannel(int channel) {
        return 16 - 8 * channel;
    }

    /**
     * Returns the provided RGB integer with the specified channel set to the provided value.
     */
    protected static int withChannel(int rgb, int channel, int value) {
        // Unset the bits that will be modified
        rgb &= ~(0xFF << (getShiftForChannel(channel)));
        // Set them the provided value
        int shiftedValue = value << (getShiftForChannel(channel)); // JVM should be smart and reuse the first result
        return rgb | shiftedValue;
    }

    /**
     * "Forces" a value to a valid range, returning minimum if it is equal to or less than the minimum or maximum if it
     * is greater than or equal to maximum. If the original value is in the range (minimum, maximum), this value is
     * returned.
     *
     * @throws IllegalArgumentException if maximum is less than minimum
     */
    protected static int forceValidRange(int total, int minimum, int maximum) {
        if (maximum < minimum) {
            throw new IllegalArgumentException("maximum should not be less than minimum");
        }
        return Math.min(Math.max(total, minimum), maximum);
    }

    private static int getInterpolatedRGB(final int[] sources) {
        int rgb = 0;
        for (int channel = 0; channel <= 2; channel++) {
            int total = 0;
            total -= getChannel(sources[0], channel);
            total += 9 * getChannel(sources[1], channel);
            total += 9 * getChannel(sources[2], channel);
            total -= getChannel(sources[3], channel);
            total /= 16;
            total = forceValidRange(total, 0, 255); // total may actually range from -32 to 286
            rgb = withChannel(rgb, channel, total);
        }
        return rgb;
    }

    private static void effectivelyInterpolate(BufferedImage image, final int[] sources, final int x, final int y) {
        image.setRGB(x, y, getInterpolatedRGB(sources));
    }

    private static int weightedRGBAverage(int rgbA, int rgbB, double aWeight, double bWeight) {
        int finalRgb = 0;
        for (int channel = 0; channel <= 2; channel++) {
            double weightedAverage = aWeight * getChannel(rgbA, channel) + bWeight * getChannel(rgbB, channel);
            int roundedWeightedAverage = (int) Math.round(weightedAverage);
            finalRgb = withChannel(finalRgb, channel, forceValidRange(roundedWeightedAverage, 0, 255));
        }
        return finalRgb;
    }

    private static int[] getDownRightRGB(BufferedImage image, int x, int y) {
        int[] sourceRgb = new int[4];
        sourceRgb[0] = image.getRGB(x - 3, y - 3);
        sourceRgb[1] = image.getRGB(x - 1, y - 1);
        sourceRgb[2] = image.getRGB(x + 1, y + 1);
        sourceRgb[3] = image.getRGB(x + 3, y + 3);
        return sourceRgb;
    }

    private static int[] getUpRightRGB(BufferedImage image, int x, int y) {
        int[] sourceRgb = new int[4];
        sourceRgb[0] = image.getRGB(x + 3, y - 3);
        sourceRgb[1] = image.getRGB(x + 1, y - 1);
        sourceRgb[2] = image.getRGB(x - 1, y + 1);
        sourceRgb[3] = image.getRGB(x - 3, y + 3);
        return sourceRgb;
    }

    private static void downRightInterpolate(BufferedImage image, final int x, final int y) {
        int[] sourceRgb = getDownRightRGB(image, x, y);
        effectivelyInterpolate(image, sourceRgb, x, y);
    }

    private static void upRightInterpolate(BufferedImage image, int x, int y) {
        int[] sourceRgb = getUpRightRGB(image, x, y);
        effectivelyInterpolate(image, sourceRgb, x, y);
    }

    private static void smoothInterpolate(BufferedImage image, int x, int y, double downRightWeight, double upRightWeight) {
        int[] upRightRGB = getUpRightRGB(image, x, y);
        int upRightRGBValue = getInterpolatedRGB(upRightRGB);
        int[] downRightRGB = getDownRightRGB(image, x, y);
        int downRightRGBValue = getInterpolatedRGB(downRightRGB);
        image.setRGB(x, y, weightedRGBAverage(downRightRGBValue, upRightRGBValue, downRightWeight, upRightWeight));
    }

    /**
     * The original paper does not specify how to handle colored images. The solution used here is to sum all RGB
     * components when calculating edge strength and interpolate over each color channel separately.
     */
    private static void interpolate(BufferedImage image, final int x, final int y) {
        // Diagonal edge strength
        int d1 = evaluateD1(image, x, y);
        int d2 = evaluateD2(image, x, y);
        if (100 * (1 + d1) > 115 * (1 + d2)) { // Up-right edge
            // For an up-right edge, interpolate in the down-right direction
            downRightInterpolate(image, x, y);
        } else if (100 * (1 + d2) > 115 * (1 + d1)) { // Down-right edge
            // For an down-right edge, interpolate in the up-right direction
            upRightInterpolate(image, x, y);
        } else { // Smooth area
            // In the smooth area, edge strength from Up-Right will contribute to the Down-Right sampled pixel, and edge
            // strength from Down-Right will contribute to the Up-Right sampled pixel.
            double w1 = 1 / (1 + Math.pow(d1, 5));
            double w2 = 1 / (1 + Math.pow(d2, 5));
            double downRightWeight = w1 / (w1 + w2);
            double upRightWeight = w2 / (w1 + w2);
            smoothInterpolate(image, x, y, downRightWeight, upRightWeight);
        }
    }
}