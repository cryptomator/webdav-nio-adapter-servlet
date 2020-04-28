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
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class DavFolder extends DavNode {

	private static final Logger LOG = LoggerFactory.getLogger(DavFolder.class);
	private static final DavPropertyName PROPERTY_QUOTA_AVAILABLE = DavPropertyName.create("quota-available-bytes");
	private static final DavPropertyName PROPERTY_QUOTA_USED = DavPropertyName.create("quota-used-bytes");

	public DavFolder(DavResourceFactoryImpl factory, LockManager lockManager, DavLocatorImpl locator, Path path, Optional<BasicFileAttributes> optional, DavSession session) {
		super(factory, lockManager, locator, path, optional, session);
		properties.add(new ResourceType(ResourceType.COLLECTION));
		properties.add(new DefaultDavProperty<Integer>(DavPropertyName.ISCOLLECTION, 1));
	}

	@Override
	public boolean isCollection() {
		return true;
	}

	@Override
	public void spool(OutputContext outputContext) throws IOException {
		// no-op
	}

	@Override
	public void addMember(DavResource resource, InputContext inputContext) throws DavException {
		if (resource instanceof DavFolder) {
			addMemberFolder((DavFolder) resource);
		} else if (resource instanceof DavFile) {
			assert inputContext.hasStream();
			addMemberFile((DavFile) resource, inputContext.getInputStream());
		} else {
			throw new IllegalArgumentException("Unsupported resource type: " + resource.getClass().getName());
		}
	}

	private void addMemberFolder(DavFolder memberFolder) throws DavException {
		try {
			Files.createDirectory(memberFolder.path);
		} catch (FileSystemException e) {
			String reason = Strings.nullToEmpty(e.getReason());
			if (reason.contains("path too long")) {
				throw new DavException(DavServletResponse.SC_REQUEST_URI_TOO_LONG);
			} else {
				throw new UncheckedIOException(e);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void addMemberFile(DavFile memberFile, InputStream inputStream) throws DavException {
		try (ReadableByteChannel src = Channels.newChannel(inputStream); //
				WritableByteChannel dst = Files.newByteChannel(memberFile.path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			ByteStreams.copy(src, dst);
		} catch (FileSystemException e) {
			String reason = Strings.nullToEmpty(e.getReason());
			if (reason.contains("path too long")) {
				throw new DavException(DavServletResponse.SC_REQUEST_URI_TOO_LONG);
			} else {
				throw new UncheckedIOException(e);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public DavResourceIterator getMembers() {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
			List<DavResource> children = new ArrayList<>();
			for (Path childPath : stream) {
				BasicFileAttributes childAttr = Files.readAttributes(childPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				DavLocatorImpl childLocator = locator.resolveChild(childPath.getFileName().toString());
				if (childAttr.isDirectory()) {
					DavFolder childFolder = factory.createFolder(childLocator, childPath, Optional.of(childAttr), session);
					children.add(childFolder);
				} else if (childAttr.isRegularFile()) {
					DavFile childFile = factory.createFile(childLocator, childPath, Optional.of(childAttr), session);
					children.add(childFile);
				} else {
					LOG.warn("encountered unsupported node: {}", childPath);
				}
			}
			return new DavResourceIteratorImpl(children);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void removeMember(DavResource member) throws DavException {
		for (ActiveLock lock : member.getLocks()) {
			member.unlock(lock.getToken());
		}
		if (member instanceof DavNode) {
			removeMemberInternal((DavNode) member);
		}
	}

	public void removeMemberInternal(DavNode member) throws DavException {
		try {
			// The DELETE method on a collection must act as if a "Depth: infinity" header was used on it
			MoreFiles.deleteRecursively(member.path, RecursiveDeleteOption.ALLOW_INSECURE);
		} catch (NoSuchFileException e) {
			throw new DavException(DavServletResponse.SC_NOT_FOUND);
		} catch (IOException e) {
			throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
		}
	}

	@Override
	public void move(DavResource destination) throws DavException {
		if (!exists()) {
			throw new DavException(DavServletResponse.SC_NOT_FOUND);
		} else if (destination instanceof DavNode) {
			this.moveInternal((DavNode) destination);
		} else {
			throw new IllegalArgumentException("Destination not a DavFolder: " + destination.getClass().getName());
		}
	}

	private void moveInternal(DavNode destination) throws DavException {
		if (Files.isDirectory(destination.path.getParent())) {
			try {
				Files.move(path, destination.path, StandardCopyOption.REPLACE_EXISTING);
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
			throw new DavException(DavServletResponse.SC_CONFLICT, "Destination's parent doesn't exist.");
		}
	}

	@Override
	public void copy(DavResource destination, boolean shallow) throws DavException {
		if (!exists()) {
			throw new DavException(DavServletResponse.SC_NOT_FOUND);
		} else if (destination instanceof DavNode) {
			copyInternal((DavNode) destination, shallow);
		} else {
			throw new IllegalArgumentException("Destination not a DavNode: " + destination.getClass().getName());
		}
	}

	private void copyInternal(DavNode destination, boolean shallow) throws DavException {
		assert exists();
		assert attr.isPresent();
		if (!Files.isDirectory(destination.path.getParent())) {
			throw new DavException(DavServletResponse.SC_CONFLICT, "Destination's parent doesn't exist.");
		}

		try {
			if (shallow && destination instanceof DavFolder) {
				// http://www.webdav.org/specs/rfc2518.html#copy.for.collections
				Files.createDirectory(destination.path);
				BasicFileAttributeView attrView = Files.getFileAttributeView(destination.path, BasicFileAttributeView.class);
				if (attrView != null) {
					BasicFileAttributes a = attr.get();
					attrView.setTimes(a.lastModifiedTime(), a.lastAccessTime(), a.creationTime());
				}
			} else {
				Files.walkFileTree(path, new CopyingFileVisitor(path, destination.path, StandardCopyOption.REPLACE_EXISTING));
			}
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
	}

	@Override
	public DavPropertyName[] getPropertyNames() {
		List<DavPropertyName> list = Arrays.asList(super.getPropertyNames());
		list.add(PROPERTY_QUOTA_AVAILABLE);
		list.add(PROPERTY_QUOTA_USED);
		return Iterables.toArray(list, DavPropertyName.class);
	}

	@Override
	public DavProperty<?> getProperty(DavPropertyName name) {
		if (PROPERTY_QUOTA_AVAILABLE.equals(name)) {
			try {
				long availableBytes = Files.getFileStore(path).getUsableSpace();
				return new DefaultDavProperty<Long>(name, availableBytes);
			} catch (IOException e) {
				return null;
			}
		} else if (PROPERTY_QUOTA_USED.equals(name)) {
			try {
				long availableBytes = Files.getFileStore(path).getTotalSpace();
				long freeBytes = Files.getFileStore(path).getUsableSpace();
				long usedBytes = availableBytes - freeBytes;
				return new DefaultDavProperty<Long>(name, usedBytes);
			} catch (IOException e) {
				return null;
			}
		} else {
			return super.getProperty(name);
		}
	}

}
