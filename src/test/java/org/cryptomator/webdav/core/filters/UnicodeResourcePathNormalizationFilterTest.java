/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.webdav.core.filters;

import org.cryptomator.webdav.core.filters.UnicodeResourcePathNormalizationFilter.MultistatusHrefNormalizer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer.Form;

import static java.nio.charset.StandardCharsets.UTF_8;

public class UnicodeResourcePathNormalizationFilterTest {

	private UnicodeResourcePathNormalizationFilter filter;
	private FilterChain chain;
	private HttpServletRequest request;
	private HttpServletResponse response;

	@BeforeEach
	public void setup() {
		filter = new UnicodeResourcePathNormalizationFilter();
		chain = Mockito.mock(FilterChain.class);
		request = Mockito.mock(HttpServletRequest.class);
		response = Mockito.mock(HttpServletResponse.class);
	}

	@Nested
	public class NormalizedRequestTest {

		@BeforeEach
		public void setup() {
			Mockito.when(request.getScheme()).thenReturn("http");
			Mockito.when(request.getServerName()).thenReturn("example.com");
			Mockito.when(request.getServerPort()).thenReturn(80);
			Mockito.when(request.getContextPath()).thenReturn("/foo");
		}

		@Test
		public void testRequestWithNormalizedResourceUri() throws IOException, ServletException {
			Mockito.when(request.getRequestURI()).thenReturn("/foo/bar");
			filter.doFilter(request, response, chain);

			ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
			Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
			Mockito.verify(request, Mockito.never()).getPathInfo();
			Assertions.assertEquals("/foo/bar", wrappedReq.getValue().getRequestURI());
			Assertions.assertEquals("http://example.com/foo/bar", wrappedReq.getValue().getRequestURL().toString());
		}

		@Test
		public void testRequestWithSemicolonInURI() throws IOException, ServletException {
			Mockito.when(request.getRequestURI()).thenReturn("/foo/bar;foo");
			filter.doFilter(request, response, chain);

			ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
			Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
			Mockito.verify(request, Mockito.never()).getPathInfo();
			Assertions.assertEquals("/foo/bar;foo", wrappedReq.getValue().getRequestURI());
			Assertions.assertEquals("http://example.com/foo/bar;foo", wrappedReq.getValue().getRequestURL().toString());
		}

		@Test
		public void testRequestWithNonNormalizedResourceUri1() throws IOException, ServletException {
			Mockito.when(request.getRequestURI()).thenReturn("/foo/\u0041\u030A");
			filter.doFilter(request, response, chain);

			ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
			Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
			Mockito.verify(request, Mockito.never()).getPathInfo();
			Assertions.assertEquals("/\u00C5", wrappedReq.getValue().getPathInfo());
			Assertions.assertEquals("/foo/\u00C5", wrappedReq.getValue().getRequestURI());
			Assertions.assertEquals("http://example.com/foo/\u00C5", wrappedReq.getValue().getRequestURL().toString());
		}

		@Test
		public void testRequestWithNonNormalizedResourceUri2() throws IOException, ServletException {
			Mockito.when(request.getRequestURI()).thenReturn("/foo/O\u0308");
			filter.doFilter(request, response, chain);

			ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
			Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
			Assertions.assertEquals("http://example.com/foo/Ö", wrappedReq.getValue().getRequestURL().toString());
		}

		@Test
		public void testRequestWithNonNormalizedDestinationUri() throws IOException, ServletException {
			Mockito.when(request.getHeader("Destination")).thenReturn("http://example.com/bar/\u0041\u030A");
			filter.doFilter(request, response, chain);

			ArgumentCaptor<HttpServletRequest> wrappedReq = ArgumentCaptor.forClass(HttpServletRequest.class);
			Mockito.verify(chain).doFilter(wrappedReq.capture(), Mockito.any(ServletResponse.class));
			Assertions.assertEquals("http://example.com/bar/\u00C5", wrappedReq.getValue().getHeader("Destination"));
		}

	}

	@Nested
	public class NormalizedResponseTest {

