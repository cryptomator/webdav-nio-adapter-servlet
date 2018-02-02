/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.webdav.core.servlet;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.header.IfHeader;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.server.AbstractWebdavServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public abstract class AbstractNioWebDavServlet extends AbstractWebdavServlet {

	private static final String NO_LOCK = "DAV:no-lock";
	private static final Logger LOG = LoggerFactory.getLogger(AbstractNioWebDavServlet.class);

	private final DavSessionProvider davSessionProvider = new DavSessionProviderImpl();
	private final DavLocatorFactory davLocatorFactory = new DavLocatorFactoryImpl();
	private final DavResourceFactoryImpl davResourceFactory = new DavResourceFactoryImpl(this::resolveUrl);

	/**
	 * @param relativeUrl An url
	 * @return A path
	 * @throws IllegalArgumentException If no path could be found for the given url.
	 */
	protected abstract Path resolveUrl(String relativeUrl) throws IllegalArgumentException;

	@Override
	protected boolean isPreconditionValid(WebdavRequest request, DavResource resource) {
		IfHeader ifHeader = new IfHeader(request);
		if (ifHeader.hasValue() && Iterators.all(ifHeader.getAllTokens(), Predicates.equalTo(NO_LOCK))) {
			// https://tools.ietf.org/html/rfc4918#section-10.4.8:
			// "DAV:no-lock" is known to never represent a current lock token.
			return false;
		} else if (ifHeader.hasValue() && Iterators.any(ifHeader.getAllNotTokens(), Predicates.equalTo(NO_LOCK))) {
			// by applying "Not" to a state token that is known not to be current, the Condition always evaluates to true.
			return true;
		} else {
			return request.matchesIfHeader(resource);
		}
	}

	@Override
	public DavSessionProvider getDavSessionProvider() {
		return davSessionProvider;
	}

	@Override
	public void setDavSessionProvider(DavSessionProvider davSessionProvider) {
		throw new UnsupportedOperationException("Setting davSessionProvider not supported.");
	}

	@Override
	public DavLocatorFactory getLocatorFactory() {
		return davLocatorFactory;
	}

	@Override
	public void setLocatorFactory(DavLocatorFactory locatorFactory) {
		throw new UnsupportedOperationException("Setting locatorFactory not supported.");
	}

	@Override
	public DavResourceFactory getResourceFactory() {
		return davResourceFactory;
	}

	@Override
	public void setResourceFactory(DavResourceFactory resourceFactory) {
		throw new UnsupportedOperationException("Setting resourceFactory not supported.");
	}

	/* Unchecked DAV exception rewrapping and logging */

	@Override
	protected boolean execute(WebdavRequest request, WebdavResponse response, int method, DavResource resource) throws ServletException, IOException, DavException {
		try {
			try {
				return super.execute(request, response, method, resource);
			} catch (UncheckedDavException e) {
				throw e.toDavException();
			}
		} catch (DavException e) {
			if (e.getErrorCode() == DavServletResponse.SC_INTERNAL_SERVER_ERROR) {
				LOG.error("Unexpected DavException.", e);
			}
			throw e;
		}
	}

	/* GET stuff */

	@Override
	protected void doGet(WebdavRequest request, WebdavResponse response, DavResource resource) throws IOException, DavException {
		super.doGet(request, response, resource);
	}

	/* LOCK stuff */

	@Override
	protected int validateDestination(DavResource destResource, WebdavRequest request, boolean checkHeader) throws DavException {
		if (isLocked(destResource) && !hasCorrectLockTokens(request.getDavSession(), destResource)) {
			throw new DavException(DavServletResponse.SC_LOCKED, "The destination resource is locked");
		}
		return super.validateDestination(destResource, request, checkHeader);
	}

	@Override
	protected void doPut(WebdavRequest request, WebdavResponse response, DavResource resource) throws IOException, DavException {
		if (isLocked(resource) && !hasCorrectLockTokens(request.getDavSession(), resource)) {
			throw new DavException(DavServletResponse.SC_LOCKED, "The resource is locked");
		}
		super.doPut(request, response, resource);
	}

	@Override
	protected void doDelete(WebdavRequest request, WebdavResponse response, DavResource resource) throws IOException, DavException {
		if (isLocked(resource) && !hasCorrectLockTokens(request.getDavSession(), resource)) {
			throw new DavException(DavServletResponse.SC_LOCKED, "The resource is locked");
		}
		super.doDelete(request, response, resource);
	}

	@Override
	protected void doMove(WebdavRequest request, WebdavResponse response, DavResource resource) throws IOException, DavException {
		if (isLocked(resource) && !hasCorrectLockTokens(request.getDavSession(), resource)) {
			throw new DavException(DavServletResponse.SC_LOCKED, "The source resource is locked");
		}
		super.doMove(request, response, resource);
	}

	@Override
	protected void doPropPatch(WebdavRequest request, WebdavResponse response, DavResource resource) throws IOException, DavException {
		if (isLocked(resource) && !hasCorrectLockTokens(request.getDavSession(), resource)) {
			throw new DavException(DavServletResponse.SC_LOCKED, "The resource is locked");
		}
		super.doPropPatch(request, response, resource);
	}

	private boolean hasCorrectLockTokens(DavSession session, DavResource resource) {
		boolean access = false;

		final Set<String> providedLockTokens = ImmutableSet.copyOf(session.getLockTokens());
		for (ActiveLock lock : resource.getLocks()) {
			access |= providedLockTokens.contains(lock.getToken());
		}
		return access;
	}

	private boolean isLocked(DavResource resource) {
		return resource.hasLock(Type.WRITE, Scope.EXCLUSIVE) || resource.hasLock(Type.WRITE, Scope.SHARED);
	}

}
