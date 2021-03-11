/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.webdav.core.servlet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DavLocatorImplTest {

	private DavLocatorFactoryImpl factory;
	private DavLocatorImpl locator;

	@BeforeEach
	public void setup() {
		factory = Mockito.mock(DavLocatorFactoryImpl.class);
		locator = new DavLocatorImpl(factory, "http://localhost/contextPath/", "foo/foo bar.txt");
	}

	@Test
	public void testConstructionWithTrailingSlash() {
		DavLocatorImpl locator = new DavLocatorImpl(factory, "http://localhost/contextPath/", "foo/bar/baz/");
		Assertions.assertEquals("foo/bar/baz", locator.getResourcePath());
	}

	@Test
	public void testConstructionWithInvalidPrefix() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new DavLocatorImpl(factory, "http://localhost/contextPath", "foo/foo bar.txt");
		});
	}

	@Test
	public void testConstructionWithInvalidPath() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new DavLocatorImpl(factory, "http://localhost/contextPath/", "/foo/foo bar.txt");
		});
	}

	@Test
	public void testGetResourcePath() {
		Assertions.assertEquals("foo/foo bar.txt", locator.getResourcePath());
	}

	@Test
	public void testResolveParent1() {
		DavLocatorImpl fooBarBaz = new DavLocatorImpl(factory, "http://localhost/contextPath/", "foo/bar/baz");
		DavLocatorImpl fooBar = new DavLocatorImpl(factory, "http://localhost/contextPath/", "foo/bar");
		Mockito.when(factory.createResourceLocator("http://localhost/contextPath/", null, "foo/bar")).thenReturn(fooBar);
		DavLocatorImpl result = fooBarBaz.resolveParent();
		Assertions.assertEquals(fooBar, result);
	}

	@Test
	public void testResolveParent2() {
		DavLocatorImpl foo = new DavLocatorImpl(factory, "http://localhost/contextPath/", "foo");
		DavLocatorImpl root = new DavLocatorImpl(factory, "http://localhost/contextPath/", "");
		Mockito.when(factory.createResourceLocator("http://localhost/contextPath/", null, "")).thenReturn(root);
		DavLocatorImpl result = foo.resolveParent();
		Assertions.assertEquals(root, result);
	}

	@Test
	public void testResolveParent3() {
		DavLocatorImpl root = new DavLocatorImpl(factory, "http://localhost/contextPath/", "");
		Assertions.assertNull(root.resolveParent());
	}

	@Test
	public void testGetFactory() {
		Assertions.assertSame(factory, locator.getFactory());
	}

}
