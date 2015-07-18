package info.danbecker.metarenamer;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.tika.metadata.Metadata;

/**
 * Utilities used by metadata programs.
 * @author <a href="mailto://dan@danbecker.info>Dan Becker</a>
 */
public class MetaUtils {

	public static void listAllMetadata( Metadata metadata) {
		// List all metadata
		String [] metadataNames = metadata.names();
		for (String name : metadataNames) {
				System.out.println("   " + name + ": " + metadata.get(name));
		}
	}
	
	/** 
	 * Allows the synthesis or clean-up of metadata. 
	 * Requires parsed metadata for year, track, artists. 
	 * @oaram someDefaults of the form "key1=value1;key2=value2" 
	 */
	public static void updateMetadata( Metadata metadata ) {
		// cleanup
	    cleanYear( metadata );
    	cleanTrack( metadata );

		// synthesis - add new items from existing items
    	String key = "xmpDM:albumArtist";
	    String albumArtist = metadata.get( key );
	    if ((null == albumArtist ) || ( albumArtist.length() == 0)) {
	    	albumArtist = metadata.get( "Author" );
		    if ((null == albumArtist ) || ( albumArtist.length() == 0)) 
		    	albumArtist = metadata.get( "creator" );
		    metadata.set( key, albumArtist );
	    }
	    
	    // Mapping, transformation
	    // e.g. albumArtist "Various Artists" to "Various"
	    // e.g. "Compilation" to "Various"
	    if (( null != albumArtist )) {
	    	if ("Various Artists".equals(albumArtist)) {
			    metadata.set( "xmpDM:albumArtist", "Various" );
	    	} else if ("Various artists".equals(albumArtist)) { 
			    metadata.set( "xmpDM:albumArtist", "Various" );
	    	} else if ( albumArtist.contains( "rtist")) {
	    		System.err.println( "   albumArtist=\"" + albumArtist + "\"" );
	    	}
	    }
	}
		
	/** 
	 * Updates and cleans Metadata "xmpDM:trackNumber", if present.
	 * Converts 1/6 to 1. 
	 * If the track number does not exist, add String MetaRenamer.MISSING_TRACK_FILLER
	 */
	public static void cleanTrack( Metadata metadata ) {
		String dirtyTrack = metadata.get( "xmpDM:trackNumber" );
		boolean update = false;
		if (( null != dirtyTrack ) && ( dirtyTrack.length() > 0 )){ 
			int loc = dirtyTrack.indexOf( "/" );
			if ( -1 != loc ) {
				dirtyTrack = dirtyTrack.substring( 0, loc );
				update = true;
			}
			if ( dirtyTrack.length() == 1 ) {
				dirtyTrack = "0" + dirtyTrack;
				update = true;
			}
		} else {
			dirtyTrack = MetaRenamer.MISSING_TRACK_FILLER;
			update = true;
		}
		if ( update )
			metadata.set( "xmpDM:trackNumber", dirtyTrack );
	}

	/** 
	 * Creates Metadata "xmpDM:releaseYear" from "xmpDM:releaseDate" 
	 */
	public static void cleanYear( Metadata metadata ) {
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
	

	/** Returns file attribute String for given file. */
	public static String getAttributes( String path ) {
		Path currentPath = Paths.get( path );
		File currentFile = currentPath.toFile();
		return getAttributes( currentFile );
	}

	/** Returns file attribute String for given file. */
	public static String getAttributes( File file ) {
		StringBuffer attr = new StringBuffer();
		if ( file.exists() ) attr.append( "E" );
		if ( file.isFile() ) attr.append( "F" );
		if ( file.isDirectory() ) attr.append( "D" );
		if ( file.canRead()) attr.append( "R" );
		if ( file.canWrite()) attr.append( "W" );
		if ( file.canExecute()) attr.append( "X" );
		if ( file.exists() ) {
			// Files will throw IOException if non-existent.
			try {
				if ( Files.isHidden( Paths.get( file.getPath()) )) attr.append( "H" );
			} catch (IOException e) {}
			if ( Files.isSymbolicLink( Paths.get( file.getPath()) )) attr.append( "L" );
		}
		return attr.toString();
	}

	/** Recursively delete folder, even if it has contents. */
	public static void deleteFolder(File folder) throws IOException  {
	    File [] files = folder.listFiles();
	    if (files!=null) { //some JVMs return null for empty dirs
	        for(File f: files) {
	            if(f.isDirectory()) {
	                deleteFolder(f);
	            } else {
	                f.delete();
	                // Files.delete( Paths.get( f.getAbsolutePath() ));
	            }
	        }
	    }
	    folder.delete();
        // Files.delete( Paths.get( folder.getAbsolutePath() ));
	}	
	
	/** Recursively copy folder, even if it has contents. */
	public static void copyFolder( final Path sourcePath, final Path targetPath ) throws IOException  {
	    Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
	        @Override
	        public FileVisitResult preVisitDirectory(final Path dir,
	                final BasicFileAttributes attrs) throws IOException {
	            Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
	            return FileVisitResult.CONTINUE;
	        }

	        @Override
	        public FileVisitResult visitFile(final Path file,
	                final BasicFileAttributes attrs) throws IOException {
	            Files.copy(file, targetPath.resolve(sourcePath.relativize(file)));
	            return FileVisitResult.CONTINUE;
	        }
	    });
	}
	
	public static Path escapeChars( Path proposed ) {
		return Paths.get( escapeChars( proposed.toString() ));
	}
	
	/** Replace bad file name characters with similar looking characters. */
	public static String escapeChars( String proposedString ) {
		// Cannot have the following characters in a Windows file system.
		// < > : " / \ | ? *
		proposedString = proposedString.replace( ':', ',' );
		proposedString = proposedString.replace( '"', '\'' );
		proposedString = proposedString.replace( '/', '!' );
		proposedString = proposedString.replace( '\\', '!' );
		proposedString = proposedString.replace( '|', '!' );
		proposedString = proposedString.replace( '?', '!' );
		proposedString = proposedString.replace( '*',  '+' );
		return proposedString;
	}
	
	/** Recursively delete folder, even if it has contents. */
	public static long recursiveSize(File folder) throws IOException {
		long size = 0;
	    File [] files = folder.listFiles();
	    if(files!=null) { //some JVMs return null for empty dirs
	        for(File f: files) {
	            if(f.isDirectory()) {
	                size += recursiveSize(f);
	            } else {
	                size += Files.size( Paths.get( f.getAbsolutePath() ) );
	            }
	        }
	    }
	    return size;
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