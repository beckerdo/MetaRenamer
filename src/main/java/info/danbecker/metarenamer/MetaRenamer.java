package info.danbecker.metarenamer;

import static info.danbecker.metarenamer.FileAttribute.*;
import static info.danbecker.metarenamer.MetaRenamer.FileAction.*;
import static java.nio.file.StandardCopyOption.*;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An app to rename files based on metadata in the file.
 * <p>
 * TODO
 * 1. No Exception friendlier messages on file not found.
 * 2. Test cases run individually. Exception thrown when run as a suite. Exception creates new files in original source resource directory which throws visited counts off.
 * 
 * @author <a href="mailto://dan@danbecker.info>Dan Becker</a>
 */
public class MetaRenamer {
	public static final SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	
	public static final String PATTERN_DELIMITER = "/";
	// xmpDM is XMP Dynamic Media schema. Other keys are Dublin core.
	// Common search fallback: xmpDM:album,title,dc:title?
	public static final String PATTERN_DEFAULT = "xmpDM:albumArtist/xmpDM:releaseYear - xmpDM:album/xmpDM:artist - xmpDM:releaseYear - xmpDM:album - xmpDM:trackNumber - title.extension";
	public static final String MISSING_TRACK_FILLER = "#";
	
	public static final String ADDITIONAL_DATA_KEY_FILENAME = "filename";
	public static final String ADDITIONAL_DATA_KEY_EXTENSION = "extension";
	
	public enum FileAction {
		CREATE,	DELETE,	UPDATE,
	};

	public enum Comparator {
		EQ, NE, LT, LE, GT, GE, TRUE, FALSE,
	};

	public static final String MEDIATYPE_KEY = MediaType.class.getSimpleName();	
	
	// options
	public static boolean actionMode = false;
	public static String msgPrefix = "   action: ";
	public static boolean verbose = false;
	public static boolean debug = false;
	public static String sourcePath = ".";
	public static String destPath = ".";	
	public static String fileGlob = "*";	
	public static boolean quiet = false;
	public static boolean moveTrueCopyFalse = false;
	public static int filesLimit = Integer.MAX_VALUE;
	public static Comparator dateTimeComparator = Comparator.FALSE;
	public static Date dateTimeCompare = null;
	
	public static String pattern; // pattern in string form with N path delimiters
	public static String [] patterns; // pattern broken up by path delimiters. [...,parent2,parent1,parent0,filename]
	public static String [] patternKeyNames; // list of all key names in pattern
	
	// statistics
    public static int filesVisited = 0;
    public static int filesRenamed = 0;
    public static int filesCreated = 0;
    public static int filesCollided = 0;
    public static int filesMissingMetadata = 0;
    public static int dirsVisited = 0;
    public static int dirsRenamed = 0;
    public static int dirsCreated = 0;
    public static int dirsCollided = 0;
    public static int dirsMissingMetadata = 0;
    public static List<String> missingMetadata = new LinkedList<String>(); 

    // Tika instance vars
    public static TikaConfig tikaConfig;
    public static DefaultParser defaultParser;
    public static DefaultHandler defaultHandler;
    public static ParseContext parseContext;
    public static PathMatcher matcher;
    
	public static Set<String> doNotParse = new TreeSet<String>();
	// A cache of paths, so that collision count does not increment.
	public static Set<String> checkedPaths = new TreeSet<String>();
	public static boolean cachePaths = true;
    
