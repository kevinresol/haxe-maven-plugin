package com.kevinresol;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 * Goal which compiles Haxe sources into java bytecode
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class HaxeBuildMojo extends AbstractMojo {
    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    
    /**
     * Location of the haxe binary.
     */
    @Parameter(defaultValue = "haxe", property = "haxe", required = true)
    private String haxe;

    /**
     * Location of the hxml file.
     */
    @Parameter(defaultValue = "${project.basedir}/build.hxml", property = "hxml", required = true)
    private File hxml;

    /**
     * Location of the hxml file.
     */
    @Parameter(property = "args")
    private String[] args;

    /**
     * Location of the hxml file.
     */
    @Parameter(defaultValue = "${project.basedir}", property = "workingDirectory", required = true)
    private File workingDirectory;

    /**
     * Location to put the generated classes.
     */
    @Parameter(defaultValue = "${project.build.directory}/classes", property = "destination", required = true)
    private File destination;

    public void execute() throws MojoExecutionException {
        try {
            
            // System.out.println(hxml.getCanonicalPath());
            
            Hxml haxeConfig = readHxml();
            
            File output = workingDirectory.toPath().resolve(haxeConfig.outputPath).toFile();
            File outputDir = output.getParentFile();
            if(!outputDir.exists()) outputDir.mkdirs();
            
            
            Process haxeProc =  new ProcessBuilder(Arrays.asList(ArrayUtils.addAll(new String[] { haxe, hxml.getCanonicalPath() }, args)))
                .directory(workingDirectory)
                .start();
            
            Util.inheritIO(haxeProc.getInputStream(), System.out);
            Util.inheritIO(haxeProc.getErrorStream(), System.err);

            if (haxeProc.waitFor() != 0) {
                throw new MojoExecutionException(
                        "Haxe Compilation Failed:\n" + new String(haxeProc.getErrorStream().readAllBytes()));
            }

            // Unzip haxe-generated jar into target/classes such that maven can pack those
            // haxe-generated .class files into the final jar
            // Ideally there should be an option in haxe to produce these .class files
            // without packing into a jar
            unzip(output, destination);

            // export a `haxe.mainClass` property which can be used by plugins in later phrases, for example:
            // <plugin>
            //   <artifactId>maven-assembly-plugin</artifactId>
            //   <configuration>
            //     <archive>
            //       <manifest>
            //         <mainClass>${haxe.mainClass}</mainClass>
            //       </manifest>
            //     </archive>
            //   </configuration>
			// </plugin>
            if(haxeConfig.mainClass != null) {
                project.getProperties().setProperty("haxe.mainClass", haxeConfig.mainClass.toJavaClass());
            }

        } catch (IOException|InterruptedException err) {
            err.printStackTrace();
            throw new MojoExecutionException("Failed to compile haxe", err);
        }
    }

    // private Plugin lookupPlugin(String groupId, String artifactId) {
    //     for (Plugin plugin : project.getBuildPlugins()) {
    //         System.out.println(plugin.getGroupId());
    //         System.out.println(plugin.getArtifactId());

    //         if (plugin.getGroupId().equals(groupId) && plugin.getArtifactId().equals(artifactId)) {
    //             return plugin;
    //         }
    //     }

    //     return null;
    // }

    private Hxml readHxml() throws MojoExecutionException, IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(hxml))) {
            String line;
            String mainClass = null;
            String outputPath = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("--main")) {
                    mainClass = line.substring(7);
                } else if (line.startsWith("--jvm")) {
                    outputPath = line.substring(6);
                }
            }
            if(outputPath == null) 
                throw new MojoExecutionException("--jvm flag not found in " + hxml.getCanonicalPath());
                
            return new Hxml(Paths.get(outputPath), mainClass == null ? null : new HaxeClass(mainClass));
        }
    }

    // https://www.baeldung.com/java-compress-and-uncompress#unzip
    private static void unzip(File zip, File output) throws IOException {
        byte[] buffer = new byte[1024];
        
        try(ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry zipEntry = zis.getNextEntry();
    
            while (zipEntry != null) {
                File newFile = createFile(output, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
    
                    // write file content
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private static File createFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }
}

class Hxml {
    public final Path outputPath;
    public final HaxeClass mainClass;

    public Hxml(Path outputPath, HaxeClass mainClass) {
        this.outputPath = outputPath;
        this.mainClass = mainClass;
    }
}

class HaxeClass {
    public final String fullname;

    public HaxeClass(String fullname) {
        this.fullname = fullname;
    }

    public String toJavaClass() {
        return fullname.contains(".") ? fullname : "haxe.root." + fullname;
    }
}