package info.danbecker.metarenamer;

import info.danbecker.metarenamer.MetaRenamer.FileAction;
import static info.danbecker.metarenamer.MetaRenamer.FileAction.*;
import static info.danbecker.metarenamer.FileAttribute.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetaRenamerTest {
	
	@SuppressWarnings("unused")
	private static Logger logger = LoggerFactory.getLogger(MetaRenamerTest.class);
	
	@Test
    public void testCheckPath() throws IOException {
		MetaRenamer.testMode = true;
		MetaRenamer.verbose = true;
		boolean result = false;

		result = MetaRenamer.checkPath( ".", EnumSet.of( EXISTS, READABLE, DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ". exists readable", result );
		
		result = MetaRenamer.checkPath( ".", EnumSet.of( EXISTS, READABLE, DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ". exists readable cached", result );
		
		result = MetaRenamer.checkPath( "..", EnumSet.of( EXISTS, READABLE, DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ".. exists readable", result );

		// Do it twice to see if cache works
	    result = MetaRenamer.checkPath( "..", EnumSet.of( EXISTS, READABLE, DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ".. exists readable cached", result );

		// Text file
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/PathA/testA.txt", 
			EnumSet.of( EXISTS, READABLE, WRITABLE, FILE), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( "testA.txt exists readable writeable file", result );

		// Text file (recall that these results cached)
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/PathB/testB.txt", 
			EnumSet.of( EXISTS, READABLE, WRITABLE, HIDDEN, FILE ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( "testB.txt not exists readable writeable hidden", !result );

		// Non-existent file
		result = MetaRenamer.checkPath( "blah", 
			EnumSet.of( EXISTS, HIDDEN ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( "blah not exists readable writeable", !result );
	}

	@Test
    public void testCheckPathActions() throws IOException {
		MetaRenamer.testMode = true;
		MetaRenamer.verbose = false;
		boolean result = false;

		// Text file create test mode
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/pathA/blah.txt", 
			EnumSet.of( EXISTS, READABLE, WRITABLE, FILE ), EnumSet.of( CREATE )  );	
		assertTrue( "blah.txt test mode not created", !result );

		MetaRenamer.testMode = false;
		MetaRenamer.verbose = true;
		
		// Text file create
		String tempFileName = "src/test/resources/info/danbecker/metarenamer/pathX/pathY/pathZ/blah.txt";
		String parentName = (new File( tempFileName )).getParent();
		result = MetaRenamer.checkPath( parentName, 
				EnumSet.of( EXISTS, READABLE, WRITABLE, DIRECTORY ), EnumSet.of( CREATE )  );	
			assertTrue( "blah.txt created exists", result );
		result = MetaRenamer.checkPath( tempFileName, 
			EnumSet.of( EXISTS, READABLE, WRITABLE, FILE ), EnumSet.of( CREATE )  );	
		assertTrue( "blah.txt created exists", result );

		// Text file update
		File file = new File( tempFileName );
		long lastModified = file.lastModified();		
		try { Thread.sleep( 1000 ); } catch (InterruptedException e) {}
		result = MetaRenamer.checkPath( tempFileName, 
			EnumSet.of( EXISTS, READABLE, WRITABLE, FILE ), EnumSet.of( UPDATE )  );	
		assertTrue( "blah.txt updated exists", result );
		assertTrue( "blah.txt updated timestamp", file.lastModified() > lastModified );

		// Text file delete
		result = MetaRenamer.checkPath( tempFileName, EnumSet.of( FILE ), EnumSet.of( DELETE )  );	
		assertTrue( "blah.txt deleted", result );

		// Dir delete
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/pathX", 
			EnumSet.of( DIRECTORY ), EnumSet.of( DELETE )  );	
		assertTrue( "pathX deleted", result );
		MetaRenamer.verbose = false;
	}

	@Test
    public void testStats() throws Exception {
		// Test visiting in test mode.
		MetaRenamer.filesVisited = 0;
		MetaRenamer.filesCollided = 0;
		MetaRenamer.filesRenamed = 0;
		MetaRenamer.filesCreated = 0;
		MetaRenamer.dirsVisited = 0;
		MetaRenamer.dirsCollided = 0;
		MetaRenamer.dirsRenamed = 0;
		MetaRenamer.dirsCreated = 0;

		MetaRenamer.main( new String [] { "-t", "-v", "-s", "src/test/resources/info/danbecker/metarenamer/" } );

		assertEquals( "files visited", 4, MetaRenamer.filesVisited );
		assertEquals( "files collided", 0, MetaRenamer.filesCollided );
		assertEquals( "files renamed", 0, MetaRenamer.filesRenamed );
		assertEquals( "files created", 0, MetaRenamer.filesCreated );
		assertEquals( "dirs visited", 0, MetaRenamer.dirsVisited );
		assertEquals( "dirs collided", 0, MetaRenamer.dirsCollided );
		assertEquals( "dirs renamed", 0, MetaRenamer.dirsRenamed );
		assertEquals( "dirs created", 0, MetaRenamer.dirsCreated );
	}
	
	@Test
    public void testCopy() throws Exception {
		// Test copy/rename to a temp directory
		Path sourcePath = Paths.get( "src/test/resources/info/danbecker/metarenamer/"  );
		long sourceSize = MetaUtils.recursiveSize( sourcePath.toFile() );
		
		Path tempPath = Files.createTempDirectory( "testPath" );
		long oldSize = MetaUtils.recursiveSize( tempPath.toFile() );
		// System.out.println( "   path old size=" + oldSize );
		MetaRenamer.main( new String [] { "-v", "-s", sourcePath.toString(), "-d", tempPath.toString() } );
		long newSize = MetaUtils.recursiveSize( tempPath.toFile() );
		// System.out.println( "   path new size=" + newSize );

		assertTrue( "copied directory size compare", newSize > oldSize );
		assertEquals( "copied directory exact size",  919522, newSize );

		// Assure nothing was moved/deleted from source directory.
		long sourceSizeNew = MetaUtils.recursiveSize( sourcePath.toFile() );
		assertEquals( "source directory exact size",  sourceSize, sourceSizeNew );

		// Clean up
		MetaUtils.deleteFolder( tempPath.toFile() );
		long cleanSize = MetaUtils.recursiveSize( tempPath.toFile() );
		assertEquals( "cleaned directory exact size", 0, cleanSize );
	}
	
	@Test
    public void testMove() throws Exception {
		// Test copy/rename to a temp directory
		Path sourcePath = Paths.get( "src/test/resources/info/danbecker/metarenamer/"  );
		long sourceSize = MetaUtils.recursiveSize( sourcePath.toFile() );
		
		// Copy to a temp directory so we don't muck up the source directory
		Path copyPath = Files.createTempDirectory( "testPath" );
		// System.out.println( "   path old size=" + oldSize );
		MetaRenamer.main( new String [] { "-v", "-s", sourcePath.toString(), "-d", copyPath.toString() } );
		long oldCopySize = MetaUtils.recursiveSize( copyPath.toFile() );
		// System.out.println( "   path new size=" + newSize );

		assertEquals( "copied directory exact size",  919522, oldCopySize );
		// Give the copies 5 seconds to close
		try { Thread.sleep( 5000 ); } catch (InterruptedException e) {}

		// Copy to a temp directory so we don't muck up the source directory
		Path movePath = Files.createTempDirectory( "testPath" );
		long oldMoveSize = MetaUtils.recursiveSize( movePath.toFile() );
		System.out.println( "   path old move size=" + oldMoveSize );
		MetaRenamer.main( new String [] { "-v", "-m", "-s", copyPath.toString(), "-d", movePath.toString() } );
		long newMoveSize = MetaUtils.recursiveSize( movePath.toFile() );
		System.out.println( "   path new move size=" + newMoveSize );

		assertTrue( "moved directory size compare", newMoveSize > oldMoveSize );
		assertEquals( "moved directory exact size",  919522, newMoveSize );

		long newCopySize = MetaUtils.recursiveSize( copyPath.toFile() );
		assertTrue( "copy directory size compare", newCopySize < oldCopySize );
		
		// Assure nothing was moved/deleted from source directoryu
		long sourceSizeNew = MetaUtils.recursiveSize( sourcePath.toFile() );
		assertEquals( "source directory exact size",  sourceSize, sourceSizeNew );

		// Clean up
		MetaUtils.deleteFolder( copyPath.toFile() );
		long cleanSize = MetaUtils.recursiveSize( copyPath.toFile() );
		assertEquals( "cleaned copy directory exact size", 0, cleanSize );

		MetaUtils.deleteFolder( movePath.toFile() );
		cleanSize = MetaUtils.recursiveSize( movePath.toFile() );
		assertEquals( "cleaned move directory exact size", 0, cleanSize );
	}
	
}