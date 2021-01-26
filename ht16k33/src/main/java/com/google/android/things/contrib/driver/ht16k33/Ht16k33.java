/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.things.contrib.driver.ht16k33;

import android.support.annotation.VisibleForTesting;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

/**
 * I2C wrapper for writing to a ht16k33 chip, such as for a 14-segment LED display. Display data is
 * written using the {@link #writeColumn(int, short)} method, with the information constructed by
 * bitwise ORing segment constants together:
 * <pre>
 * short sigma = SEGMENT_TOP | SEGMENT_BOTTOM
 *         | SEGMENT_DIAGONAL_LEFT_TOP | SEGMENT_DIAGONAL_LEFT_BOTTOM;
 * ht16k33.write(0, sigma);
 * </pre>
 * If you intend to display standard alphanumeric characters only, consider using something like
 * {@link AlphanumericDisplay} which provides convenient methods for doing that.
 */
public class Ht16k33 implements AutoCloseable {

    private static final String TAG = Ht16k33.class.getSimpleName();

    /**
     * Default I2C peripheral address.
     */
    public static final int I2C_ADDRESS = 0x70;

    private static final int HT16K33_CMD_SYSTEM_SETUP = 0x20;
    private static final int HT16K33_OSCILLATOR_ON = 0b0001;
    private static final int HT16K33_OSCILLATOR_OFF = 0b0000;
    private static final int HT16K33_CMD_DISPLAYSETUP = 0x80;
    private static final int HT16K33_DISPLAY_ON = 0b0001;
    private static final int HT16K33_DISPLAY_OFF = 0b0000;
    private static final int HT16K33_CMD_BRIGHTNESS = 0xE0;

    /**
     * The maximum brightness level for this display
     */
    public static final int HT16K33_BRIGHTNESS_MAX = 0b00001111;

    /** Top segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_TOP = 1;
    /** Right top segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_RIGHT_TOP = 1 << 1;
    /** Right bottom segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_RIGHT_BOTTOM = 1 << 2;
    /** Bottom segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_BOTTOM = 1 << 3;
    /** Left bottom segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_LEFT_BOTTOM = 1 << 4;
    /** Left top segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_LEFT_TOP = 1 << 5;
    /** Center left segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_CENTER_LEFT = 1 << 6;
    /** Center right segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_CENTER_RIGHT = 1 << 7;
    /** Diagonal left top segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_DIAGONAL_LEFT_TOP = 1 << 8;
    /** Center top segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_CENTER_TOP = 1 << 9;
    /** Diagonal right top segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_DIAGONAL_RIGHT_TOP = 1 << 10;
    /** Diagonal left bottom segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_DIAGONAL_LEFT_BOTTOM = 1 << 11;
    /** Center bottom segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_CENTER_BOTTOM = 1 << 12;
    /** Diagonal right bottom segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_DIAGONAL_RIGHT_BOTTOM = 1 << 13;
    /** Dot segment bit. Useful for ORing together segments to display. */
    public static final short SEGMENT_DOT = 1 << 14;

    private I2cDevice mDevice;

    /**
     * Create a new driver for a HT16K33 peripheral connected on the given I2C bus using the
     * {@link #I2C_ADDRESS default I2C address}.
     * @param bus
     */
    public Ht16k33(String bus) throws IOException {
        this(bus, I2C_ADDRESS);
    }

    /**
     * Create a new driver for a HT16K33 peripheral connected on the given I2C bus and using the
     * given I2C address.
     * @param bus
     * @param i2cAddress
     */
    public Ht16k33(String bus, int i2cAddress) throws IOException {
        PeripheralManager pioService = PeripheralManager.getInstance();
        I2cDevice device = pioService.openI2cDevice(bus, i2cAddress);
        connect(device);
    }

    /**
     * Create a new driver for a HT16K33 peripheral from a given I2C device.
     * @param device
     */
    @VisibleForTesting
    /*package*/ Ht16k33(I2cDevice device) {
        connect(device);
    }

    private void connect(I2cDevice device) {
        mDevice = device;
    }

    /**
     * Close the device and the underlying device.
     */
    @Override
    public void close() throws IOException {
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

    /**
     * Enable oscillator and LED display.
     * @throws IOException
     */
    public void setEnabled(boolean enabled) throws IOException {
        if (mDevice == null) {
            throw new IllegalStateException("I2C device not opened");
        }
        int oscillator_flag = enabled ? HT16K33_OSCILLATOR_ON : HT16K33_OSCILLATOR_OFF;
        mDevice.write(new byte[]{(byte) (HT16K33_CMD_SYSTEM_SETUP | oscillator_flag)}, 1);
        int display_flag = enabled ? HT16K33_DISPLAY_ON : HT16K33_DISPLAY_OFF;
        mDevice.write(new byte[]{(byte) (HT16K33_CMD_DISPLAYSETUP | display_flag)}, 1);
    }

    /**
     * Set LED display brightness.
     * @param value brigthness value between 0 and {@link #HT16K33_BRIGHTNESS_MAX}
     */
    public void setBrightness(int value) throws IOException {
        if (mDevice == null) {
            throw new IllegalStateException("I2C device not opened");
        }
        if (value < 0 || value > HT16K33_BRIGHTNESS_MAX) {
            throw new IllegalArgumentException("brightness must be between 0 and " +
                    HT16K33_BRIGHTNESS_MAX);
        }
        mDevice.write(new byte[]{(byte) (HT16K33_CMD_BRIGHTNESS | value)}, 1);
    }

    /**
     * Set LED display brightness.
     * @param value brigthness value between 0 and 1.0f
     */
    public void setBrightness(float value) throws IOException {
        int val = Math.round(value * HT16K33_BRIGHTNESS_MAX);
        setBrightness(val);
    }

    /***
     * Write 16bit of LED row data to the given column.
     * @param column
     * @param data LED state for ROW0-15
     */
    public void writeColumn(int column, short data) throws IOException {
        if (mDevice == null) {
            throw new IllegalStateException("I2C device not opened");
        }
        mDevice.writeRegWord(column * 2, data);
    }
}