		private ServletOutputStream out;
		private HttpServletResponse res;

		@BeforeEach
		public void setup() throws IOException, ServletException {
			out = Mockito.mock(ServletOutputStream.class);

			Mockito.when(response.getOutputStream()).thenReturn(out);
			Mockito.when(request.getMethod()).thenReturn("PROPFIND");
			Mockito.when(request.getHeader("User-Agent")).thenReturn("WebDAVFS");

			filter.doFilter(request, response, chain);

			ArgumentCaptor<HttpServletResponse> wrappedRes = ArgumentCaptor.forClass(HttpServletResponse.class);
			Mockito.verify(chain).doFilter(Mockito.any(ServletRequest.class), wrappedRes.capture());
			res = wrappedRes.getValue();
		}

		@Test
		public void testUnmodifiedNonMultistatusResponseBody() throws IOException {
			res.setStatus(200);
			Assertions.assertSame(out, res.getOutputStream());
		}

		@Test
		public void testNfdUrlsInMultistatusResponseBody() throws IOException {
			ByteArrayOutputStream nfdBody = new ByteArrayOutputStream();
			Mockito.doAnswer(invocation -> {
				int b = invocation.getArgument(0);
				nfdBody.write(b);
				return null;
			}).when(out).write(Mockito.anyInt());

			byte[] nfcBody = "<href>http://example.com/%C3%BC/</href>".getBytes(UTF_8);
			res.setStatus(207);
			res.setContentLength(nfcBody.length);
			res.getOutputStream().write(nfcBody);

			MatcherAssert.assertThat(nfdBody.toString(UTF_8), CoreMatchers.containsString("<href>http://example.com/u%cc%88/</href>"));
		}

	}

	@Nested
	public class MultistatusHrefNormalizerTest {

		@Test
		public void testPreservesXmlStructure() throws XMLStreamException {
			ByteArrayInputStream in = new ByteArrayInputStream("<?xml version=\"1.0\" ?><l:foo xmlns:l=\"LOL\"><l:bar>bar</l:bar><l:href>http://example.com/ascii/</l:href></l:foo>".getBytes(UTF_8));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (MultistatusHrefNormalizer transformer = new MultistatusHrefNormalizer(in, out, Form.NFD)) {
				transformer.transform();
			}
			String transformed = out.toString(UTF_8);
			Assertions.assertTrue(transformed.startsWith("<?xml"));
			Assertions.assertTrue(transformed.contains("<l:foo xmlns:l=\"LOL\">"));
			Assertions.assertTrue(transformed.contains("<l:bar>bar</l:bar>"));
			Assertions.assertTrue(transformed.contains("<l:href>http://example.com/ascii/</l:href>"));
			Assertions.assertTrue(transformed.endsWith("</l:foo>"));
		}

		@Test
		public void testNfcToNfd() throws XMLStreamException {
			ByteArrayInputStream in = new ByteArrayInputStream("<obj><text>\u00fc</text><href>http://example.com/%C3%BC/</href></obj>".getBytes(UTF_8));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (MultistatusHrefNormalizer transformer = new MultistatusHrefNormalizer(in, out, Form.NFD)) {
				transformer.transform();
			}
			String transformed = out.toString(UTF_8);
			Assertions.assertTrue(transformed.contains("<text>\u00fc</text>"));
			Assertions.assertTrue(transformed.contains("<href>http://example.com/u%cc%88/</href>"));
		}

		@Test
		public void testNfdToNfc() throws XMLStreamException {
			ByteArrayInputStream in = new ByteArrayInputStream("<obj><text>u\u0308</text><href>http://example.com/u%CC%88/</href></obj>".getBytes(UTF_8));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (MultistatusHrefNormalizer transformer = new MultistatusHrefNormalizer(in, out, Form.NFC)) {
				transformer.transform();
			}
			String transformed = out.toString(UTF_8);
			Assertions.assertTrue(transformed.contains("<text>u\u0308</text>"));
			Assertions.assertTrue(transformed.contains("<href>http://example.com/%c3%bc/</href>"));
		}

	}

}
