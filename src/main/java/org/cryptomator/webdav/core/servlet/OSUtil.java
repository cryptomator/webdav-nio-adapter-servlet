package org.cryptomator.webdav.core.servlet;

final class OSUtil {

	static boolean isMacOS15_4orNewer() {
		var osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("mac")) {
			var osVersion = System.getProperty("os.version").split("\\.");
			if (osVersion.length >= 2) {
				try {
					var majorVersion = Integer.parseInt(osVersion[0]);
					var minorVersion = Integer.parseInt(osVersion[1]);
					return majorVersion == 15 && minorVersion >= 4 || majorVersion > 16;
				} catch (NumberFormatException e) {
					//no-op
				}
			}
		}
		return false;
	}
}
