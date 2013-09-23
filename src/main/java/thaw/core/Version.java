/*
 * Thaw - Version.java - Copyright © 2013 David Roden
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package thaw.core;

import com.google.common.collect.ComparisonChain;

/**
 * Container for version information.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class Version implements Comparable<Version> {

	/** The major version number. */
	private final int major;

	/** The minor version number. */
	private final int minor;

	/** The update version number. */
	private final int update;

	/**
	 * Creates a new version with the given major, minor, and update number.
	 *
	 * @param major
	 * 		The major version number
	 * @param minor
	 * 		The minor version number
	 * @param update
	 * 		The update version number
	 */
	public Version(int major, int minor, int update) {
		this.major = major;
		this.minor = minor;
		this.update = update;
	}

	//
	// STATIC METHODS
	//

	/**
	 * Parses the given string into a Version. The string is required to be of the
	 * format “<major>.<minor>.<update>”.
	 *
	 * @param versionString
	 * 		The version string to parse
	 * @return The parsed version
	 */
	public static Version valueOf(String versionString) {
		String[] numbers = versionString.split("\\.");
		int major = Integer.parseInt(numbers[0]);
		int minor = Integer.parseInt(numbers[1]);
		int update = Integer.parseInt(numbers[2]);
		return new Version(major, minor, update);
	}

	//
	// COMPARABLE METHODS
	//

	@Override
	public int compareTo(Version version) {
		return ComparisonChain.start()
				.compare(major, version.major)
				.compare(minor, version.minor)
				.compare(update, version.update)
				.result();
	}

	//
	// OBJECT METHODS
	//

	@Override
	public String toString() {
		return String.format("%d.%d.%d", major, minor, update);
	}

}
