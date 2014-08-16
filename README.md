MetaRenamer
===========

Rename, move, and update files from metadata.

Primarily used for putting music metadata (artists, year, album name) into the file name or directory structure.

===

Run as a Java class with provided/built JAR file:
java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer <options> 

For help use option "-h":
java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -h 

Source directory is option "-s". Destination directory is option "-d".
For test mode use option "-t". Test mode states actions without actually doing anything:
java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -t -v -s "e:\audio\AmazonMP3" -d "."
java -jar target\MetaRenamer-1.0.0-SNAPSHOT.jar info.danbecker.metarenamer.MetaRenamer -t -v -s "src/test/resources/info/danbecker/metarenamer/rhythmpatterns" 