	/** Commmand line version of this application. */
	public static void main(String[] args) throws Exception {
	    System.out.println( "MetaRenamer 1.0 by Dan Becker" );
	    long startTime = System.currentTimeMillis();
	    
	    // Parse the command line arguments
		Options cliOptions = createOptions();
		CommandLineParser cliParser = new BasicParser();
	    CommandLine line = cliParser.parse( cliOptions, args );

	    // Gather command line arguments for execution
	    if( line.hasOption( "help" ) ) {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.setWidth( 100 );
	    	formatter.printHelp( "java -jar MetaRenamer.jar <options>", cliOptions );
	    	System.exit( 0 );
	    }
	    if( line.hasOption( "verbose" ) ) {
	    	verbose = true;	
	    	System.out.println( "   running in verbose mode");
	    }	    
	    if( line.hasOption( "action" ) ) {
	    	actionMode = true;	
	    	// this.fileName = line.getOptionValue( "fileName" );
	    	if ( verbose )
	    		System.out.println( "   running in test mode");
	    }	    
	    if( line.hasOption( "debug" ) ) {
	    	debug = true;	
	    	System.out.println( "   running in debug mode");
	    }	    
		msgPrefix = actionMode ? "   action: " : "   proposed: "; 
	    if( line.hasOption( "sourcePath" ) ) {
	    	sourcePath = line.getOptionValue( "sourcePath" );
	    	if ( verbose ) {
	    		System.out.println( "   source path=\"" + Paths.get( sourcePath ) + "\"" );
	    	}
	    }	    
	    checkPath( sourcePath, EnumSet.of( EXISTS, READABLE, DIRECTORY ), EnumSet.noneOf( FileAction.class ) );
	    if( line.hasOption( "destinationPath" ) ) {
	    	destPath  = line.getOptionValue( "destinationPath" );
	    	if ( verbose ) {
	    		System.out.println( "   destination path=\"" + Paths.get( destPath ) + "\"");
	    	}
	    } else {
	    	destPath = sourcePath;
	    }
	    checkPath( destPath, EnumSet.of( EXISTS, READABLE, WRITABLE, DIRECTORY ), EnumSet.of( CREATE )  );	    
	    if( line.hasOption( "time" ) ) {
	    	String option = line.getOptionValue( "time" );
	    	MetaRenamer.readDateTime(option);
	    	if ( verbose ) {
	    		System.out.println( "   time comparison=" + MetaRenamer.dateTimeComparator + ", datetime=" + DEFAULT_DATE_FORMAT.format( MetaRenamer.dateTimeCompare ) );
	    	}
	    }
	    if( line.hasOption( "glob" ) ) {
	    	fileGlob  = line.getOptionValue( "glob" );
	    	// Strange Eclipse Windows bug. Asterisks are expanded even when quoted. Will allow @ as * replacement.  
	    	if (( null != fileGlob ) && fileGlob.contains("@")) {
	    		System.out.println( "   replacing glob @ with *" );
	    		fileGlob = fileGlob.replace("@", "*");
	    	}
	    	if ( verbose ) {
	    		System.out.println( "   path glob pattern=\"" + fileGlob + "\"" );
	    	}
	    }
	    if( line.hasOption( "pattern" ) ) {
	    	pattern = line.getOptionValue( "pattern" );
	    	if ( verbose ) {
	    		System.out.println( "   pattern for renaming=\"" + pattern + "\"" );
	    	}
	    } else {
	    	pattern = PATTERN_DEFAULT;	    	
	    }
		patterns = MetaUtils.split( pattern, PATTERN_DELIMITER );  // Bugs in String [] keys = pattern.split( " -\\x2E" );  // x2E= point
		patternKeyNames = MetaUtils.split( pattern, " -./" );  // Bugs in String [] keys = pattern.split( " -\\x2E" );  // x2E= point
	    if( line.hasOption( "move" ) ) {
	    	moveTrueCopyFalse = true;
    		System.out.println( "   files will be moved/renamed" );
	    } else {
    		System.out.println( "   files will be copied" );
	    }
	    if( line.hasOption( "quiet" ) ) {
	    	quiet = true;
	    }	    
	    if( line.hasOption( "limit" ) ) {
	    	filesLimit = Integer.parseInt( line.getOptionValue( "limit" ) );
	    	if ( verbose ) {
	    		System.out.println( "   files limited to \"" + filesLimit + "\" file visits." );
	    	}
	    } else {
	    	pattern = PATTERN_DEFAULT;	    	
	    }
	    
	    // Init things
	    MetaRenamer.readDoNotParse( "src/main/resources/doNotParse.txt ", doNotParse);
	    
	    // Init Tika variables
	    tikaConfig = new TikaConfig();
	    defaultParser = (DefaultParser) tikaConfig.getParser();
	    defaultHandler = new DefaultHandler();
	    parseContext = new ParseContext(); 
	    
	    // Kick off tree walking process.
    	// See file system path matching at http://docs.oracle.com/javase/tutorial/essential/io/find.html
	    matcher = FileSystems.getDefault().getPathMatcher("glob:" + fileGlob );
	    try {
	    	Files.walkFileTree( Paths.get( sourcePath ), new MetaRenamerFileVisitor() );
			if (!quiet) {
				if (verbose) {
					if ( missingMetadata.size() > 0) {
						StringBuilder sb = new StringBuilder( "missing metadata keys (" + missingMetadata.size() + "/" + patternKeyNames.length + ")=" );
						int i = 0;
						for ( String metadataKey: missingMetadata) {
							if ( i > 0 ) sb.append( ",");
							sb.append( metadataKey );
							i++;
						}
						System.out.println( sb.toString() );
					} else {
						System.out.println( "no missing metadata" );
					}
				}
				System.out.println( "dirs visited/renamed/created/collided/missing meta " + dirsVisited + "/" + dirsRenamed + "/" + dirsCreated + "/" + dirsCollided + "/" + dirsMissingMetadata + "." );
				System.out.println( "files visited/renamed/created/collided/missing meta " + filesVisited + "/" + filesRenamed + "/" + filesCreated + "/" + filesCollided + "/" + filesMissingMetadata ); 
			}
	    } catch ( IOException e ) {
	    	System.err.println( "Exception=" + e);
	    }

		// conclude and end
       long elapsedTime = System.currentTimeMillis() - startTime;
       System.out.println( "elapsed time=" + format( elapsedTime ));       
	}
	
