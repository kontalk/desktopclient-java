/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kontalk;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Ignore;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class KontalkTest {
    @ClassRule
    public static TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

    private static Path APP_DIR;

    public KontalkTest() {
    }

    @BeforeClass
    public static void setUpClass() {
        APP_DIR = TEMP_FOLDER.getRoot().toPath().resolve("app_dir");
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of start method, of class Kontalk.
     */
    @Test
    public void testStart() {
        System.out.println("start");
        Kontalk app = new Kontalk(APP_DIR);
        int returnCode = app.start(false);
        assertEquals(returnCode, 0);
        // TODO stop
    }

    /**
     * Test of main method, of class Kontalk.
     */
    @Test
    @Ignore
    public void testMain() {
        System.out.println("main");
        String[] args = null;
        Kontalk.main(args);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }
}
