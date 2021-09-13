package com.kevinresol;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;

import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

import com.kevinresol.HaxeBuildMojo;

public class HaxeBuildMojoTest {
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
    public void testBuild() throws Exception {
        File pom = new File("target/test-classes/project-to-test/");
        assertNotNull(pom);
        assertTrue(pom.exists());

        HaxeBuildMojo mojo = (HaxeBuildMojo) rule.lookupConfiguredMojo(pom, "build");
        assertNotNull(mojo);
        mojo.execute();

        File destination = (File) rule.getVariableValueFromObject(mojo, "destination");
        assertNotNull(destination);
        assertTrue(destination.exists());

        File main = new File( destination, "haxe/root/Main.class" );
        assertTrue( main.exists() );

    }

    /** Do not need the MojoRule. */
    // @WithoutMojo
    // @Test
    // public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() {
    //     assertTrue(true);
    // }

}
