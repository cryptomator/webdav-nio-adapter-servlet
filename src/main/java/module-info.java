module org.cryptomator.frontend.webdav.servlet {
	requires com.google.common;
	requires java.xml;
	requires org.slf4j;

	requires transitive jakarta.servlet;
	requires transitive org.apache.jackrabbit.webdav;

	exports org.cryptomator.webdav.core.filters;
	exports org.cryptomator.webdav.core.servlet;
}
