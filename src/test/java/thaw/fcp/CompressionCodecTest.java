/*
 * Thaw - CompressionCodecTest.java - Copyright © 2013 David Roden
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.Collection;

import org.junit.Test;

/**
 * Test case for {@link CompressionCodec}.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class CompressionCodecTest {

	@Test
	public void testParsingASingleCodec() {
		CompressionCodec codec = CompressionCodec.parseCodec("BZIP2(3)");
		assertThat(codec, notNullValue());
		assertThat(codec.getName(), is("BZIP2"));
		assertThat(codec.getCode(), is(3));
	}

	@Test
	public void testParsingCodecs() {
		Collection<CompressionCodec> compressionCodecs = CompressionCodec.parseCodecs("4 - GZIP(0), BZIP2(1), LZMA(2), LZMA_NEW(3)");
		assertThat(compressionCodecs, notNullValue());
		assertThat(compressionCodecs.size(), is(4));
		assertThat(compressionCodecs, containsInAnyOrder(new CompressionCodec("GZIP", 0), new CompressionCodec("BZIP2", 1), new CompressionCodec("LZMA", 2), new CompressionCodec("LZMA_NEW", 3)));
	}

}
