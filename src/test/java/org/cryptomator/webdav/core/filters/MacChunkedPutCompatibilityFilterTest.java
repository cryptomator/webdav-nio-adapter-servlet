/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.webdav.core.filters;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MacChunkedPutCompatibilityFilterTest {

	private MacChunkedPutCompatibilityFilter filter;
	private FilterChain chain;
	private HttpServletRequest request;
	private HttpServletResponse response;

	@BeforeEach
	public void setup() {
		filter = new MacChunkedPutCompatibilityFilter();
		chain = Mockito.mock(FilterChain.class);
		request = Mockito.mock(HttpServletRequest.class);
		response = Mockito.mock(HttpServletResponse.class);
	}

	@Test
	public void testUnfilteredGetRequest() throws IOException, ServletException {
		Mockito.when(request.getMethod()).thenReturn("GET");
		filter.doFilter(request, response, chain);

		ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
		Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
		Assertions.assertSame(request, wrappedReq.getValue());
	}

	@Test
	public void testUnfilteredPutRequest1() throws IOException, ServletException {
		Mockito.when(request.getMethod()).thenReturn("PUT");
		Mockito.when(request.getHeader("Transfer-Encoding")).thenReturn(null);
		filter.doFilter(request, response, chain);

		ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
		Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
		Assertions.assertSame(request, wrappedReq.getValue());
	}

	@Test
	public void testUnfilteredPutRequest2() throws IOException, ServletException {
		Mockito.when(request.getMethod()).thenReturn("PUT");
		Mockito.when(request.getHeader("Transfer-Encoding")).thenReturn("chunked");
		Mockito.when(request.getHeader("X-Expected-Entity-Length")).thenReturn(null);
		filter.doFilter(request, response, chain);

		ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
		Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
		Assertions.assertSame(request, wrappedReq.getValue());
	}

	@Test
	public void testMalformedXExpectedEntityLengthHeader() throws IOException, ServletException {
		Mockito.when(request.getMethod()).thenReturn("PUT");
		Mockito.when(request.getHeader("Transfer-Encoding")).thenReturn("chunked");
		Mockito.when(request.getHeader("X-Expected-Entity-Length")).thenReturn("NaN");
		filter.doFilter(request, response, chain);

		Mockito.verify(response).sendError(Mockito.eq(HttpServletResponse.SC_BAD_REQUEST), Mockito.anyString());
		Mockito.verifyNoMoreInteractions(chain);
	}

	/* actual input stream testing */

	@Test
	public void testBoundedInputStream() throws IOException, ServletException {
		ServletInputStream in = Mockito.mock(ServletInputStream.class);

		Mockito.when(request.getMethod()).thenReturn("PUT");
		Mockito.when(request.getHeader("Transfer-Encoding")).thenReturn("chunked");
		Mockito.when(request.getHeader("X-Expected-Entity-Length")).thenReturn("5");
		Mockito.when(request.getInputStream()).thenReturn(in);
		filter.doFilter(request, response, chain);

		ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
		Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
		ServletInputStream wrappedIn = wrappedReq.getValue().getInputStream();

		Mockito.when(in.isFinished()).thenReturn(false);
		Assertions.assertFalse(wrappedIn.isFinished());

		Mockito.when(in.isReady()).thenReturn(true);
		Assertions.assertTrue(wrappedIn.isReady());

		Mockito.when(in.read()).thenReturn(0xFF);
		Assertions.assertEquals(0xFF, wrappedIn.read());

		Mockito.when(in.available()).thenReturn(100);
		Assertions.assertEquals(4, wrappedIn.available());

		Mockito.when(in.skip(2)).thenReturn(2l);
		Assertions.assertEquals(2, wrappedIn.skip(2));

		Mockito.when(in.read(Mockito.any(), Mockito.eq(0), Mockito.eq(100))).thenReturn(100);
		Mockito.when(in.read(Mockito.any(), Mockito.eq(0), Mockito.eq(2))).thenReturn(2);
		Assertions.assertEquals(2, wrappedIn.read(new byte[100], 0, 100));

		Mockito.when(in.read()).thenReturn(0xFF);
		Assertions.assertEquals(-1, wrappedIn.read());

		Mockito.when(in.isFinished()).thenReturn(false);
		Assertions.assertTrue(wrappedIn.isFinished());

		Mockito.when(in.isReady()).thenReturn(true);
		Assertions.assertFalse(wrappedIn.isReady());
	}

}
