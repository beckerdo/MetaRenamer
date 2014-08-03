package info.danbecker.metarenamer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.tika.metadata.Metadata;

/**
 * Utilities used by metadata programs.
 * @author <a href="mailto://dan@danbecker.info>Dan Becker</a>
 */
public class MetaUtils {
	public static final String PATTERN_DELIMITER = "/";
	public static final String PATTERN_DEFAULT = "xmpDM:albumArtist/xmpDM:releaseYear - xmpDM:album/xmpDM:artist - xmpDM:releaseYear - xmpDM:album - xmpDM:trackNumber - title.extension";


	public static void listAllMetadata( Metadata metadata) {
		// List all metadata
		String [] metadataNames = metadata.names();
		for (String name : metadataNames) {
				System.out.println("   " + name + ": " + metadata.get(name));
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
    	
	    // cleanup
	    addYear( metadata );
    	cleanTrack( metadata );

	    String albumArtist = metadata.get( "xmpDM:albumArtist" );
	    if ((null == albumArtist ) || ( albumArtist.length() == 0)) {
		    metadata.set( "xmpDM:albumArtist", metadata.get( "xmpDM:artist" ) );
	    }
	}
	
	
	/** 
	 * Updates and cleans Metadata "xmpDM:trackNumber", if present.
	 * Converts 1/6 to 1. 
	 */
	public static void cleanTrack( Metadata metadata ) {
		String dirtyTrack = metadata.get( "xmpDM:trackNumber" );
		if (( null != dirtyTrack ) && ( dirtyTrack.length() > 0 )){ 
			int loc = dirtyTrack.indexOf( "/" );
			if( -1 != loc ) {
				dirtyTrack = dirtyTrack.substring( 0, loc );
			}
			if ( dirtyTrack.length() == 1 ) {
				dirtyTrack = "0" + dirtyTrack;
			}
			metadata.set( "xmpDM:trackNumber", dirtyTrack );
		}
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
	

	public static String getAttributes( String path) {
		Path currentPath = Paths.get( path );
		File currentFile = currentPath.toFile(); 
		StringBuffer attr = new StringBuffer();
		if ( currentFile.exists() ) attr.append( "E" );
		if ( currentFile.canRead()) attr.append( "R" );
		if ( currentFile.canWrite()) attr.append( "W" );
		if ( currentFile.canExecute()) attr.append( "E" );
		if ( currentFile.exists() ) {
			// Files will throw IOException if non-existent.
			try {
				if ( Files.isHidden( currentPath )) attr.append( "H" );
			} catch (IOException e) {}
			if ( Files.isSymbolicLink( currentPath )) attr.append( "L" );
		}
		return attr.toString();
	}

	/** Recusively delete folder, even if it has contents. */
	public static void deleteFolder(File folder) {
	    File [] files = folder.listFiles();
	    if(files!=null) { //some JVMs return null for empty dirs
	        for(File f: files) {
	            if(f.isDirectory()) {
	                deleteFolder(f);
	            } else {
	                f.delete();
	            }
	        }
	    }
	    folder.delete();
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
		proposedString = proposedString.replace( '/', '¦' );
		proposedString = proposedString.replace( '\\', '¦' );
		proposedString = proposedString.replace( '?', '!' );
		proposedString = proposedString.replace( '*',  '+' );
		return proposedString;
	}
	
	/** Recusively delete folder, even if it has contents. */
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