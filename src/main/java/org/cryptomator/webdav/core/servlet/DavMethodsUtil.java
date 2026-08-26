package org.cryptomator.webdav.core.servlet;

import org.apache.jackrabbit.webdav.DavMethods;

class DavMethodsUtil {

	private DavMethodsUtil() {}

	/**
	 * Returns for the given DAV method code the method name.
	 *
	 * @param code DAV method code as defined in {@link DavMethods}
	 * @return DAV method name
	 */
	static String getName(int code) {
		return switch (code) {
			case DavMethods.DAV_GET -> DavMethods.METHOD_GET;
			case DavMethods.DAV_HEAD -> DavMethods.METHOD_HEAD;
			case DavMethods.DAV_PROPFIND -> DavMethods.METHOD_PROPFIND;
			case DavMethods.DAV_PROPPATCH -> DavMethods.METHOD_PROPPATCH;
			case DavMethods.DAV_POST -> DavMethods.METHOD_POST;
			case DavMethods.DAV_PUT -> DavMethods.METHOD_PUT;
			case DavMethods.DAV_DELETE -> DavMethods.METHOD_DELETE;
			case DavMethods.DAV_COPY -> DavMethods.METHOD_COPY;
			case DavMethods.DAV_MOVE -> DavMethods.METHOD_MOVE;
			case DavMethods.DAV_MKCOL -> DavMethods.METHOD_MKCOL;
			case DavMethods.DAV_OPTIONS -> DavMethods.METHOD_OPTIONS;
			case DavMethods.DAV_LOCK -> DavMethods.METHOD_LOCK;
			case DavMethods.DAV_UNLOCK -> DavMethods.METHOD_UNLOCK;
			case DavMethods.DAV_ORDERPATCH -> DavMethods.METHOD_ORDERPATCH;
			case DavMethods.DAV_SUBSCRIBE -> DavMethods.METHOD_SUBSCRIBE;
			case DavMethods.DAV_UNSUBSCRIBE -> DavMethods.METHOD_UNSUBSCRIBE;
			case DavMethods.DAV_POLL -> DavMethods.METHOD_POLL;
			case DavMethods.DAV_SEARCH -> DavMethods.METHOD_SEARCH;
			case DavMethods.DAV_VERSION_CONTROL -> DavMethods.METHOD_VERSION_CONTROL;
			case DavMethods.DAV_LABEL -> DavMethods.METHOD_LABEL;
			case DavMethods.DAV_REPORT -> DavMethods.METHOD_REPORT;
			case DavMethods.DAV_CHECKIN -> DavMethods.METHOD_CHECKIN;
			case DavMethods.DAV_CHECKOUT -> DavMethods.METHOD_CHECKOUT;
			case DavMethods.DAV_UNCHECKOUT -> DavMethods.METHOD_UNCHECKOUT;
			case DavMethods.DAV_MERGE -> DavMethods.METHOD_MERGE;
			case DavMethods.DAV_UPDATE -> DavMethods.METHOD_UPDATE;
			case DavMethods.DAV_MKWORKSPACE -> DavMethods.METHOD_MKWORKSPACE;
			case DavMethods.DAV_MKACTIVITY -> DavMethods.METHOD_MKACTIVITY;
			case DavMethods.DAV_BASELINE_CONTROL -> DavMethods.METHOD_BASELINE_CONTROL;
			case DavMethods.DAV_ACL -> DavMethods.METHOD_ACL;
			case DavMethods.DAV_REBIND -> DavMethods.METHOD_REBIND;
			case DavMethods.DAV_UNBIND -> DavMethods.METHOD_UNBIND;
			case DavMethods.DAV_BIND -> DavMethods.METHOD_BIND;
			default -> "UNKNOWN";
		};
	}
}
