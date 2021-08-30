package org.haxe;

import java.nio.file.Files;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileWriter;

/**
 * Goal which creates a hxml containing `--java-lib-extern` flags for Haxe to consume
 */
@Mojo(name = "setup", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class HaxeSetupMojo extends AbstractMojo {
    /**
     * Location of the hxml file to write out the haxe compiler flags (mainly
     * "--java-lib-extern")
     */
    @Parameter(defaultValue = "${project.basedir}/maven.hxml", property = "hxml", required = true)
    private File hxml;

    public void execute() throws MojoExecutionException {
        try {
            Process proc = Runtime.getRuntime().exec(new String[] { "mvn", "dependency:build-classpath",
                    "-Dmdep.outputFile=" + hxml.getCanonicalPath(), "-Dmdep.pathSeparator=" + File.pathSeparator });
            proc.waitFor();

            String content = Files.readString(hxml.toPath());
            try (FileWriter writer = new FileWriter(hxml)) {
                for (String path : content.split(File.pathSeparator)) {
                    writer.write("--java-lib-extern " + path + "\n");
                }
            }
        } catch (Exception err) {
            throw new MojoExecutionException("Failed to setup hxml", err);
        }
    }
}