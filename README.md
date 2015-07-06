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
<p>
Run as a Java class with provided/built JAR file:
<code>
<pre>
   java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer <options> 
</pre>
</code>
<p>
For help use option "-h":
<code>
<pre>
   java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -h 
 -a,--action                  perform actions. Without this, the app reports whathat would happen.
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
 -t.--time <arg>			  limit actions to given datetime stamps. Form is <comparator><datetime>
 						      for example LT2015-07-04, GE2015-01-01, or EQ2015-04-01
 -v,--verbose                 prints many more messages to the console than normal.
</pre>
</code>
<p>
The most common options are source directory (option "-s") and destination directory (option "-d").
Verbose mode "-v" is used to print detailed steps.
Action mode "-a" is used to perform action, otherwise the app tells you what it would do.
<code>
<pre>
   java -jar target/MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -v -s "e:/audio/CDs" -d "."
   java -jar target/MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -v -s "src/test/resources/info/danbecker/metarenamer/rhythmpatterns"
   java -jar target/MetaRenamer-1.0.0-SNAPSHOT.jar -v -s "e:/audio/CDs" -d "." -t "GE2015-07-01"
</pre>
</code>
==========
FAQ
   *  Q: I see multiple repeated directories in my destination path. For example the artist name is repeated twice in the path.
   	  A: Be aware that the destination path and the naming pattern are appended to form the complete destination path. So
	  	 if the destination path has an artist name and the naming pattern has an artist name you will see two.
		 For example destinationPath="/Music/ACDC" and naming pattern="artist/year-album/title.ext", the complete file path
		 of a song might be might be "/Music/ACDC/ACDC/1980-Back in Black/Hells Bells.mp3"


==========
TODOs
   * Not yet tested on Mac and Linux environments. Make unit test pass on these OSs.
   * View more to dos in MetaRenamer.
==========
Authors
   * Dan Becker, dan@danbecker.info
