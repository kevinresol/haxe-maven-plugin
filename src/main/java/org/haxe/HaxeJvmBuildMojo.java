package org.haxe;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

/**
 * Goal which compiles Haxe sources into java bytecode
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class HaxeJvmBuildMojo extends AbstractMojo {
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
     * Location to put the generated classes.
     */
    @Parameter(defaultValue = "${project.build.directory}/classes", property = "output", required = true)
    private File output;

    public void execute() throws MojoExecutionException {
        try {
            Hxml haxeConfig = readHxml();

            Process haxeProc = Runtime.getRuntime().exec(ArrayUtils.addAll(new String[] { haxe, hxml.getCanonicalPath() }, args));
            
            inheritIO(haxeProc.getInputStream(), System.out);
            inheritIO(haxeProc.getErrorStream(), System.err);

            if (haxeProc.waitFor() != 0) {
                throw new MojoExecutionException(
                        "Haxe Compilation Failed:\n" + new String(haxeProc.getErrorStream().readAllBytes()));
            }

            // Unzip haxe-generated jar into target/classes such that maven can pack those
            // haxe-generated .class files into the final jar
            // Ideally there should be an option in haxe to produce these .class files
            // without packing into a jar
            unzip(haxeConfig.outputPath.toFile(), output);

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

        } catch (Exception err) {
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

    private Hxml readHxml() throws FileNotFoundException, IOException {

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

            return new Hxml(Paths.get(outputPath), mainClass == null ? null : new HaxeClass(mainClass));
        }
    }

    // https://www.baeldung.com/java-compress-and-uncompress#unzip
    private static void unzip(File zip, File output) throws FileNotFoundException, IOException {
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
    
    private static void inheritIO(final InputStream src, final PrintStream dest) {
        new Thread(new Runnable() {
            public void run() {
                try(Scanner scanner = new Scanner(src)) {
                    while (scanner.hasNextLine()) {
                        dest.println(scanner.nextLine());
                    }
                }
            }
        }).start();
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