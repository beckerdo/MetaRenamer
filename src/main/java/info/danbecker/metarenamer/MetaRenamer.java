package info.danbecker.metarenamer;

import static info.danbecker.metarenamer.FileAttribute.*;
import static info.danbecker.metarenamer.MetaRenamer.PatternPortion.*;
import static info.danbecker.metarenamer.MetaRenamer.FileAction.*;
import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
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
	/** Used for checking portion of a pattern.
	 * 	Use EnumSet to set one or more parts, e.g. EnumSet.of( PatternPortion.ROOT, PatternPortion.FILE )	
	 * */
	public enum PatternPortion {
		ALL,  		// 0..N of pattern
		DIRECTORY, 	// 0..N-1 of pattern
		FILE,		// N of pattern
		PARENT,		// N-1 of pattern
		ROOT		// 0 of pattern
	};
	public enum FileAction {
		CREATE,
		DELETE,
		UPDATE,
	};

	public static final String MEDIATYPE_KEY = MediaType.class.getSimpleName();	
	
	// options
	public static boolean testMode = false;
	public static String msgPrefix = "   action";
	public static boolean verbose = false;
	public static String sourcePath = ".";
	public static String destPath = ".";	
	public static boolean mute = false;
	
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
    
// Test files of various types.
//	String [] fileNames = {
//		"E:\\audio\\DansMusic\\Al di Meola\\The Essence of Al Di Meola\\05 Race With the Devil On Spanish Hi.m4a",
//		"E:\\audio\\AmazonMP3\\Paul Westerberg\\PW & The Ghost Gloves Cat Wing Joy Boys\\01 - Ghost On The Canvas.mp3",
//		"E:\\audio\\DansMusic\\Amy Winehouse\\2006 - Back to Black\\Amy Winehouse - 2006 - Back to Black - 01 - Rehab.wav",
//		"E:\\audio\\DansMusic\\Amy Winehouse\\2006 - Back to Black\\Amy Winehouse - 2006 - Back to Black - 01 - Rehab.mid",
//		"E:\\audio\\DansMusic\\Adele\\2011 - 21\\Digital Booklet - 21.pdf",
//		"E:\\audio\\DansMusic\\Various\\KingOfTheBluesTracks\\2011\\description.txt",
//      "E:\\audio\\DansMusic\\Various - DIY\\1993 - DIY We're Desperate The L.A. Scene (1976-79)\\", // compilation/albumArtist
//	    "E:\\audio\\DansMusic\\Various - KGSR\\2013 - KGSR Broadcasts Vol. 21 - Disc 2\\", // multi disc compilation    
//		};

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
	    	formatter.printHelp( "java -jar metarenamer.jar <options> info.danbecker.metarename.MetaRenamer", cliOptions );
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
	    		System.out.println( "   source path for files=" + Paths.get( sourcePath ));
	    	}
	    }	    
	    checkPath( sourcePath, EnumSet.of( PatternPortion.ALL ), EnumSet.of( EXISTS, READABLE, FileAttribute.DIRECTORY ), EnumSet.noneOf( FileAction.class ) );
	    if( line.hasOption( "destinationPath" ) ) {
	    	destPath  = line.getOptionValue( "destinationPath" );
	    	if ( verbose ) {
	    		System.out.println( "   destination path for files=" + Paths.get( destPath ));
	    	}
	    } else {
	    	destPath = sourcePath;
	    }
	    checkPath( destPath, EnumSet.of( PatternPortion.ALL ), EnumSet.of( EXISTS, READABLE, WRITABLE,  FileAttribute.DIRECTORY ), EnumSet.of( CREATE )  );
	    
	    if( line.hasOption( "pattern" ) ) {
	    	pattern = line.getOptionValue( "pattern" );
	    	if ( verbose ) {
	    		System.out.println( "   pattern for renaming=\"" + pattern + "\"" );
	    	}
	    } else {
	    	pattern = PATTERN_DEFAULT;	    	
	    }
		patterns = split( pattern, PATTERN_DELIMITER );  // Bugs in String [] keys = pattern.split( " -\\x2E" );  // x2E= point
		patternKeyNames = split( pattern, " -." );  // Bugs in String [] keys = pattern.split( " -\\x2E" );  // x2E= point
	    if( line.hasOption( "mute" ) ) {
	    	mute = true;
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
		if (!mute)
	       System.out.println( "   files visited/renamed/collided" + filesVisited + "/" + filesRenamed + "/" + filesCollided + 
	    		   ", dirs visted/renamed/collided/created" + dirsVisited + "/" + dirsRenamed + "/" + dirsCollided + "/" + dirsCreated + "." );
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
		options.addOption( "m", "mute", false, "mute all logging including title and stats." );
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
	    metadata.add( MEDIATYPE_KEY, mediaType.toString());
	    if ( verbose )
		       System.out.println( "\"" + file + "\", type=\"" + mediaType.toString() + "\"" );
	    
	    // Parse interesting media types.
	    if ( !doNotParse.contains( mediaType.toString() )) { // PDF throws lof4j warnings
		    DefaultParser defaultParser = (DefaultParser) tikaConfig.getParser();
		    Parser specificParser = defaultParser.getParsers().get( mediaType );
			try {
		    	specificParser.parse( org.apache.tika.io.TikaInputStream.get( file ), new DefaultHandler(), metadata, new ParseContext() );
			    updateMetadata( metadata ); // add or clean up metadata
			    
		    	dirNameAction( metadata );
		    	fileNameAction( metadata );
			} catch ( Exception e ) {
				System.out.println( e.toString() + ", file=\"" + file + "\", type=\"" + mediaType.toString() + "\"" );
				e.printStackTrace();						
			}
		}
	}
		
	/** Allows the synthesis or clean-up of metadata. */
	public static void updateMetadata( Metadata metadata ) {
		// synthesis
		String resourceName = metadata.get( Metadata.RESOURCE_NAME_KEY );
	    String fileName = resourceName.substring( resourceName.lastIndexOf( File.separator ) + 1 );
	    metadata.add( "fileName", fileName ); 
	    String extension = fileName.substring( fileName.lastIndexOf( "." ) + 1 );
	    metadata.add( "extension", extension );
    	addYear( metadata );

	    // cleanup
    	cleanTrack( metadata );

	    // Can add parent names from pattern broken up by path delimiters. [...,parent2,parent1,parent0,filename]
	}
	
	/** Checks portions of delimited path for file attributes. Add them if requested. Examples: 
	 *     check if a file exists and is readable: checkPath( "blah", EnumSet.of( FILE ), EnumSet.of( EXISTS, READABLE ), false ); 
	 *     check if a directory exists and is readable,writeable: checkPath( "foo/blah", EnumSet.of( DIRECTORY), EnumSet.of( EXISTS, READABLE, WRITABLE ), true ); 
	 * @throws IOException 
	 */
	public static boolean checkPath( String pattern, EnumSet<PatternPortion> portions, EnumSet<FileAttribute> attrs, EnumSet<FileAction> actions ) throws IOException {
		String [] patterns = split( pattern, PATTERN_DELIMITER );  // Bugs in String [] keys = pattern.split( " -\\x2E" );  // x2E= point
		return checkPath( patterns, portions, attrs, actions );
	}
	
	/** Checks portions of paths for file attributes. Add them if requested. Examples: 
	 *     check if a file exists and is readable: checkPath( "", new String[]{"blah"}, EnumSet.of( FILE ), EnumSet.of( EXISTS, READABLE ), false ); 
	 *     check if a directory exists and is readable,writeable: checkPath( "", new String[]{"foo","blah"}, EnumSet.of( DIRECTORY), EnumSet.of( EXISTS, READABLE, WRITABLE ), true ); 
	 * @throws IOException 
	 */
	public static boolean checkPath( String [] patterns, EnumSet<PatternPortion> portions, EnumSet<FileAttribute> attrs, EnumSet<FileAction> actions ) throws IOException {
		// public enum PatternPortion {
		//	ROOT		// 0 of pattern
		// 	ALL,  		// 0..N of pattern
		// 	DIRECTORY, 	// 0..N-1 of pattern
		//	FILE,		// N of pattern
		//	PARENT,		// N-1 of pattern
		//};

		// For each path, ensure that requested piece has file attrs. Optionall add if requested
	    // Patterni = 0..n-1 for just paths, n-2 for path+filename
		// int length = pathOnly ?  paths.length - 2: paths.length - 1;
		boolean result = true;
					
		Path currentPath = null;
		// public static Path get(String first,String... more)
		// getPath("/foo","bar","gus")  =>  "/foo/bar/gus" 
		
		for ( int patterni = 0; patterni < patterns.length; patterni++) {
			boolean lastPathPattern = patterni == patterns.length - 1;
			if ( ( 0 == patterni ) && ( portions.contains( ROOT ) || portions.contains( ALL ) || portions.contains( PatternPortion.DIRECTORY )  ))
				currentPath = Paths.get( patterns[ patterni ] );
			else { 
				currentPath = Paths.get( currentPath.toString(), patterns[ patterni ] );
			}			
			
			// Use cache unless requestd || actions || empty cache.
			if ( !cachePaths || (actions.size() > 0) || !checkedPaths.contains( currentPath.toString() ) ) {
				File currentFile = currentPath.toFile();
				// if ( MetaRenamer.verbose ) {
				// 	StringBuffer attr = new StringBuffer();
				//	if ( currentFile.exists() ) attr.append( "E" );
				//	if ( currentFile.canRead()) attr.append( "R" );
				//	if ( currentFile.canWrite()) attr.append( "W" );
				//	if ( currentFile.canExecute()) attr.append( "E" );
				//	if ( currentFile.exists() ) {
				//		// Files will throw IOException if non-existent.
				//		if ( Files.isHidden( currentPath )) attr.append( "H" );
				//		if ( Files.isSymbolicLink( currentPath )) attr.append( "L" );
				//	}
				//	System.out.println( "   checkPath currentFile=\"" + currentFile.getPath() + "\", absPath=\"" + currentFile.getAbsolutePath() + "\", attrs=" + attr.toString() );
				// }					
				if (attrs.contains( EXISTS )) result &= currentFile.exists();
				if ( actions.contains( CREATE ) && !currentFile.exists() ) {
  			       if ( verbose ) 
  			    	   System.out.println( msgPrefix + "create file=" + currentFile.toString() );
  			       if (!testMode) {
  			    	 if ( lastPathPattern )
  			    		 currentFile.createNewFile();
  			    	 else
  			    		 currentFile.mkdir();
   				     result = currentFile.exists(); // redo
  			       }
  			       // reset non-exist to true
				}
				if ( actions.contains( UPDATE )  && lastPathPattern ) { // only update bottom most one in path
					if ( verbose )
	 			    	System.out.println( msgPrefix + "update file=" + currentFile.toString() );
			        if (!testMode) {
			        	currentFile.setLastModified( (new Date()).getTime() );        	  
			        }
				}
				if (attrs.contains( READABLE )) result &= currentFile.canRead();
				if (attrs.contains( WRITABLE )) result &= currentFile.canWrite();
				if (attrs.contains( EXECUTABLE )) result &= currentFile.canExecute();
				if (attrs.contains( FileAttribute.DIRECTORY ) && lastPathPattern) result &= currentFile.isDirectory();
				if (attrs.contains( FileAttribute.FILE ) && lastPathPattern) result &= currentFile.isFile();
				if ( result ) {
					// Files will throw IOException if non-existent.
					if (attrs.contains( HIDDEN )) result &= Files.isHidden( currentPath );
					if (attrs.contains( LINK )) result &= Files.isSymbolicLink( currentPath );
				}
				if ( actions.contains( DELETE ) && lastPathPattern) { // only delete bottom most one in path
					if ( verbose )
	 			    	System.out.println( msgPrefix + "delete file=" + currentFile.toString() );
			        if (!testMode) {
			        	currentFile.delete();        	  
			        }
				} else {
					if (cachePaths)
						checkedPaths.add( currentPath.toString() ); // do not add if just deleted.
				}
				if ( !result ) return result; // short circuit
			}
		}
		return result;
	}
	
	/** Recommends or performs action on media directory name. Currently only looks at audio MP3 and MP4 media types. s*/
	public static void dirNameAction( final Metadata metaData ) throws IOException {
		MediaType mediaType = MediaType.parse( metaData.get( MEDIATYPE_KEY ));
		if ( "audio/mp4".equals( mediaType.toString() ) || "audio/mpeg".equals( mediaType.toString() )) {
			// Recall that pattern contains full path/filename, 
			// patterns [] contains pattern broken up by path delimiters. [...,parent2,parent1,parent0,filename]
			// patternKeyNames  contains list of all key names in pattern
			
			String oldPath =  metaData.get( Metadata.RESOURCE_NAME_KEY );
			
			// Check that the file exists and is readable. 
		    boolean oldExists = checkPath( oldPath, EnumSet.of( PatternPortion.ALL ), EnumSet.of( EXISTS, READABLE ), EnumSet.noneOf( FileAction.class ) );
		    if ( !oldExists  ) {
		    	if ( verbose )
		    		System.out.println( "   file \"" + oldPath + "\" does not exist or is not readable." );
		    	return;
		    }
		    
		    // Propose a new pattern.
		    String proposedPattern = new String( pattern ); 
			for ( String key: patternKeyNames ) {
				String value = metaData.get( key );
				// System.out.println( "   key=" + key + ", value=" + value);
				proposedPattern = proposedPattern.replaceAll( key , value );
			}
			
		    Path proposedPath = Paths.get( destPath, proposedPattern );
		    if ( verbose ) {
		    	System.out.println( msgPrefix + " change old pattern\"" + oldPath + "\" to\n   new pattern \"" + proposedPath + "\"." );
		    }
		    
//			String proposedName = parentNamePattern;
//			if ( !testMode ) {
//			    Path oldDir = Paths.get( metaData.get( "parentPath" ), metaData.get( "parentName" ) ); 
//			    Path newDir = Paths.get( metaData.get( "parentPath" ), proposedName ); 
//				boolean oldExists = Files.exists( oldDir );
//				dirsVisited++;
//				if ( oldExists ) {
//				   long lastMod = oldDir.toFile().lastModified();
//				   if ( verbose )
//				      System.out.println ( "   " + oldDir.getFileName() +  ", exists=" + oldExists + ", lastMod=" + (new Date( lastMod )).toString());			   
//				} else {					
//				   if ( verbose )
//				      System.out.println ( "   " + oldDir.getFileName() +  ", exists=" + oldExists );
//				}
//				boolean newExists = Files.exists( newDir );
//				dirsVisited++;
//				if ( newExists  ) {
//				   dirsCollided++;
//				   long lastMod = newDir.toFile().lastModified();
//				   if ( verbose )
//   				      System.out.println ( "   " + newDir.getFileName() +  ", exists=" + newExists + ", lastMod=" + (new Date( lastMod )).toString());			   					
//				} else {
//				   if ( verbose )
//				      System.out.println ( "   " + newDir.getFileName() + ", exists=" + newExists );
//				}
//				if ( oldExists && !newExists ) {
//  				   System.out.println( "   action: dir  rename \"" + parentName + "\" to \"" + proposedName + "\"" );
//  				   // Visit each name in the pattern and potentially create directories.
//  				   // dirsCreated++;
//  				   // Rename old directory to new.
//  				   dirsRenamed++;
//				   if ( verbose )
//					  System.out.println( "   action: rename success" );
//				   // Files.move( oldPath, newPath );	
//				}
//			}
		// } else if ( "audio/x-wav".equals( mediaType.toString() )) {			
		} else {
		   // System.out.println( "   no action: type \"" + mediaType.toString() + "\"" );
		}			
	}

	/** Recommends or performs action on media file name. */
	public static void fileNameAction( final Metadata metaData ) throws IOException {
		MediaType mediaType = MediaType.parse( metaData.get( MEDIATYPE_KEY ));
		if ( "audio/mp4".equals( mediaType.toString() ) || "audio/mpeg".equals( mediaType.toString() )) {
			if ( verbose ) {
				// List all metadata
				String [] metadataNames = metaData.names();
				for (String name : metadataNames) {
					System.out.println("   " + name + ": " + metaData.get(name));
				}
			}

//			String proposedName = pattern;
//			for ( String key: keys) {
//				String value = metaData.get( key );
//				if ( key.contains( "trackNumber")) {
//					value = cleanTrack( value );
//				}
//				// System.out.println( "   key=" + key + ", value=" + value);
//				proposedName = proposedName.replace( key , value );
//			}
//			// System.out.println( "   action: file rename \"" + metaData.get( "fileName") + "\" to \"" + proposedName + "\"" );
//			if ( !testMode ) {
//			    Path dir = Paths.get( metaData.get( "parentPath" ), metaData.get( "parentName" ) ); 
//       	        Path oldFile = dir.resolve( metaData.get( "fileName" ) );
//				boolean oldExists = Files.exists(  oldFile );
//				if ( oldExists ) {
//				   long lastMod = oldFile.toFile().lastModified();
//				   if ( verbose )
//				      System.out.println ( "   " + oldFile.getFileName() +  ", exists=" + oldExists + ", lastMod=" + (new Date( lastMod )).toString());			   
//				} else {
//				   if ( verbose )
//   				      System.out.println ( "   " + oldFile.getFileName() +  ", exists=" + oldExists );
//				}
//       	        Path newFile = dir.resolve( proposedName );
//				boolean newExists = Files.exists( newFile );
//				if ( newExists  ) {
//				   filesCollided++;
//				   long lastMod = newFile.toFile().lastModified();
//				   if ( verbose )
//				      System.out.println ( "   " + newFile.getFileName() +  ", exists=" + newExists + ", lastMod=" + (new Date( lastMod )).toString());			   					
//				} else {
//				   if ( verbose )
//				      System.out.println ( "   " + newFile.getFileName() + ", exists=" + newExists );
//				}
//				if ( oldExists && !newExists ) {
//  				   filesRenamed++;
// 				   System.out.println( "   action: file rename \"" + oldFile.getFileName() + "\" to \"" + proposedName + "\"" );
//  				   if ( verbose )
//  					  System.out.println( "   action: rename success" );
//				   // Files.move( oldPath, newPath );	
//				}
//			}
		// } else if ( "audio/x-wav".equals( mediaType.toString() )) {			
		} else {
		   if ( verbose ) {
		      System.out.println( "   no action: type \"" + mediaType.toString() + "\"" );
		   }
		}	
	}
	
	/** 
	 * Updates and cleans Metadata "xmpDM:trackNumber".
	 * Converts 1/6 to 1. 
	 */
	public static void cleanTrack( Metadata metadata ) {
		String dirtyTrack = metadata.get( "xmpDM:trackNumber" );
		int loc = dirtyTrack.indexOf( "/" );
		if( -1 != loc ) {
			dirtyTrack = dirtyTrack.substring( 0, loc );
		}
		if ( dirtyTrack.length() == 1 ) {
			dirtyTrack = "0" + dirtyTrack;
		}
		metadata.set( "xmpDM:trackNumber", dirtyTrack );
	}

	/** 
	 * Creates Metadata "xmpDM:releaseYear" from "xmpDM:releaseDate" 
	 */
	public static void addYear( Metadata metadata ) {
		String releaseDate = metadata.get( "xmpDM:releaseDate" );
		if ( null == releaseDate )
			return;
		int loc = releaseDate.indexOf( "-" );
		if ( -1 != loc ) {
			metadata.add("xmpDM:releaseYear" , releaseDate.substring( 0, loc ));
			return;
		}
		loc = releaseDate.indexOf( "/" );
		if ( -1 != loc ) {
			metadata.add("xmpDM:releaseYear" , releaseDate.substring( 0, loc ));
			return;
		}
		metadata.add("xmpDM:releaseYear" , releaseDate );
	}
	
	/** Split since Java regex has trouble with "." */
	public static String [] split( String pattern, String delimiters ) {
		List<String> keys = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer( pattern, delimiters );
		while ( st.hasMoreTokens() ) {
			keys.add(  st.nextToken() );
		}
		return keys.toArray(new String[0]);
	}
}