	/** This is the file visitor called for each file on the path. */
	public static class MetaRenamerFileVisitor extends SimpleFileVisitor<Path> {
	    @Override
	    public FileVisitResult visitFile(Path path, BasicFileAttributes attr) {
	        if (attr.isRegularFile()) {
				// System.out.println("   file=\"" + path.getFileName() + "\", isFile=" + attr.isRegularFile() + ", isDirectory=" + attr.isDirectory() );
				if (!attr.isDirectory()) {
					// Check for hidden. Ignore hidden
					try {
						if ( Files.isHidden(path) ) 
							return FileVisitResult.CONTINUE;
					} catch ( IOException e) {
					}
					filesVisited++;
					if (filesVisited >= filesLimit) {
						if (verbose) {
							System.out.println("   files visited limit reached \"" + filesLimit + "\".");
						}
						return FileVisitResult.TERMINATE;
					}
				}
					
				// Check lastModified
				if ( null != MetaRenamer.dateTimeCompare ) {
					File file = path.toFile();
					boolean useIt = MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, new Date( file.lastModified() ) ); 
    				// System.out.println("   file=\"" + file.getName() + "\"" + ", datetime \"" + DEFAULT_DATE_FORMAT.format( new Date( file.lastModified() )) + "\" " + 
    				//    MetaRenamer.dateTimeComparator.toString() + DEFAULT_DATE_FORMAT.format( MetaRenamer.dateTimeCompare ) + ", continue=" + useIt);
					if ( !useIt ) {
	    		        return FileVisitResult.SKIP_SUBTREE;
					}
				}
            	try {
					MetaRenamer.fileVisitor( path.toFile() );
				} catch (Exception e) {
					System.err.println( "   exception=" + e.getMessage());
					e.printStackTrace();
				}
	        } else if (attr.isSymbolicLink()) {
		        System.out.format( "   will not follow symbolic link: %s%n", path );
	        } else {
	            System.out.format( "   will not follow other file: %s%n", path );
	        }
	        return FileVisitResult.CONTINUE;
	    }
	    
	    @Override
	    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
	    	// Only run glob on direct children of sourcePath.
	    	// System.out.println( "   parent=\"" + dir.getParent().toString() + "\", equals=" + dir.getParent().toString().equals( sourcePath ) + ", compareTo=" + dir.getParent().compareTo( Paths.get( sourcePath )));
			if (attrs.isDirectory()) {
				// System.out.println("   file=\"" + dir.getFileName() + "\", isFile=" + attrs.isRegularFile() + ", isDirectory=" + attrs.isDirectory() );
				dirsVisited++;
			}
	    	
