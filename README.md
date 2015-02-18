MetaRenamer
===========

Rename, move, and update files from metadata.

Primarily used for putting music metadata (artists, year, album name) into the file name or directory structure.

===========

Clone the repository and build it with "mvn package". 
   - If you have trouble getting artifacts from your local repository,
add the Maven Central repository to your Maven settings.xml (http://central.sonatype.org/pages/consumers.html#apache-maven).
   - The project must be compiled with Java 7 environment.
   - If the build fails because of tests, try the command line option "-DskipTests".

Run as a Java class with provided/built JAR file:
<code>
<pre>
   java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer <options> 
</pre>
</code>

For help use option "-h":
<code>
<pre>
   java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -h 
 -d,--destinationPath <arg>   desination path for file search. The default
                              is the source directory.
 -g,--glob <arg>              file name pattern matching glob
                              (http://docs.oracle.com/javase/tutorial/esse
                              ntial/io/fileOps.html#glob).
 -h,--help                    print the command line options.
 -l,--limit <arg>             end after visiting <limit> file count.
 -m,--move                    move renamed files rather than copy them.
 -p,--pattern <arg>           pattern for filename and parent directories.
 -q,--quiet                   mute all logging including title and stats.
 -s,--sourcePath <arg>        starting path for file search. The default
                              is the local directory for the app.
 -t,--test                    do not perform actions, just list what would
                              happen.
 -v,--verbose                 prints many more messages to the console
                              than normal.
</pre>
</code>

The most common options are source directory (option "-s") and destination directory (option "-d").
For test mode use option "-t". Test mode states actions without actually doing anything:
<code>
<pre>
   java -jar target/MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -t -v -s "e:/audio/AmazonMP3" -d "."
   java -jar target/MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -t -v -s "src/test/resources/info/danbecker/metarenamer/rhythmpatterns"
</pre>
</code>

==========
TODOs
   * Not yet tested on Mac and Linux environments. Make unit test pass on these OSs.
   * View more to dos in MetaRenamer.
==========
Authors
   * Dan Becker, dan@danbecker.info
