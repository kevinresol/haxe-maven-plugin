package com.kevinresol;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;

import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

import com.kevinresol.HaxeSetupMojo;

public class HaxeSetupMojoTest {
    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() throws Throwable {
        }

        @Override
        protected void after() {
        }
    };

    /**
     * @throws Exception if any
     */
    @Test
    public void testSetup() throws Exception {
        File pom = new File("target/test-classes/project-to-test/");
        assertNotNull(pom);
        assertTrue(pom.exists());

        HaxeSetupMojo mojo = (HaxeSetupMojo) rule.lookupConfiguredMojo(pom, "setup");
        assertNotNull(mojo);
        mojo.execute();

        File destination = (File) rule.getVariableValueFromObject(mojo, "hxml");
        assertNotNull(destination);
        assertTrue(destination.exists());

    }

    /** Do not need the MojoRule. */
    // @WithoutMojo
    // @Test
    // public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() {
    //     assertTrue(true);
    // }

}
