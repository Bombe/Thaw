/*
 * Thaw - ConfigTest.java - Copyright © 2013 David Roden
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

/**
 * Tests for {@link Config}.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class ConfigTest {

	@Test
	public void testStoringAndRetrievingValues() {
		Config config = new Config(null, ".");
		config.setValue("Test", "Foo");
		String value = config.getValue("Test");
		assertThat(value, is("Foo"));
	}

	@Test
	public void testSettingDefaultValue() {
		Config config = new Config(null, ".");
		config.setDefaultValue("Test", "Foo");
		assertThat(config.getValue("Test"), is("Foo"));
	}

	@Test
	public void testStoringAndRetrievingValueBeforeSettingDefaultValue() {
		Config config = new Config(null, ".");
		config.setValue("Test", "Foo");
		config.setDefaultValue("Test", "Bar");
		assertThat(config.getValue("Test"), is("Foo"));
	}

	@Test
	public void testStoringAndRetrievingValueAfterSettingDefaultValue() {
		Config config = new Config(null, ".");
		config.setDefaultValue("Test", "Bar");
		config.setValue("Test", "Foo");
		assertThat(config.getValue("Test"), is("Foo"));
	}

}
