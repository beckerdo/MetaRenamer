package info.danbecker.metarenamer;

import static info.danbecker.metarenamer.FileAttribute.*;
import static info.danbecker.metarenamer.MetaRenamer.FileAction.*;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardCopyOption.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

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
import org.xml.sax.helpers.DefaultHandler;

/**
 * An app to rename files based on metadata in the file.
 * @author <a href="mailto://dan@danbecker.info>Dan Becker</a>
 */
public class MetaRenamer {
	public static final String PATTERN_DELIMITER = "/";
	public static final String PATTERN_DEFAULT = "xmpDM:albumArtist/xmpDM:releaseYear - xmpDM:album/xmpDM:artist - xmpDM:releaseYear - xmpDM:album - xmpDM:trackNumber - title.extension";

	public enum FileAction {
		CREATE,
		DELETE,
		UPDATE,
	};

	public static final String MEDIATYPE_KEY = MediaType.class.getSimpleName();	
	
	// options
	public static boolean testMode = false;
	public static String msgPrefix = "   action: ";
	public static boolean verbose = false;
	public static String sourcePath = ".";
	public static String destPath = ".";	
	public static boolean quiet = false;
	public static boolean moveTrueCopyFalse = false;
	
	public static String pattern; // pattern in string form with N path delimiters
	public static String [] patterns; // pattern broken up by path delimiters. [...,parent2,parent1,parent0,filename]
	public static String [] patternKeyNames; // list of all key names in pattern
	
	// statistics
    public static int filesVisited = 0;
    public static int filesRenamed = 0;
    public static int filesCollided = 0;
    public static int filesCreated = 0;
    public static int dirsVisited = 0;
    public static int dirsRenamed = 0;
    public static int dirsCollided = 0;
    public static int dirsCreated = 0;

    // instance vars
    public static TikaConfig tikaConfig;
	public static Set<String> doNotParse = new TreeSet<String>( Arrays.asList("application/pdf") );
	// A cache of paths, so that collision count does not increment.
	public static Set<String> checkedPaths = new TreeSet<String>();
	public static boolean cachePaths = true;
    
	/** Commmand line version of this application. */
	public static void main(String[] args) throws Exception {
	    System.out.println( "MetaRenamer 1.0 by Dan Becker" );
	    
	    // Parse the command line arguments
		Options cliOptions = createOptions();
		CommandLineParser cliParser = new BasicParser();
	    CommandLine line = cliParser.parse( cliOptions, args );

	    // Gather command line arguments for execution
	    if( line.hasOption( "help" ) ) {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp( "java -jar MetaRenamer.jar <options> info.danbecker.metarename.MetaRenamer", cliOptions );
	    	System.exit( 0 );
	    }
	    if( line.hasOption( "verbose" ) ) {
	    	verbose = true;	
	    	System.out.println( "   running in verbose mode");
	    }	    
	    if( line.hasOption( "test" ) ) {
	    	testMode = true;	
	    	// this.fileName = line.getOptionValue( "fileName" );
	    	if ( verbose )
	    		System.out.println( "   running in test mode");
	    }	    
		msgPrefix = testMode ? "   proposed action: " : "   action: "; 
	    if( line.hasOption( "sourcePath" ) ) {
	    	sourcePath = line.getOptionValue( "sourcePath" );
	    	if ( verbose ) {
	    		System.out.println( "   source path=" + Paths.get( sourcePath ));
	    	}
	    }	    
	    checkPath( sourcePath, EnumSet.of( EXISTS, READABLE, DIRECTORY ), EnumSet.noneOf( FileAction.class ) );
	    if( line.hasOption( "destinationPath" ) ) {
	    	destPath  = line.getOptionValue( "destinationPath" );
	    	if ( verbose ) {
	    		System.out.println( "   destination path=" + Paths.get( destPath ));
	    	}
	    } else {
	    	destPath = sourcePath;
	    }
	    checkPath( destPath, EnumSet.of( EXISTS, READABLE, WRITABLE, DIRECTORY ), EnumSet.of( CREATE )  );
	    
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
	    }	    
	    if( line.hasOption( "quiet" ) ) {
	    	quiet = true;
	    }	    
	    // Init instance variables
	    tikaConfig = new TikaConfig();
	    
