package org.mafagafogigante.photo;

import org.junit.Assert;
import org.junit.Test;

public class DcciTest {

    @Test
    public void testGetRGBChannelsDifferenceSumRespectsItsContract() throws Exception {
        int black = 0x000000;
        int white = 0xFFFFFF;
        Assert.assertEquals(0, Dcci.getRGBChannelsDifferenceSum(white, white));
        Assert.assertEquals(0, Dcci.getRGBChannelsDifferenceSum(black, black));
        Assert.assertEquals(765, Dcci.getRGBChannelsDifferenceSum(white, black));
    }

    @Test
    public void testGetChannelWorksForEachChannel() throws Exception {
        int red = 0xFF0000;
        Assert.assertEquals(255, Dcci.getChannel(red, 1));
        Assert.assertEquals(0, Dcci.getChannel(red, 2));
        Assert.assertEquals(0, Dcci.getChannel(red, 3));
        int green = 0x00FF00;
        Assert.assertEquals(0, Dcci.getChannel(green, 1));
        Assert.assertEquals(255, Dcci.getChannel(green, 2));
        Assert.assertEquals(0, Dcci.getChannel(green, 3));
        int blue = 0x0000FF;
        Assert.assertEquals(0, Dcci.getChannel(blue, 1));
        Assert.assertEquals(0, Dcci.getChannel(blue, 2));
        Assert.assertEquals(255, Dcci.getChannel(blue, 3));
    }

    @Test
    public void testValidRangeThrowsExceptionOnInvalidRanges() {
        try {
            Dcci.forceValidRange(0, 1, 0);
            Assert.fail("expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testValidRangeActuallyEnsuresRanges() throws Exception {
        Assert.assertEquals(0, Dcci.forceValidRange(-127, 0, 255));
        Assert.assertEquals(0, Dcci.forceValidRange(0, 0, 255));
        Assert.assertEquals(127, Dcci.forceValidRange(127, 0, 255));
        Assert.assertEquals(255, Dcci.forceValidRange(255, 0, 255));
        Assert.assertEquals(255, Dcci.forceValidRange(384, 0, 255));
    }

    @Test
    public void testWithChannel() throws Exception {
        Assert.assertEquals(0xFF0000, Dcci.withChannel(0, 1, 0xFF));
        Assert.assertEquals(0x00FF00, Dcci.withChannel(0, 2, 0xFF));
        Assert.assertEquals(0x0000FF, Dcci.withChannel(0, 3, 0xFF));
    }

}