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
		switch (code) {
			case DavMethods.DAV_GET:
				return DavMethods.METHOD_GET;
			case DavMethods.DAV_HEAD:
				return DavMethods.METHOD_HEAD;
			case DavMethods.DAV_PROPFIND:
				return DavMethods.METHOD_PROPFIND;
			case DavMethods.DAV_PROPPATCH:
				return DavMethods.METHOD_PROPPATCH;
			case DavMethods.DAV_POST:
				return DavMethods.METHOD_POST;
			case DavMethods.DAV_PUT:
				return DavMethods.METHOD_PUT;
			case DavMethods.DAV_DELETE:
				return DavMethods.METHOD_DELETE;
			case DavMethods.DAV_COPY:
				return DavMethods.METHOD_COPY;
			case DavMethods.DAV_MOVE:
				return DavMethods.METHOD_MOVE;
			case DavMethods.DAV_MKCOL:
				return DavMethods.METHOD_MKCOL;
			case DavMethods.DAV_OPTIONS:
				return DavMethods.METHOD_OPTIONS;
			case DavMethods.DAV_LOCK:
				return DavMethods.METHOD_LOCK;
			case DavMethods.DAV_UNLOCK:
				return DavMethods.METHOD_UNLOCK;
			case DavMethods.DAV_ORDERPATCH:
				return DavMethods.METHOD_ORDERPATCH;
			case DavMethods.DAV_SUBSCRIBE:
				return DavMethods.METHOD_SUBSCRIBE;
			case DavMethods.DAV_UNSUBSCRIBE:
				return DavMethods.METHOD_UNSUBSCRIBE;
			case DavMethods.DAV_POLL:
				return DavMethods.METHOD_POLL;
			case DavMethods.DAV_SEARCH:
				return DavMethods.METHOD_SEARCH;
			case DavMethods.DAV_VERSION_CONTROL:
				return DavMethods.METHOD_VERSION_CONTROL;
			case DavMethods.DAV_LABEL:
				return DavMethods.METHOD_LABEL;
			case DavMethods.DAV_REPORT:
				return DavMethods.METHOD_REPORT;
			case DavMethods.DAV_CHECKIN:
				return DavMethods.METHOD_CHECKIN;
			case DavMethods.DAV_CHECKOUT:
				return DavMethods.METHOD_CHECKOUT;
			case DavMethods.DAV_UNCHECKOUT:
				return DavMethods.METHOD_UNCHECKOUT;
			case DavMethods.DAV_MERGE:
				return DavMethods.METHOD_MERGE;
			case DavMethods.DAV_UPDATE:
				return DavMethods.METHOD_UPDATE;
			case DavMethods.DAV_MKWORKSPACE:
				return DavMethods.METHOD_MKWORKSPACE;
			case DavMethods.DAV_MKACTIVITY:
				return DavMethods.METHOD_MKACTIVITY;
			case DavMethods.DAV_BASELINE_CONTROL:
				return DavMethods.METHOD_BASELINE_CONTROL;
			case DavMethods.DAV_ACL:
				return DavMethods.METHOD_ACL;
			case DavMethods.DAV_REBIND:
				return DavMethods.METHOD_REBIND;
			case DavMethods.DAV_UNBIND:
				return DavMethods.METHOD_UNBIND;
			case DavMethods.DAV_BIND:
				return DavMethods.METHOD_BIND;
			default:
				// any other method
				return "UNKNOWN";
		}
	}
}
