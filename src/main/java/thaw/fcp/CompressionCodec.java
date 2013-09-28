/*
 * Thaw - CompressionCodec.java - Copyright © 2013 David Roden
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

package thaw.fcp;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Container for compression codec information sent to us by the node in the
 * {@code NodeHello} FCP message.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class CompressionCodec {

	/** The pattern to parse codec names with. */
	private static final Pattern codecPattern = Pattern.compile(" ?([A-Za-z0-9_]+)\\((\\d+)\\)");

	/** The name of the codec. */
	private final String name;

	/** The code of the codec. */
	private final int code;

	/**
	 * Creates a new compression codec.
	 *
	 * @param name
	 * 		The name of the codec
	 * @param code
	 * 		The code of the codec
	 */
	public CompressionCodec(String name, int code) {
		this.name = name;
		this.code = code;
	}

	//
	// ACCESSORS
	//

	/**
	 * Returns the name of the codec.
	 *
	 * @return The name of the codec
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the code of the codec. This code is specific to the node it’s being
	 * sent from.
	 *
	 * @return The code of the codec
	 */
	public int getCode() {
		return code;
	}

	//
	// ACTION METHODS
	//

	/**
	 * Parses the compression codecs from the given compression codecs line which
	 * is contained the {@code NodeHello} FCP message sent to us by the node. The
	 * format of this message is “&lt;number&gt; - &lt;name&gt;(&lt;code&gt;)[,&lt;name&gt;(&lt;code&gt;)[,…]]”.
	 *
	 * @param compressionCodecs
	 * 		The compression codec information from the node
	 * @return The parsed compression codecs
	 * @throws WrongFormat
	 * 		if the line can not be parsed
	 */
	public static Collection<CompressionCodec> parseCodecs(String compressionCodecs) throws WrongFormat {
		List<CompressionCodec> codecs = new ArrayList<CompressionCodec>();
		String[] codecList = compressionCodecs.substring(compressionCodecs.indexOf('-') + 2).split(",");
		for (String codec : codecList) {
			codecs.add(parseCodec(codec));
		}
		return codecs;
	}

	/**
	 * Parses a single compression codec from the given string. The string has to
	 * be in the format “&lt;name&gt(&lt;code&gt;)”. The name is currently
	 * restricted to letters A-Z (case-insensitive), the digits 0-9, and the
	 * underscore.
	 *
	 * @param codec
	 * 		The codec information to parse
	 * @return The parsed codec
	 * @throws WrongFormat
	 * 		if the codec can not be parsed
	 */
	public static CompressionCodec parseCodec(String codec) throws WrongFormat {
		Matcher matcher = codecPattern.matcher(codec);
		if (!matcher.matches()) {
			throw new WrongFormat();
		}
		String codecName = matcher.group(1);
		int codecCode = Integer.valueOf(matcher.group(2));
		return new CompressionCodec(codecName, codecCode);
	}

	//
	// OBJECT METHODS
	//

	@Override
	public int hashCode() {
		return name.hashCode() ^ (code << 16);
	}

	@Override
	public boolean equals(Object object) {
		if (!(object instanceof CompressionCodec)) {
			return false;
		}
		CompressionCodec compressionCodec = (CompressionCodec) object;
		return getName().equals(compressionCodec.getName()) && (getCode() == compressionCodec.getCode());
	}

	@Override
	public String toString() {
		return format("%s(%d)", getName(), getCode());
	}

	/**
	 * Exception that signals that a codec could not be parsed from a string.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public static class WrongFormat extends RuntimeException {

	}

}
