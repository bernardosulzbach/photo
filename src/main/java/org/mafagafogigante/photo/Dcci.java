package org.mafagafogigante.photo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * The Directional Cubic Convolution Interpolation image scaling algorithm.
 */
public class Dcci {

    /**
     * Scales an image to twice its original width minus one and twice its original height minus one using Directional
     * Cubic Convolution Interpolation.
     *
     * @param original the original BufferedImage, must be at least one pixel
     * @return a BufferedImage whose size is twice the original dimensions minus one
     */
    public static BufferedImage scale(BufferedImage original) {
        BufferedImage result = getDestinationBufferedImage(original);
        UnderlyingArray underlyingArray = new UnderlyingArray(result);
        // The original paper does not specify how to handle colored images. The solution used here is to sum all RGB
        // components when calculating edge strength and interpolate over each color channel separately.
        interpolateDiagonalGaps(underlyingArray);
        interpolateRemainingGaps(underlyingArray);
        return result;
    }

    /**
     * Returns a BufferedImage backed by integers and big enough to support the scaling algorithm.
     *
     * @param bufferedImage a BufferedImage
     */
    private static BufferedImage getDestinationBufferedImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth() * 2 - 1;
        int height = bufferedImage.getHeight() * 2 - 1;
        BufferedImage destination = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = destination.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(bufferedImage, 0, 0, width, height, null);
        g2.dispose();
        return destination;
    }

    private static void interpolateDiagonalGaps(UnderlyingArray array) {
        for (int y = 1; y < array.colLength; y += 2) {
            for (int x = 1; x < array.rowLength; x += 2) {
                // Should handle edges properly.
                if (y >= 3 && y <= array.colLength - 4) {
                    if (x >= 3 && x <= array.rowLength - 4) {
                        interpolateDiagonalGap(array, x, y);
                    }
                }
            }
        }
    }

    private static void interpolateRemainingGaps(UnderlyingArray array) {
        for (int y = 0; y < array.colLength; y++) {
            for (int x = ((y % 2 == 0) ? 1 : 0); x < array.rowLength; x += 2) {
                // Should handle edges properly.
                if (y >= 3 && y <= array.colLength - 4) {
                    if (x >= 3 && x <= array.rowLength - 4) {
                        interpolateRemainingGap(array, x, y);
                    }
                }
            }
        }
    }

    /**
     * Evaluates the sum of the RGB channel differences between two RGB values.
     *
     * <p>This is equal to |a.r - b.r| + |a.g - b.g| + |a.b - b.b|
     *
     * @param rgbA an RGB integer where each 8 bits represent one channel
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
     * @param array an UnderlyingArray object
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return an integer that represents the edge strength in this direction
     */
    private static int evaluateUpRightDiagonalStrength(UnderlyingArray array, final int x, final int y) {
        int strength = 0;
        for (int cY = y - 3; cY <= y + 1; cY += 2) {
            for (int cX = x - 1; cX <= x + 3; cX += 2) {
                strength += getRGBChannelsDifferenceSum(array.getRGB(cX, cY), array.getRGB(cX - 2, cY + 2));
            }
        }
        return strength;
    }

    /**
     * Evaluates the down-right diagonal strength. Uses the sum of all RGB channels differences.
     *
     * @param array an UnderlyingArray object
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return an integer that represents the edge strength in this direction
     */
    private static int evaluateDownRightDiagonalStrength(UnderlyingArray array, final int x, final int y) {
        int strength = 0;
        for (int cY = y - 3; cY <= y + 1; cY += 2) {
            for (int cX = x - 3; cX <= x + 1; cX += 2) {
                strength += getRGBChannelsDifferenceSum(array.getRGB(cX, cY), array.getRGB(cX + 2, cY + 2));
            }
        }
        return strength;
    }

    /**
     * Returns the required bit shift to retrieve a specific channel from an integer RGB.
     *
     * @param channel the channel, where 0 represents the leftmost 8 bits and 3 the rightmost 8 bits
     * @return one of the following multiples of eight: 0, 8, 16, 24
     */
    private static int getShiftForChannel(int channel) {
        return 24 - 8 * channel;
    }

    /**
     * Returns the eight bits that correspond to a given channel of the provided RGB integer.
     *
     * <p><ul> <li>0 maps to Alpha <li>1 maps to Red <li>2 maps to Green <li>3 maps to Blue </ul>
     */
    protected static int getChannel(int rgb, int channel) {
        return (rgb >> getShiftForChannel(channel)) & 0xFF;
    }

    /**
     * Returns the provided RGB integer with the specified channel set to the provided value.
     */
    protected static int withChannel(int rgb, int channel, int value) {
        final int shift = getShiftForChannel(channel);
        // Unset the bits that will be modified
        rgb &= ~(0xFF << shift);
        // Set them the provided value
        int shiftedValue = value << shift;
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

    private static int getInterpolatedRGB(int[] sources) {
        int rgb = 0;
        for (int channel = 0; channel <= 3; channel++) {
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

    private static void effectivelyInterpolate(UnderlyingArray array, int[] sources, final int x, final int y) {
        array.setRGB(x, y, getInterpolatedRGB(sources));
    }

    private static int weightedRGBAverage(int rgbA, int rgbB, double aWeight, double bWeight) {
        int finalRgb = 0;
        for (int channel = 0; channel <= 3; channel++) {
            double weightedAverage = aWeight * getChannel(rgbA, channel) + bWeight * getChannel(rgbB, channel);
            int roundedWeightedAverage = (int) Math.round(weightedAverage);
            finalRgb = withChannel(finalRgb, channel, forceValidRange(roundedWeightedAverage, 0, 255));
        }
        return finalRgb;
    }

    private static void interpolateDiagonalGap(UnderlyingArray array, final int x, final int y) {
        // Diagonal edge strength
        int d1 = evaluateUpRightDiagonalStrength(array, x, y);
        int d2 = evaluateDownRightDiagonalStrength(array, x, y);
        if (100 * (1 + d1) > 115 * (1 + d2)) { // Up-right edge
            // For an up-right edge, interpolate in the down-right direction
            downRightInterpolate(array, x, y);
        } else if (100 * (1 + d2) > 115 * (1 + d1)) { // Down-right edge
            // For an down-right edge, interpolate in the up-right direction
            upRightInterpolate(array, x, y);
        } else { // Smooth area
            // Here edge strength from up-right will contribute to the down-right sampled pixel, and vice versa.
            final double w1 = 1 / (1 + Math.pow(d1, 5));
            final double w2 = 1 / (1 + Math.pow(d2, 5));
            final double downRightWeight = w1 / (w1 + w2);
            final double upRightWeight = w2 / (w1 + w2);
            smoothDiagonalInterpolate(array, x, y, downRightWeight, upRightWeight);
        }
    }

    private static int[] getDownRightRGB(UnderlyingArray array, int x, int y) {
        int[] sourceRgb = new int[4];
        sourceRgb[0] = array.getRGB(x - 3, y - 3);
        sourceRgb[1] = array.getRGB(x - 1, y - 1);
        sourceRgb[2] = array.getRGB(x + 1, y + 1);
        sourceRgb[3] = array.getRGB(x + 3, y + 3);
        return sourceRgb;
    }

    private static int[] getUpRightRGB(UnderlyingArray array, int x, int y) {
        int[] sourceRgb = new int[4];
        sourceRgb[0] = array.getRGB(x + 3, y - 3);
        sourceRgb[1] = array.getRGB(x + 1, y - 1);
        sourceRgb[2] = array.getRGB(x - 1, y + 1);
        sourceRgb[3] = array.getRGB(x - 3, y + 3);
        return sourceRgb;
    }

    private static void downRightInterpolate(UnderlyingArray array, final int x, final int y) {
        int[] sourceRgb = getDownRightRGB(array, x, y);
        effectivelyInterpolate(array, sourceRgb, x, y);
    }

    private static void upRightInterpolate(UnderlyingArray array, int x, int y) {
        int[] sourceRgb = getUpRightRGB(array, x, y);
        effectivelyInterpolate(array, sourceRgb, x, y);
    }

    private static void smoothDiagonalInterpolate(UnderlyingArray array, int x, int y, double downRightWeight, double upRightWeight) {
        int[] upRightRGB = getUpRightRGB(array, x, y);
        int upRightRGBValue = getInterpolatedRGB(upRightRGB);
        int[] downRightRGB = getDownRightRGB(array, x, y);
        int downRightRGBValue = getInterpolatedRGB(downRightRGB);
        array.setRGB(x, y, weightedRGBAverage(downRightRGBValue, upRightRGBValue, downRightWeight, upRightWeight));
    }

    private static void interpolateRemainingGap(UnderlyingArray array, final int x, final int y) {
        // Diagonal edge strength
        int d1 = evaluateHorizontalWeight(array, x, y);
        int d2 = evaluateVerticalWeight(array, x, y);
        if (100 * (1 + d1) > 115 * (1 + d2)) { // Horizontal edge
            // For a horizontal edge, interpolate vertically.
            verticalInterpolate(array, x, y);
        } else if (100 * (1 + d2) > 115 * (1 + d1)) { // Vertical edge
            // For a vertical edge, interpolate horizontally.
            horizontalInterpolate(array, x, y);
        } else { // Smooth area
            // In the smooth area, edge strength from horizontal will contribute to the vertical sampled pixel, and
            // edge strength from vertical will contribute to the horizontal sampled pixel.
            double w1 = 1 / (1 + Math.pow(d1, 5));
            double w2 = 1 / (1 + Math.pow(d2, 5));
            double verticalWeight = w1 / (w1 + w2);
            double horizontalWeight = w2 / (w1 + w2);
            smoothRemainingInterpolate(array, x, y, verticalWeight, horizontalWeight);
        }
    }

    private static int[] getVerticalRGB(UnderlyingArray array, int x, int y) {
        int[] sourceRgb = new int[4];
        sourceRgb[0] = array.getRGB(x, y - 3);
        sourceRgb[1] = array.getRGB(x, y - 1);
        sourceRgb[2] = array.getRGB(x, y + 1);
        sourceRgb[3] = array.getRGB(x, y + 3);
        return sourceRgb;
    }

    private static int[] getHorizontalRGB(UnderlyingArray array, int x, int y) {
        int[] sourceRgb = new int[4];
        sourceRgb[0] = array.getRGB(x - 3, y);
        sourceRgb[1] = array.getRGB(x - 1, y);
        sourceRgb[2] = array.getRGB(x + 1, y);
        sourceRgb[3] = array.getRGB(x + 3, y);
        return sourceRgb;
    }

    private static void verticalInterpolate(UnderlyingArray array, int x, int y) {
        int[] source = getVerticalRGB(array, x, y);
        effectivelyInterpolate(array, source, x, y);
    }

    private static void horizontalInterpolate(UnderlyingArray array, int x, int y) {
        int[] source = getHorizontalRGB(array, x, y);
        effectivelyInterpolate(array, source, x, y);
    }

    private static void smoothRemainingInterpolate(UnderlyingArray array, int x, int y, double vWeight, double hWeight) {
        int[] verticalRGB = getVerticalRGB(array, x, y);
        int interpolatedVerticalRGB = getInterpolatedRGB(verticalRGB);
        int[] horizontalRGB = getHorizontalRGB(array, x, y);
        int interpolatedHorizontalRGB = getInterpolatedRGB(horizontalRGB);
        int finalRGB = weightedRGBAverage(interpolatedVerticalRGB, interpolatedHorizontalRGB, vWeight, hWeight);
        array.setRGB(x, y, finalRGB);
    }

    private static int evaluateVerticalWeight(UnderlyingArray array, int x, int y) {
        int weight = 0;
        // Could be refactored into a for loop to improve readability. However, I don't know if there is a way to do
        // so that wouldn't make it too cryptic.
        // Notice that these operations are exactly the same as the ones in evaluateHorizontalWeight but swapped x and
        // y modifications.
        weight += getRGBChannelsDifferenceSum(array.getRGB(x - 2, y + 1), array.getRGB(x - 2, y - 1));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x - 1, y + 2), array.getRGB(x - 1, y));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x - 1, y), array.getRGB(x - 1, y - 2));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x, y + 3), array.getRGB(x, y + 1));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x, y + 1), array.getRGB(x, y - 1));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x, y - 1), array.getRGB(x, y - 3));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 1, y + 2), array.getRGB(x + 1, y));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 1, y), array.getRGB(x + 1, y - 2));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 2, y + 1), array.getRGB(x + 2, y - 1));
        return weight;
    }

    private static int evaluateHorizontalWeight(UnderlyingArray array, int x, int y) {
        int weight = 0;
        // Could be refactored into a for loop to improve readability. However, I don't know if there is a way to do
        // so that wouldn't make it too cryptic.
        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 2, y - 1), array.getRGB(x - 1, y - 2));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 2, y - 1), array.getRGB(x, y - 1));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x, y - 1), array.getRGB(x - 2, y - 1));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 3, y), array.getRGB(x + 1, y));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 1, y), array.getRGB(x - 1, y));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x - 1, y), array.getRGB(x - 3, y));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 2, y + 1), array.getRGB(x, y + 1));
        weight += getRGBChannelsDifferenceSum(array.getRGB(x, y + 1), array.getRGB(x - 2, y + 1));

        weight += getRGBChannelsDifferenceSum(array.getRGB(x + 1, y + 2), array.getRGB(x - 1, y + 2));
        return weight;
    }

    private static class UnderlyingArray { // This class presented a performance improvement of around 50 %.
        private int[] array;
        private int rowLength;
        private int colLength;

        private UnderlyingArray(BufferedImage bufferedImage) {
            rowLength = bufferedImage.getWidth();
            colLength = bufferedImage.getHeight();
            array = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        }

        private int getRGB(int x, int y) {
            return array[y * rowLength + x];
        }

        private void setRGB(int x, int y, int rgb) {
            array[y * rowLength + x] = rgb;
        }
    }

}