	    	if ( dir.getParent().toString().equals( sourcePath )) {
	    		Path name = dir.getFileName();
	    		if (name != null)  {
	    			if ( matcher.matches(name) ) {
	    				// if (( verbose ) && !( "*".equals( fileGlob ))) 
	    				//	System.out.println("   sourcePath child \"" + name + "\" matches glob." );
	    				// Check lastModified
	    				if ( null != MetaRenamer.dateTimeCompare ) {
	    					File file = dir.toFile();
	    					boolean useIt = MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, new Date( file.lastModified() ) ); 
		    				// System.out.println("   file=\"" + file.getName() + "\"" + ", datetime \"" + DEFAULT_DATE_FORMAT.format( new Date( file.lastModified() )) + "\" " + 
			    			// MetaRenamer.dateTimeComparator.toString() + DEFAULT_DATE_FORMAT.format( MetaRenamer.dateTimeCompare ) + ", continue=" + useIt);
	    					if ( useIt ) {
	    						return FileVisitResult.CONTINUE;
	    					} else {
			    		        return FileVisitResult.SKIP_SUBTREE;
	    					}
	    				}
	    				return FileVisitResult.CONTINUE;
	    			} else {
	    				// if (( verbose ) && !( "*".equals( fileGlob ))) 
	                	//   System.out.println("   sourcePath child \"" + name + "\" does not match glob." );
	    		        return FileVisitResult.SKIP_SUBTREE;							    				
	    			}
	    		}
	    	}
    		// System.out.println("   not child \"" + dir.getFileName() + "\" matches glob." );
        	return FileVisitResult.CONTINUE;		    		
	    }
	    
	    // @Override
	    // public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
	    // 	// System.out.println( "   done visiting \"" + dir.toString() + "\"" );
	    // 	return super.postVisitDirectory(dir, exc);
	    // }
		
	}
	
	// For example, to convert 10 minutes to milliseconds, use: TimeUnit.MILLISECONDS.convert(10L, TimeUnit.MINUTES)
    public static String format(long durationMillis) {
        if (durationMillis == 0) return "00:00:00.000";
        long hours = TimeUnit.HOURS.convert( durationMillis, TimeUnit.MILLISECONDS );
        long mins = TimeUnit.MINUTES.convert( durationMillis, TimeUnit.MILLISECONDS );
        long secs = TimeUnit.SECONDS.convert( durationMillis, TimeUnit.MILLISECONDS );
        long millis = TimeUnit.MILLISECONDS.convert( durationMillis % 1000L, TimeUnit.MILLISECONDS );
        return String.format("%02d:%02d:%02d.%03d", hours, mins, secs, millis);        
    }
    
	/** Command line options for this application. */
	public static Options createOptions() {
		// create the Options
		Options options = new Options();
		options.addOption( "h", "help", false, "print the command line options." );
		options.addOption( "a", "action", false, "perform actions, otherwise just list what would happen." );
		options.addOption( "b", "debug", false, "prints many more messages to the console than verbose." );
		options.addOption( "s", "sourcePath", true, "starting path for file search. The default is the local directory for the app." );
		options.addOption( "d", "destinationPath", true, "desination path for file search. The default is the source directory." );
		options.addOption( "g", "glob", true, "file name pattern matching glob (http://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob)." );
		options.addOption( "p", "pattern", true, "pattern for filename and parent directories." );
		options.addOption( "m", "move", false, "move renamed files rather than copy them." );
		options.addOption( "q", "quiet", false, "mute all logging including title and stats." );
		options.addOption( "l", "limit", true, "end after visiting <limit> file count." );
		options.addOption( "t", "time", true, "accepts if file compares to given datetime (for example \"GE2015-01-01\" or \"EQ2015-04-15\")." );
		options.addOption( "v", "verbose", false, "prints many more messages to the console than normal." );
		return options;
	}

	/** A callback method from the file/directory visitor. */
	public static void fileVisitor( File file ) throws Exception  {
		if ( !file.exists() || !file.canRead()) {
            System.out.println( "   file does not exist,readable" + file.getName() );
            return;
		}
		
		// Add name and type to metadata.
		Metadata metadata = new Metadata();	
	    metadata.add( Metadata.RESOURCE_NAME_KEY, file.toString() );   		    
	    MediaType mediaType = tikaConfig.getDetector().detect( TikaInputStream.get(file), metadata );
	    metadata.add( MEDIATYPE_KEY, mediaType.toString());
    	fileNameAction( metadata );    	
	}
		
	/** Recommends or performs action on media file name. */
	public static void fileNameAction( final Metadata metadata ) throws Exception {
		MediaType mediaType = MediaType.parse( metadata.get( MEDIATYPE_KEY ));
	    String mediaTypeString = mediaType.toString();
		
	    // Parse interesting media types.	
		if ( "audio/mp4".equals( mediaTypeString ) || "audio/mpeg".equals( mediaTypeString )) {
			// Add non-meta data items worth pursuing, e.g. filename, extension.
			String resourceName = metadata.get( Metadata.RESOURCE_NAME_KEY );
		    String fileName = resourceName.substring( resourceName.lastIndexOf( File.separator ) + 1 );
		    metadata.add( ADDITIONAL_DATA_KEY_FILENAME, fileName ); 
		    String extension = fileName.substring( fileName.lastIndexOf( "." ) + 1 );
		    metadata.add( ADDITIONAL_DATA_KEY_EXTENSION, extension );
	    	
			// Add metadata items based on type - year, artists, mapping of names.
		    Parser specificParser = defaultParser.getParsers().get( mediaType );
		    File file = new File( resourceName );
	    	specificParser.parse( TikaInputStream.get( file ), defaultHandler, metadata, parseContext );
			
		    MetaUtils.updateMetadata( metadata ); // add or clean up metadata		    
			if ( debug ) 
				MetaUtils.listAllMetadata( metadata );

		    // Recall that pattern contains full path/filename, 
			// patterns [] contains pattern broken up by path delimiters. [...,parent2,parent1,parent0,filename]
			// patternKeyNames  contains list of all key names in pattern
			String oldName =  metadata.get( Metadata.RESOURCE_NAME_KEY );
			Path oldPath = Paths.get( oldName );
	    
		    // Propose a new pattern.
		    String proposedName = new String( pattern );
		    int emptyCount = 0;
		    StringBuffer emptyKeys = new StringBuffer( "" );
			for ( String key: patternKeyNames ) {
				String value = metadata.get( key );
				// System.out.println( "   key=" + key + ", value=" + value);
				if (( null == value ) || (value.length() == 0)) {
					// System.err.println( "   missing key=" + key );
					value = key; // replace empty value with key name, e.g. "title"="title"
					// accounting
					if ( emptyKeys.length() > 0 ) 
						emptyKeys.append(",");
					emptyKeys.append( key );
					if ( !missingMetadata.contains( key ) )
						missingMetadata.add( key );
					emptyCount++;
				}
			    value = MetaUtils.escapeChars( value );
				proposedName = proposedName.replaceAll( key , value );
			}
			if ( emptyCount > 0 ) {
				// System.out.println( "   metadata missing " + emptyCount + "/" + patternKeyNames.length + " fields (" + emptyKeys.toString() + "), srcName=\"" + oldName + "\", proposedName=\"" + proposedName + "\"." );
				filesMissingMetadata++;
			}
			
		    Path proposedPath = Paths.get( destPath, proposedName );
		    if ( !oldPath.equals( proposedPath )) {
			    if ( Files.exists( proposedPath )) {
			    	if ( verbose ) {
			    		System.err.println( "   file \"" + proposedPath + "\" exists." );
			    	}
			    	filesCollided++;
			    	return;
			    }
			    
	    	    // Check parent directory
    			checkPath( proposedPath.getParent(), EnumSet.of( EXISTS, READABLE, WRITABLE, DIRECTORY ), EnumSet.of( CREATE ) );
		    	
		    	if ( verbose ) {
		    		if ( moveTrueCopyFalse ) 
		    			System.out.println( msgPrefix + "rename \"" + oldPath + "\" to\n      \"" + proposedPath + "\"." );
		    		else
		    			System.out.println( msgPrefix + "copy \"" + oldPath + "\" to\n      \"" + proposedPath + "\"." );
		    	}
		    	
		    	if ( actionMode ) {	    		
		    		// Move file
		    		if ( moveTrueCopyFalse ) {
		    			// This System.gc() call is required in JDK 7_60 and JDK 8_05
		    			// If not called the move throws java.nio.file.FileSystemException.
		    			System.gc();
		    			try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}
		    			Files.move( oldPath, proposedPath ); // throws java.nio.file.FileSystemException "The process cannot access the file because it is being used by another process."
		    			filesRenamed++;
		    		} else {
		    			Files.copy( oldPath, proposedPath, COPY_ATTRIBUTES ); // no REPLACE_EXISTING		    		
		    			filesCreated++;
		    		}
		    	}
		    }
		    
		// } else if ( "audio/x-wav".equals( mediaType.toString() )) {			
		} else {
			if ( MetaRenamer.doNotParseStartsWith(doNotParse, mediaTypeString )) {
			   if ( verbose ) {
				  System.out.println( "   no action: ignored media type=\"" + mediaType.toString() + "\", resource=\"" + metadata.get( Metadata.RESOURCE_NAME_KEY ) + "\"" );
			   }				
			}
		}	
	}
	
	/** One time check of a path for file attributes. Add them if requested. Examples: 
	 *     check if a file exists and is readable: checkPath( "path/blah.txt", EnumSet.of( EXISTS, READABLE, FILE ), EnumSet.noneOf( FileActions.class) ); 
	 *     check if a directory exists and is readable,writable: checkPath( "pathXYZ/foo", EnumSet.of( EXISTS, READABLE, WRITABLE, DIRECTORY ), EnumSet.of( ADD ) );
	 *  Creating and deleting a directory is recursive and will work even if there are multiple levels with contents. 
	 * @throws IOException 
	 */
	public static boolean checkPath( Path path, EnumSet<FileAttribute> attrs, EnumSet<FileAction> actions ) throws IOException {
		boolean result = true;
					
		// Use cache unless requestd || actions || empty cache.
		if ( !cachePaths || (actions.size() > 0) || !checkedPaths.contains( path.toString() ) ) {
			File currentFile = path.toFile();
			if ( MetaRenamer.debug )
			    System.out.println( "   checkPath currentFile=\"" + currentFile.getPath() + "\", absPath=\"" + currentFile.getAbsolutePath() + "\", attrs=" + MetaUtils.getAttributes( currentFile ) );
			if (attrs.contains( EXISTS )) {
				result &= currentFile.exists();
			}
			
			
			if ( actions.contains( CREATE ) && !currentFile.exists() ) {
		    	 if (attrs.contains( FileAttribute.FILE )) {
  		            if ( verbose ) 
				       System.out.println( msgPrefix + "create file=" + currentFile.toString() );
		    		if ( actionMode ) {
			    		// currentFile.createNewFile();
		    		   Files.createFile( path );
		    		   filesCreated++;
		    		}
		    	 }
		    	 if( attrs.contains( FileAttribute.DIRECTORY )) {
					if (!checkedPaths.contains(path.toString())) {
						if (verbose)
							System.out.println(msgPrefix + "create directory=" + currentFile.toString());
						if (actionMode) {
							// currentFile.mkdir();
							Files.createDirectories(path); // will create recursively
							checkedPaths.add(path.toString());
							dirsCreated++;
						}
					}
		    	 }
			     result = currentFile.exists(); // reset to true if created
			}
			if ( actions.contains( UPDATE ) ) {
				if ( verbose )
			    	System.out.println( msgPrefix + "update file=" + currentFile.toString() );
		        if (actionMode) {
		        	// currentFile.setLastModified( (new Date()).getTime() );        	  
		        	Files.setLastModifiedTime( path, FileTime.fromMillis(System.currentTimeMillis()) );
		        }
			}
			if (attrs.contains( READABLE )) result &= currentFile.canRead();
			if (attrs.contains( WRITABLE )) result &= currentFile.canWrite();
			if (attrs.contains( EXECUTABLE )) result &= currentFile.canExecute();
			if (attrs.contains( FileAttribute.DIRECTORY )) result &= currentFile.isDirectory();
			if (attrs.contains( FileAttribute.FILE )) result &= currentFile.isFile();
			if ( result ) {
				// Files will throw IOException if non-existent.
				if (attrs.contains( HIDDEN )) result &= Files.isHidden( path );
				if (attrs.contains( LINK )) result &= Files.isSymbolicLink( path );
			}
			if ( actions.contains( DELETE ) ) { // only delete bottom most one in path
				if ( verbose )
			    	System.out.println( msgPrefix + "delete file=" + currentFile.toString() );
		        if (actionMode) {
			    	 if (attrs.contains( FileAttribute.FILE )) 
		        	    currentFile.delete();
		        	// Files.delete( currentPath );
		        	// Delete content
			    	 if (attrs.contains( FileAttribute.DIRECTORY )) {
			    		 MetaUtils.deleteFolder( currentFile );
		        	}
		        }
			} else {
				if (cachePaths)
					checkedPaths.add( path.toString() ); // do not add if just deleted.
			}
		}
		return result;
	}
	
	/** One time check of a path for file attributes. Add them if requested. Examples: 
	 *     check if a file exists and is readable: checkPath( "path/blah.txt", EnumSet.of( EXISTS, READABLE, FILE ), EnumSet.noneOf( FileActions.class) ); 
	 *     check if a directory exists and is readable,writable: checkPath( "pathXYZ/foo", EnumSet.of( EXISTS, READABLE, WRITABLE, DIRECTORY ), EnumSet.of( ADD ) );
	 *  Creating and deleting a directory is recursive and will work even if there are multiple levels with contents. 
	 * @throws IOException 
	 */
	public static boolean checkPath( String pattern, EnumSet<FileAttribute> attrs, EnumSet<FileAction> actions ) throws IOException {
		if ( (null == pattern) || ( pattern.length() ==0 ))
			return false;
					
		return checkPath( Paths.get( pattern ), attrs, actions );
	}
	
	/** Reads strings from the given file path. Places in the given set. */
	public static void readDoNotParse( String fileName, Set<String> doNotParse ) 
			throws IOException {
		File file = new File( fileName );
		if ( !file.exists() || !file.isFile() || !file.canRead() )
			return;
		
		// try with resources. Requires JDK 1.7
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		       if ( !line.startsWith("//") ) {
			       line = line.trim();
			       if ( line.length() > 0 )
			    	   doNotParse.add( line );
		       }
		    }
		}
	}

	/** Tests if the testString starts with any String in the given set. */
	public static boolean doNotParseStartsWith( final Set<String> doNotParse, String testString ) {
		for( String doNotParseString : doNotParse ) {
			if ( testString.startsWith( doNotParseString ))
				return true;
		}
		return false;
	}
	
	/** Reads datetime comparator and datetime from the given option. */
	public static void readDateTime( String option  ) {
		if ( null == option || option.length() < 1 ) return;
		for ( Comparator comp : EnumSet.allOf( MetaRenamer.Comparator.class) ) {
			int loc = -1;
			loc = option.indexOf( comp.toString() );
			if ( 0 == loc ) {
				dateTimeComparator = comp;
				// Strip comparator.
				option = option.replace( comp.name().toString(), "" );
				// Convert slashes to dashes
				option = option.replaceAll( "[/]+", "-");
				// option = option.replaceAll( "[\\]+", "-");

				// Parse date
				DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");
				// Try with long format first, short format second.
				try {
					LocalDateTime dateTime = f.parseLocalDateTime( option );
					MetaRenamer.dateTimeCompare = dateTime.toDate();
				} catch ( IllegalArgumentException e ) {
					f = DateTimeFormat.forPattern("yyyy-MM-dd");
					LocalDateTime dateTime = f.parseLocalDateTime( option );
					MetaRenamer.dateTimeCompare = dateTime.toDate();				
				}
			}
		}
	}

	/** Tests if the testString starts with any String in the given set. */
	public static boolean testDateTime( MetaRenamer.Comparator comparator, Date compareDateTime, Date givenDateTime ) {
		if ( null == comparator ) return false;
		if ( MetaRenamer.Comparator.FALSE.equals( comparator ) ) return false;
		if ( MetaRenamer.Comparator.TRUE.equals( comparator ) ) return true;
		if ( null == compareDateTime || null == givenDateTime ) return false;
		
		if ( MetaRenamer.Comparator.LT.equals( comparator ) ) return givenDateTime.getTime() < compareDateTime.getTime(); 
		if ( MetaRenamer.Comparator.GT.equals( comparator ) ) return givenDateTime.getTime() > compareDateTime.getTime(); 
		if ( MetaRenamer.Comparator.LE.equals( comparator ) ) return givenDateTime.getTime() <= compareDateTime.getTime(); 
		if ( MetaRenamer.Comparator.GE.equals( comparator ) ) return givenDateTime.getTime() >= compareDateTime.getTime(); 
		if ( MetaRenamer.Comparator.EQ.equals( comparator ) ) return givenDateTime.getTime() == compareDateTime.getTime(); 
		if ( MetaRenamer.Comparator.NE.equals( comparator ) ) return givenDateTime.getTime() != compareDateTime.getTime(); 
		
		return false;
	}
}