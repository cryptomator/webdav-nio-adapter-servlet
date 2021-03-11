/*******************************************************************************
 * Copyright (c) 2017 Skymatic UG (haftungsbeschränkt).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE file.
 *******************************************************************************/
package org.cryptomator.webdav.core.servlet;

import org.cryptomator.webdav.core.servlet.ByteRange.MalformedByteRangeException;
import org.cryptomator.webdav.core.servlet.ByteRange.UnsupportedRangeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ByteRangeTest {

	@Test
	public void testConstructionWithUnsupportedRangeType() {
		Assertions.assertThrows(UnsupportedRangeException.class, () -> {
			ByteRange.parse("cats=2-3");
		});
	}

	@Test
	public void testConstructionWithMultipleRanges() {
		Assertions.assertThrows(UnsupportedRangeException.class, () -> {
			ByteRange.parse("bytes=2-3,7-8");
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"bytes=2-3-4", "bytes=3-2", "bytes=-", "bytes=", "bytes=2-z"})
	public void testConstructionWithMalformedSingleRange1(String str) {
		Assertions.assertThrows(MalformedByteRangeException.class, () -> {
			ByteRange.parse(str);
		});
	}

	@Test
	public void testConstructionWithSingleClosedRange() throws UnsupportedRangeException, MalformedByteRangeException {
		ByteRange range = ByteRange.parse("bytes=2-3");
		Assertions.assertEquals(2, range.getEffectiveFirstByte(1000));
		Assertions.assertEquals(3, range.getEffectiveLastByte(1000));
	}

	@Test
	public void testConstructionWithSingleOpenRange1() throws UnsupportedRangeException, MalformedByteRangeException {
		ByteRange range = ByteRange.parse("bytes=2-");
		Assertions.assertEquals(2, range.getEffectiveFirstByte(1000));
		Assertions.assertEquals(999, range.getEffectiveLastByte(1000));
	}

	@Test
	public void testConstructionWithSingleOpenRange2() throws UnsupportedRangeException, MalformedByteRangeException {
		ByteRange range = ByteRange.parse("bytes=-2");
		Assertions.assertEquals(998, range.getEffectiveFirstByte(1000));
		Assertions.assertEquals(999, range.getEffectiveLastByte(1000));
	}

}