	    // Kick off tree walking process.
		Files.walkFileTree( Paths.get( sourcePath, "" ), new SimpleFileVisitor<Path>() {
		    @Override
		    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws FileNotFoundException, IOException {
		        if (attr.isSymbolicLink()) {
		            System.out.format("Symbolic link: %s%n", file);
		        } else if (attr.isRegularFile()) {
		            // System.out.format("Regular file: %s%n", file);
	            	// Call back.
	            	MetaRenamer.fileVisitor( file.toString() );
		        } else {
		            System.out.format("Other: %s%n", file);
		        }
		        // System.out.println("(" + attr.size() + "bytes)");
		        return CONTINUE;
		    }			
		});

		// conclude and end
		if (!quiet)
	       System.out.println( "files visited/renamed/collided/created " + filesVisited + "/" + filesRenamed + "/" + filesCreated + "/" + filesCreated + 
	    		   ", dirs visited/renamed/collided/created " + dirsVisited + "/" + dirsRenamed + "/" + dirsCollided + "/" + dirsCreated + "." );
	}

	/** Command line options for this application. */
	public static Options createOptions() {
		// create the Options
		Options options = new Options();
		options.addOption( "h", "help", false, "print the command line options." );
		options.addOption( "t", "test", false, "do not perform actions, just list what would happen." );
		options.addOption( "v", "verbose", false, "prints many more messages to the console than normal." );
		options.addOption( "s", "sourcePath", true, "starting path for file search. The default is the local directory for the app." );
		options.addOption( "d", "destinationPath", true, "desination path for file search. The default is the source directory." );
		options.addOption( "p", "pattern", true, "pattern for filename and parent directories." );
		options.addOption( "m", "move", false, "move renamed files rather than copy them." );
		options.addOption( "q", "quiet", false, "mute all logging including title and stats." );
		return options;
	}

	/** A callback method from the file/directory visitor. */
	public static void fileVisitor( String fileName ) throws FileNotFoundException, IOException  {
		filesVisited++;
		File file = new File(fileName);
		
		// Synthesize, update metadata
		Metadata metadata = new Metadata();	
	    metadata.add( Metadata.RESOURCE_NAME_KEY, file.toString() );   		    
	    MediaType mediaType = tikaConfig.getDetector().detect( TikaInputStream.get(file), metadata );
	    String mediaTypeString = mediaType.toString();
	    metadata.add( MEDIATYPE_KEY, mediaTypeString);
	    // Parse interesting media types.
	    if ( !(mediaTypeString.startsWith( "application" ) || 
	    		mediaTypeString.startsWith( "text" ) || 
	    		mediaTypeString.startsWith( "images" ) || 
	    		doNotParse.contains( mediaType.toString() ))) { // PDF throws lof4j warnings
		    // if ( verbose )
		    //    System.out.println( msgPrefix + "visit \"" + file + "\", type=\"" + mediaTypeString + "\"" );
		    
		    DefaultParser defaultParser = (DefaultParser) tikaConfig.getParser();
		    Parser specificParser = defaultParser.getParsers().get( mediaType );
			try {
		    	specificParser.parse( org.apache.tika.io.TikaInputStream.get( file ), new DefaultHandler(), metadata, new ParseContext() );
		    	fileNameAction( metadata );
			} catch ( Exception e ) {
				System.out.println( e.toString() + ", file=\"" + file + "\", type=\"" + mediaType.toString() + "\"" );
				e.printStackTrace();						
			}
		} else {
		    // if ( verbose )
		    //    System.out.println( "   no action: " + "\"" + file + "\", type=\"" + mediaTypeString + "\"" );		    
		}
	    
	}
		
	/** Recommends or performs action on media file name. */
	public static void fileNameAction( final Metadata metaData ) throws IOException {
		MediaType mediaType = MediaType.parse( metaData.get( MEDIATYPE_KEY ));
		if ( "audio/mp4".equals( mediaType.toString() ) || "audio/mpeg".equals( mediaType.toString() )) {
		    MetaUtils.updateMetadata( metaData ); // add or clean up metadata
		    
			// if ( verbose ) {
		    //    listAllMetaData( metaData );

		    // Recall that pattern contains full path/filename, 
			// patterns [] contains pattern broken up by path delimiters. [...,parent2,parent1,parent0,filename]
			// patternKeyNames  contains list of all key names in pattern
			String oldName =  metaData.get( Metadata.RESOURCE_NAME_KEY );
			Path oldPath = Paths.get( oldName );

			// Check that the file exists and is readable. 
		    boolean oldExists = checkPath( oldPath, EnumSet.of( EXISTS, READABLE, FILE ), EnumSet.noneOf( FileAction.class ) );
		    if ( !oldExists  ) {
		    	if ( verbose )
		    		System.out.println( "   file \"" + oldPath + "\" does not exist or is not readable." );
		    	return;
		    }
		    
		    // Propose a new pattern.
		    String proposedName = new String( pattern ); 
			for ( String key: patternKeyNames ) {
				String value = metaData.get( key );
				// System.out.println( "   key=" + key + ", value=" + value);
				if ( null == value ) value = ""; 
				proposedName = proposedName.replaceAll( key , value );
			}
			
		    Path proposedPath = Paths.get( destPath, proposedName );		    
		    if ( !oldPath.equals(proposedPath )) {
			    if ( Files.exists( proposedPath )) {
			    	if ( verbose ) {
			    		System.out.println( "   file \"" + proposedPath + "\" exists." );
	    				long lastMod = oldPath.toFile().lastModified();
				        System.out.println ( "   old file size=" + Files.size( oldPath ) +  ", lastMod=" + (new Date( lastMod )).toString());			   
	    				lastMod = proposedPath.toFile().lastModified();
				        System.out.println ( "   proposed size=" + Files.size( proposedPath ) +  ", lastMod=" + (new Date( lastMod )).toString());			   
			    	}
			    	filesCollided++;
			    	return;
			    }
			    
		    	if ( verbose ) {
		    		if ( moveTrueCopyFalse ) 
		    			System.out.println( msgPrefix + "rename \"" + oldPath + "\" to\n      \"" + proposedPath + "\"." );
		    		else
		    			System.out.println( msgPrefix + "copy \"" + oldPath + "\" to\n      \"" + proposedPath + "\"." );
		    	}
		    	
	    	    // Check parent directory
    			checkPath( proposedPath.getParent(), EnumSet.of( EXISTS, READABLE, WRITABLE, DIRECTORY ), EnumSet.of( CREATE ) );
		    	
		    	if ( !testMode ) {	    		
		    		// Move file
		    		if ( moveTrueCopyFalse ) {
		    			Files.move( oldPath, proposedPath, ATOMIC_MOVE ); // throws The process cannot access the file because it is being used by another process.
		    			// oldPath.toFile().renameTo(  proposedPath.toFile() ); // renames a file. DOes not actually move what is underneath
		    			filesRenamed++;
		    		} else {
		    			Files.copy( oldPath, proposedPath, COPY_ATTRIBUTES ); // no REPLACE_EXISTING		    		
		    			filesCreated++;
		    		}
		    	}
		    }
		    
		// } else if ( "audio/x-wav".equals( mediaType.toString() )) {			
		} else {
		   if ( verbose ) {
		      System.out.println( "   no action: type \"" + mediaType.toString() + "\"" );
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
			// if ( MetaRenamer.verbose ) {
			//    System.out.println( "   checkPath currentFile=\"" + currentFile.getPath() + "\", absPath=\"" + currentFile.getAbsolutePath() + "\", attrs=" + MetaUtils.getAttributes(pattern) );
			if (attrs.contains( EXISTS )) {
				result &= currentFile.exists();
		    	if( attrs.contains( FileAttribute.DIRECTORY )) dirsVisited++;
			}
			if ( actions.contains( CREATE ) && !currentFile.exists() ) {
		    	 if (attrs.contains( FileAttribute.FILE )) {
  		            if ( verbose ) 
				       System.out.println( msgPrefix + "create file=" + currentFile.toString() );
		    		if ( !testMode ) {
			    		// currentFile.createNewFile();
		    		   Files.createFile( path );
		    		   filesCreated++;
		    		}
		    	 }
		    	 if( attrs.contains( FileAttribute.DIRECTORY )) {
   		            if ( verbose ) 
	 				       System.out.println( msgPrefix + "create directory=" + currentFile.toString() );
		    		if ( !testMode ) {
		    		   // currentFile.mkdir();
		    		   Files.createDirectories( path ); // will create recursively
		    		   dirsCreated++;
		    		}
		    	 }
			     result = currentFile.exists(); // reset to true if created
			}
			if ( actions.contains( UPDATE ) ) {
				if ( verbose )
			    	System.out.println( msgPrefix + "update file=" + currentFile.toString() );
		        if (!testMode) {
		        	currentFile.setLastModified( (new Date()).getTime() );        	  
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
		        if (!testMode) {
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
}