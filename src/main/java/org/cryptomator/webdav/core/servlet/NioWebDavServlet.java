package org.cryptomator.webdav.core.servlet;

import javax.servlet.ServletException;
import java.nio.file.Path;

public class NioWebDavServlet extends AbstractNioWebDavServlet {

	public static final String INIT_PARAM_ROOT_PATH = "rootPath";
	private Path rootPath;

	@Override
	public void init() throws ServletException {
		super.init();
		rootPath = Path.of(getInitParameter(INIT_PARAM_ROOT_PATH));
	}

	@Override
	protected Path resolveUrl(String relativeUrl) throws IllegalArgumentException {
		return rootPath.resolve(relativeUrl);
	}

}
