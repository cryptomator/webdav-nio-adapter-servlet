/*******************************************************************************
 * Copyright (c) 2016, 2017 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.webdav.core.servlet;

import com.google.common.collect.ImmutableSet;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.lock.LockManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Function;

class DavResourceFactoryImpl implements DavResourceFactory {

	private static final String RANGE_HEADER = "Range";
	private static final String IF_RANGE_HEADER = "If-Range";

	private final LockManager lockManager = new ExclusiveSharedLockManager();
	private final Function<String, Path> urlResolver;

	public DavResourceFactoryImpl(Function<String, Path> urlResolver) {
		this.urlResolver = urlResolver;
	}

	private Path resolveUrl(String relativeUrl) throws DavException {
		try {
			return urlResolver.apply(relativeUrl);
		} catch (IllegalArgumentException e) {
			throw new DavException(DavServletResponse.SC_NOT_FOUND, e.getMessage(), e, null);
		}
	}

	@Override
	public DavResource createResource(DavResourceLocator locator, DavServletRequest request, DavServletResponse response) throws DavException {
		if (locator instanceof DavLocatorImpl && locator.equals(request.getRequestLocator())) {
			return createRequestResource((DavLocatorImpl) locator, request, response);
		} else if (locator instanceof DavLocatorImpl && locator.equals(request.getDestinationLocator())) {
			return createDestinationResource((DavLocatorImpl) locator, request, response);
		} else {
			throw new IllegalArgumentException("Unsupported locator of type " + locator.getClass());
		}
	}

	private DavResource createRequestResource(DavLocatorImpl locator, DavServletRequest request, DavServletResponse response) throws DavException {
		assert locator.equals(request.getRequestLocator());
		Path p = resolveUrl(locator.getResourcePath());
		Optional<BasicFileAttributes> attr = readBasicFileAttributes(p);
		if (DavMethods.METHOD_PUT.equals(request.getMethod())) {
			checkPreconditionsForPut(p, attr);
			return createFile(locator, p, Optional.empty(), request.getDavSession());
		} else if (DavMethods.METHOD_MKCOL.equals(request.getMethod())) {
			checkPreconditionsForMkcol(p, attr);
			return createFolder(locator, p, Optional.empty(), request.getDavSession());
		} else if (!attr.isPresent() && DavMethods.METHOD_LOCK.equals(request.getMethod())) {
			// locking non-existing resources must create a non-collection resource:
			// https://tools.ietf.org/html/rfc4918#section-9.10.4
			// See also: DavFile#lock(...)
			return createFile(locator, p, Optional.empty(), request.getDavSession());
		} else if (!attr.isPresent()) {
			throw new DavException(DavServletResponse.SC_NOT_FOUND);
		} else if (attr.get().isDirectory()) {
			return createFolder(locator, p, attr, request.getDavSession());
		} else if (attr.get().isRegularFile() && DavMethods.METHOD_GET.equals(request.getMethod()) && request.getHeader(RANGE_HEADER) != null) {
			return createFileRange(locator, p, attr.get(), request.getDavSession(), request, response);
		} else if (attr.get().isRegularFile()) {
			return createFile(locator, p, attr, request.getDavSession());
		} else {
			throw new DavException(DavServletResponse.SC_NOT_FOUND, "Node not a file or directory: " + p);
		}
	}

	private void checkPreconditionsForPut(Path p, Optional<BasicFileAttributes> attr) throws DavException {
		if (attr.isPresent() && !attr.get().isRegularFile()) {
			throw new DavException(DavServletResponse.SC_CONFLICT, p + " already exists.");
		}
	}

	private void checkPreconditionsForMkcol(Path p, Optional<BasicFileAttributes> attr) throws DavException {
		if (attr.isPresent()) {
			// status code 405 required by https://tools.ietf.org/html/rfc2518#section-8.3.2
			throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED, p + " already exists.");
		}
	}

	private DavResource createDestinationResource(DavLocatorImpl locator, DavServletRequest request, DavServletResponse response) throws DavException {
		assert locator.equals(request.getDestinationLocator());
		assert ImmutableSet.of(DavMethods.METHOD_MOVE, DavMethods.METHOD_COPY).contains(request.getMethod());
		Path srcP = resolveUrl(request.getRequestLocator().getResourcePath());
		Path dstP = resolveUrl(locator.getResourcePath());
		Optional<BasicFileAttributes> srcAttr = readBasicFileAttributes(srcP);
		Optional<BasicFileAttributes> dstAttr = readBasicFileAttributes(dstP);
		if (!srcAttr.isPresent()) {
			throw new DavException(DavServletResponse.SC_NOT_FOUND);
		} else if (srcAttr.get().isDirectory()) {
			return createFolder(locator, dstP, dstAttr, request.getDavSession());
		} else {
			return createFile(locator, dstP, dstAttr, request.getDavSession());
		}
	}

	@Override
	public DavResource createResource(DavResourceLocator locator, DavSession session) throws DavException {
		if (locator instanceof DavLocatorImpl) {
			return createResourceInternal((DavLocatorImpl) locator, session);
		} else {
			throw new IllegalArgumentException("Unsupported locator of type " + locator.getClass());
		}
	}

	private DavResource createResourceInternal(DavLocatorImpl locator, DavSession session) throws DavException {
		Path p = resolveUrl(locator.getResourcePath());
		Optional<BasicFileAttributes> attr = readBasicFileAttributes(p);
		if (!attr.isPresent()) {
			throw new DavException(DavServletResponse.SC_NOT_FOUND);
		} else if (attr.get().isDirectory()) {
			return createFolder(locator, p, attr, session);
		} else {
			return createFile(locator, p, attr, session);
		}
	}

	/**
	 * @return BasicFileAttributes or {@link Optional#empty()} if the file/folder for the given path does not exist.
	 * @throws DavException If an {@link IOException} occured during {@link Files#readAttributes(Path, Class, java.nio.file.LinkOption...)}.
	 */
	private Optional<BasicFileAttributes> readBasicFileAttributes(Path path) throws DavException {
		try {
			return Optional.of(Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
		} catch (NoSuchFileException e) {
			return Optional.empty();
		} catch (IOException e) {
			throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
		}
	}

	DavFolder createFolder(DavLocatorImpl locator, Path path, Optional<BasicFileAttributes> attr, DavSession session) {
		return new DavFolder(this, lockManager, locator, path, attr, session);
	}

	DavFile createFile(DavLocatorImpl locator, Path path, Optional<BasicFileAttributes> attr, DavSession session) {
		return new DavFile(this, lockManager, locator, path, attr, session);
	}

	private DavFile createFileRange(DavLocatorImpl locator, Path path, BasicFileAttributes attr, DavSession session, DavServletRequest request, DavServletResponse response) throws DavException {
		// 200 for "normal" resources, if if-range is not satisified:
		final String ifRangeHeader = request.getHeader(IF_RANGE_HEADER);
		if (!isIfRangeHeaderSatisfied(attr, ifRangeHeader)) {
			return createFile(locator, path, Optional.of(attr), session);
		}

		final String rangeHeader = request.getHeader(RANGE_HEADER);
		try {
			// 206 for ranged resources:
			final ByteRange byteRange = ByteRange.parse(rangeHeader);
			response.setStatus(DavServletResponse.SC_PARTIAL_CONTENT);
			return new DavFileWithRange(this, lockManager, locator, path, attr, session, byteRange);
		} catch (ByteRange.UnsupportedRangeException ex) {
			return createFile(locator, path, Optional.of(attr), session);
		} catch (ByteRange.MalformedByteRangeException e) {
			throw new DavException(DavServletResponse.SC_BAD_REQUEST, "Malformed range header: " + rangeHeader);
		}
	}

	/**
	 * @return <code>true</code> if a partial response should be generated according to an If-Range precondition.
	 */
	private boolean isIfRangeHeaderSatisfied(BasicFileAttributes attr, String ifRangeHeader) throws DavException {
		if (ifRangeHeader == null) {
			// no header set -> satisfied implicitly
			return true;
		} else {
			try {
				Instant expectedTime = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifRangeHeader));
				Instant actualTime = attr.lastModifiedTime().toInstant();
				return expectedTime.compareTo(actualTime) == 0;
			} catch (DateTimeParseException e) {
				throw new DavException(DavServletResponse.SC_BAD_REQUEST, "Unsupported If-Range header: " + ifRangeHeader);
			}
		}
	}

}
