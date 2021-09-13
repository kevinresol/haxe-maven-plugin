package com.kevinresol;

import java.nio.file.Files;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Goal which creates a hxml containing `--java-lib-extern` flags for Haxe to consume
 */
@Mojo(name = "setup", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class HaxeSetupMojo extends AbstractMojo {
    /**
     * Location of the maven project pom.xml file
     */
    @Parameter(defaultValue = "${project.basedir}/pom.xml", property = "pom", required = true)
    private File pom;

    /**
     * Location of the hxml file to write out the haxe compiler flags
     * (mainly "--java-lib-extern")
     */
    @Parameter(defaultValue = "${project.basedir}/maven.hxml", property = "hxml", required = true)
    private File hxml;

    public void execute() throws MojoExecutionException {
        try {
            // System.out.println(pom.getCanonicalPath());
            // System.out.println(hxml.getCanonicalPath());
            
            Process proc = Runtime.getRuntime()
                    .exec(new String[] { "mvn", "-f", pom.getCanonicalPath(), "dependency:build-classpath",
                            "-Dmdep.outputFile=" + hxml.getCanonicalPath(),
                            "-Dmdep.pathSeparator=" + File.pathSeparator });
                            
            Util.inheritIO(proc.getInputStream(), System.out);
            Util.inheritIO(proc.getErrorStream(), System.err);
            
            if(proc.waitFor() != 0) {
                throw new MojoExecutionException("Failed to invoke mvn");
            }

            String content = Files.readString(hxml.toPath());
            try (FileWriter writer = new FileWriter(hxml)) {
                for (String path : content.split(File.pathSeparator)) {
                    if(!path.isBlank()) {
                        writer.write("--java-lib-extern " + path + "\n");
                    }
                }
            }
        } catch (IOException|InterruptedException err) {
            err.printStackTrace();
            throw new MojoExecutionException("Failed to setup hxml", err);
        }
    }
}