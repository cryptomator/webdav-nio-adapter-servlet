/*******************************************************************************
 * Copyright (c) 2017 Skymatic UG (haftungsbeschränkt).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE file.
 *******************************************************************************/
package org.cryptomator.webdav.core.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

public class LoggingFilter implements HttpFilter {

	private static final Logger LOG = LoggerFactory.getLogger(LoggingFilter.class);
	private final AtomicLong REQUEST_ID_GEN = new AtomicLong();

	@Override
	public void doFilterHttp(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (LOG.isDebugEnabled()) {
			long requestId = REQUEST_ID_GEN.getAndIncrement();
			LOG.debug("REQUEST {}:\n{} {} {}\n{}", requestId, request.getMethod(), request.getRequestURI(), request.getProtocol(), headers(request));
			chain.doFilter(request, response);
			LOG.debug("RESPONSE {}:\n{}\n{}", requestId, response.getStatus(), headers(response));
		} else {
			chain.doFilter(request, response);
		}
	}

	private String headers(HttpServletResponse response) {
		StringBuilder result = new StringBuilder();
		for (String headerName : response.getHeaderNames()) {
			for (String value : response.getHeaders(headerName)) {
				result.append(headerName).append(": ").append(value).append('\n');
			}
		}
		return result.toString();
	}

	private String headers(HttpServletRequest request) {
		StringBuilder result = new StringBuilder();
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			Enumeration<String> values = request.getHeaders(headerName);
			while (values.hasMoreElements()) {
				result.append(headerName).append(": ").append(values.nextElement()).append('\n');
			}
		}
		return result.toString();
	}

}
