package org.honton.chas.bom;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Create a Bill of Materials from a set of jars or tar file. */
@Mojo(name = "extract", requiresProject = false)
public class CreateBomMojo extends AbstractMojo {

  /** The groupId for the bom. */
  @Parameter(property = "bom.groupId", defaultValue = "extracted")
  private String groupId;

  /** The artifactId for the bom. */
  @Parameter(property = "bom.artifactId", defaultValue = "bom")
  private String artifactId;

  /** The version for the bom. */
  @Parameter(property = "bom.version", defaultValue = "1.0.0-SNAPSHOT")
  private String version;

  /** The source directory or tar. */
  @Parameter(property = "bom.source")
  private String source;

  /** The name of the Bill of Materials file */
  @Parameter(property = "bom.bom", defaultValue = "bom.xml")
  private String bom;

  /**
   * The directory to receive a copy of unknown jars
   *
   * @since 1.1.0
   */
  @Parameter(property = "bom.unknowns")
  private String unknowns;

  private Sha1 sha1;
  private QueryCentral queryCentral = new QueryCentral();
  private SortedMap<String, Dependency> dependencies = new TreeMap<>();
  private Path unknownsPath;

  public void execute() throws MojoExecutionException {
    try {
      unknownsPath = unknowns != null ? Files.createDirectories(Path.of(unknowns)) : null;
      sha1 = new Sha1();
      if (source == null) {
        source = "";
      }
      if (source.endsWith(".tar.gz") || source.endsWith(".tar")) {
        extractTar();
      } else {
        findJars();
      }

      writeBom();
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void findJars() throws IOException {
    try (Stream<Path> walk = Files.walk(Paths.get(source))) {
      walk.filter(f -> f.getFileName().toString().endsWith(".jar"))
          .forEach(this::findDependencyOrCopyUnknown);
    }
  }

  private void findDependencyOrCopyUnknown(Path jarPath) {
    if (!findGAVforJar(jarPath) && unknowns != null) {
      try {
        Files.copy(jarPath, createTmpFile(jarPath.toString()));
      } catch (IOException e) {
        getLog().error("Unable to copy " + jarPath + " to unknowns: " + e.getMessage(), e);
      }
    }
  }

  private boolean findGAVforJar(Path jarPath) {
    return findGAVfromPomProperties(jarPath) || findGAVfromSha1(jarPath);
  }

  private boolean findGAVfromPomProperties(Path jarPath) {
    try {
      try (JarFile jarFile = new JarFile(jarPath.toFile())) {
        return findPomProperties(jarFile);
      }
    } catch (IOException e) {
      getLog().error("Error during extraction of " + jarPath + " : " + e.getMessage());
      return false;
    }
  }

  private boolean findPomProperties(JarFile jarFile) throws IOException {
    for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
      JarEntry entry = entries.nextElement();
      String name = entry.getName();
      if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
        dependencyFromProperties(jarFile, entry);
        return true;
      }
    }
    return false;
  }

  private void dependencyFromProperties(JarFile jarFile, JarEntry entry) throws IOException {
    Properties properties = new Properties();
    properties.load(jarFile.getInputStream(entry));

    Dependency dependency = new Dependency();
    dependency.setGroupId(properties.getProperty("groupId"));
    dependency.setArtifactId(properties.getProperty("artifactId"));
    dependency.setVersion(properties.getProperty("version"));
    dependency.setClassifier(properties.getProperty("classifier"));

    addDependency(dependency);
  }

  private boolean findGAVfromSha1(Path jarPath) {
    try {
      Dependency dependency = queryCentral.getDependency(jarPath, sha1.getChecksum(jarPath));
      if (dependency != null) {
        addDependency(dependency);
        return true;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      getLog().error("Error while querying checksum of " + jarPath + " : " + e.getMessage(), e);
    }
    return false;
  }

  private void addDependency(Dependency dependency) {
    dependencies.put(dependency.getManagementKey() + ':' + dependency.getVersion(), dependency);
  }

  private ArchiveInputStream getArchiveInputStream() throws IOException {
    InputStream bi = new BufferedInputStream(Files.newInputStream(Path.of(source)));
    return new TarArchiveInputStream(
        source.endsWith(".gz") ? new GzipCompressorInputStream(bi) : bi);
  }

  private void extractTar() {
    try {
      try (ArchiveInputStream ais = getArchiveInputStream()) {
        extractTar(ais);
      }
    } catch (IOException e) {
      getLog().error("Error during processing " + source + " : " + e.getMessage(), e);
    }
  }

  private void extractTar(ArchiveInputStream archiveInputStream) throws IOException {
    for (; ; ) {
      ArchiveEntry entry = archiveInputStream.getNextEntry();
      if (entry == null) {
        break;
      }
      if (!entry.isDirectory() && entry.getSize() != 0L) {
        String entryName = entry.getName();
        if (entryName.endsWith(".jar")) {
          Path jarFile = createTmpFile(entryName);
          Files.copy(archiveInputStream, jarFile, StandardCopyOption.REPLACE_EXISTING);
          if (findGAVforJar(jarFile)) {
            Files.delete(jarFile);
          }
        }
      }
    }
  }

  private Path createTmpFile(String fullPath) throws IOException {
    String name = fullPath.substring(fullPath.lastIndexOf('/') + 1);
    return unknownsPath != null
        ? unknownsPath.resolve(name)
        : Files.createTempFile(name.substring(0, name.length() - 4), ".jar");
  }

  private void writeBom() throws IOException {
    Model model = new Model();
    model.setModelVersion("4.0.0");
    model.setGroupId(groupId);
    model.setArtifactId(artifactId);
    model.setVersion(version);
    dependencies.values().forEach(model::addDependency);

    try (BufferedWriter writer = Files.newBufferedWriter(Path.of(bom), StandardCharsets.UTF_8)) {
      new MavenXpp3Writer().write(writer, model);
    }
  }
}
