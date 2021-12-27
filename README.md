# bom-maven-plugin

Create a BOM (bill of materials) from a set of jars or tar file.  The bom is formatted as a [pom](https://maven.apache.org/pom.html).

## Goals
There is one goal: [extract](https://chonton.github.io/bom-maven-plugin/1.1.0/extract-mojo.html) creates
a bom from the jars found in a directory or tar.

The specified source is recursively searched for files ending with '.jar'.  If the specified source
ends with '.tar' or '.tar.gz', it is assumed to be a tar file.  All files inside the tar ending with
'.jar' are extracted to the temp directory.

Once the set of jars is determined, each is examined to determine its GAV (groupId,artifactId,version).

First, the jar is scanned for the existence of a 'META-INF/maven/**/pom.properties' file.  If the
file exists, it contains the GAV.

If pom.properties is not present, the sha1 hash of the jar is computed, and maven central is queried
to determine the GAV of the jar.  If multiple jars with the same hash are returned from maven
central, the best match based on the name of the jar will be used.

Using the GAV of the jars, a pom.xml is generated with the jars as dependencies.

Mojo details at [plugin info](https://chonton.github.io/bom-maven-plugin/1.0.0/plugin-info.html)

## Parameters
Every parameter can be set with a maven property **bom.**_<parameter_name\>_.  e.g. source parameter
can be set from command line: -Dbom.source=image.tar

| Parameter  | Default        | Description                                          |
|------------|----------------|------------------------------------------------------|
| groupId    | extracted      | The groupId for the bom                              |
| artifactId | bom            | The artifactId for the bom                           |
| version    | 1.0.0-SNAPSHOT | The version for the bom                              |
| bom        | pom.xml        | The output file                                      |
| source     |                | The source to examine, Defaults to current directory |
| unknowns   |                | The directory to receive a copy of unknown jars      |

## Requirements
- Maven 3.5 or later
- Java 11 or later

## Typical Uses

```shell
# extract bill of materials from the jars in the current directory
mvn org.honton.chas:bom-maven-plugin:1.1.0:extract -D bom.groupId=myGroup

# extract bill of materials from image.tar
mvn org.honton.chas:bom-maven-plugin:1.1.0:extract -D bom.source=image.tar
```
