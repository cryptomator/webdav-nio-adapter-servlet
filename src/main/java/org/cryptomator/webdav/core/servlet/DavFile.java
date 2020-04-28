/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.webdav.core.servlet;

import com.google.common.base.Strings;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

class DavFile extends DavNode {

	public static final Logger LOG = LoggerFactory.getLogger(DavFile.class);

	protected static final String CONTENT_TYPE_VALUE = "application/octet-stream";
	protected static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
	protected static final String CONTENT_DISPOSITION_VALUE = "attachment";
	protected static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
	protected static final String X_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";

	public DavFile(DavResourceFactoryImpl factory, LockManager lockManager, DavLocatorImpl locator, Path path, Optional<BasicFileAttributes> attr, DavSession session) {
		super(factory, lockManager, locator, path, attr, session);
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public void spool(OutputContext outputContext) throws IOException {
		assert exists();
		outputContext.setModificationTime(attr.get().lastModifiedTime().toMillis());
		if (!outputContext.hasStream()) {
			return;
		}
		outputContext.setContentType(CONTENT_TYPE_VALUE);
		outputContext.setProperty(CONTENT_DISPOSITION_HEADER, CONTENT_DISPOSITION_VALUE);
		outputContext.setProperty(X_CONTENT_TYPE_OPTIONS_HEADER, X_CONTENT_TYPE_OPTIONS_VALUE);
		outputContext.setContentLength(attr.get().size());
		Files.copy(path, outputContext.getOutputStream());
	}

	@Override
	public void addMember(DavResource resource, InputContext inputContext) throws DavException {
		throw new UnsupportedOperationException();
	}

	@Override
	public DavResourceIterator getMembers() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeMember(DavResource member) throws DavException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void move(DavResource destination) throws DavException {
		if (destination instanceof DavNode) {
			DavFile dst = (DavFile) destination;
			if (!Files.isDirectory(dst.path.getParent())) {
				throw new DavException(DavServletResponse.SC_CONFLICT, "Destination's parent doesn't exist.");
			}
			try {
				// Overwrite header already checked by AbstractWebdavServlet#validateDestination
				Files.move(path, dst.path, StandardCopyOption.REPLACE_EXISTING);
			} catch (FileSystemException e) {
				String reason = Strings.nullToEmpty(e.getReason());
				if (reason.contains("too long")) {
					// Status code 414 not applictable for things other than request uris.
					// If Destination header is too long, return status code 400:
					// https://tools.ietf.org/html/rfc4918#section-10.3
					throw new DavException(DavServletResponse.SC_BAD_REQUEST);
				} else {
					throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
				}
			} catch (IOException e) {
				throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
			}
		} else {
			throw new IllegalArgumentException("Destination not a DavNode: " + destination.getClass().getName());
		}
	}

	@Override
	public void copy(DavResource destination, boolean shallow) throws DavException {
		if (destination instanceof DavNode) {
			DavFile dst = (DavFile) destination;
			if (!Files.isDirectory(dst.path.getParent())) {
				throw new DavException(DavServletResponse.SC_CONFLICT, "Destination's parent doesn't exist.");
			}
			try {
				// Overwrite header already checked by AbstractWebdavServlet#validateDestination
				Files.copy(path, dst.path, StandardCopyOption.REPLACE_EXISTING);
			} catch (FileSystemException e) {
				String reason = Strings.nullToEmpty(e.getReason());
				if (reason.contains("path too long")) {
					// Status code 414 not applictable for things other than request uris.
					// If Destination header is too long, return status code 400:
					// https://tools.ietf.org/html/rfc4918#section-10.3
					throw new DavException(DavServletResponse.SC_BAD_REQUEST);
				} else {
					throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
				}
			} catch (IOException e) {
				throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
			}
		} else {
			throw new IllegalArgumentException("Destination not a DavFile: " + destination.getClass().getName());
		}
	}

	@Override
	public DavProperty<?> getProperty(DavPropertyName name) {
		if (DavPropertyName.GETCONTENTLENGTH.equals(name)) {
			return sizeProperty().orElse(null);
		} else {
			return super.getProperty(name);
		}
	}

	@Override
	public DavPropertySet getProperties() {
		final DavPropertySet result = super.getProperties();
		if (!result.contains(DavPropertyName.GETCONTENTLENGTH)) {
			sizeProperty().ifPresent(result::add);
		}
		return result;
	}

	private Optional<DavProperty<?>> sizeProperty() {
		return attr.map(a -> new DefaultDavProperty<Long>(DavPropertyName.GETCONTENTLENGTH, a.size()));
	}

	@Override
	public ActiveLock lock(LockInfo reqLockInfo) throws DavException {
		ActiveLock lock = super.lock(reqLockInfo);
		if (!exists()) {
			// locking non-existing resources must create a non-collection resource:
			// https://tools.ietf.org/html/rfc4918#section-9.10.4
			DavFolder parentFolder = getCollection();
			assert parentFolder != null : "File always has a folder.";
			parentFolder.addMember(this, new NullInputContext());
		}
		return lock;
	}

}
