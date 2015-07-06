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
import java.util.Set;
import java.util.TreeSet;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class MetaRenamerTest {
	
	@Test
    public void testCheckPath() throws IOException {
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
		MetaRenamer.verbose = false;
		boolean result = false;
		
		// This really needs a clean-up to ensire stats are correct (e.g. collision count.)

		// Text file create test mode
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/pathA/blah.txt", 
			EnumSet.of( EXISTS, READABLE, WRITABLE, FILE ), EnumSet.of( CREATE )  );	
		assertTrue( "blah.txt test mode not created", !result );

		MetaRenamer.actionMode = true;
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
		try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}		
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

		MetaRenamer.main( new String [] { "-v", "-s", "src/test/resources/info/danbecker/metarenamer/" } );

		assertEquals( "files visited", 11, MetaRenamer.filesVisited );
		assertEquals( "files collided", 0, MetaRenamer.filesCollided );
		assertEquals( "files renamed", 0, MetaRenamer.filesRenamed );
		assertEquals( "files created", 0, MetaRenamer.filesCreated );
		assertEquals( "dirs visited", 10, MetaRenamer.dirsVisited );
		assertEquals( "dirs collided", 0, MetaRenamer.dirsCollided );
		assertEquals( "dirs renamed", 0, MetaRenamer.dirsRenamed );
		assertEquals( "dirs created", 0, MetaRenamer.dirsCreated );
	}
	
	@Test
    public void testCopy() throws Exception {
		// Works when run singly. Fails when run as suite.

		// Test copy/rename to a temp directory
		Path sourcePath = Paths.get( "src/test/resources/info/danbecker/metarenamer/"  );
		long sourceSize = MetaUtils.recursiveSize( sourcePath.toFile() );
		
		Path tempPath = Files.createTempDirectory( "metaTestPath" );
		// System.out.println( "   path old size=" + oldSize );
		MetaRenamer.main( new String [] { "-v", "-s", sourcePath.toString(), "-d", tempPath.toString() } );
		try { Thread.sleep( 2000 ); } catch (InterruptedException e) {	}
		long newSize = MetaUtils.recursiveSize( tempPath.toFile() );

		// assertTrue( "copied directory size compare", newSize == sourceSize );
		assertEquals( "copied directory exact size", 919522, newSize );

		// Assure nothing was moved/deleted from source directory.
		long sourceSizeNew = MetaUtils.recursiveSize( sourcePath.toFile() );
		assertEquals( "source directory exact size",  sourceSize, sourceSizeNew );

		// There should be two collisions.
		assertEquals( "files collided", 2, MetaRenamer.filesCollided );
		
		// Clean up
		try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}
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
		Path copyPath = Files.createTempDirectory( "metaTestPath" );
		MetaUtils.copyFolder( sourcePath,  copyPath );
		try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}
		long oldCopySize = MetaUtils.recursiveSize( copyPath.toFile() );
		// System.out.println( "   path new size=" + newSize );

		assertEquals( "copied directory exact size",  1894801, oldCopySize );

		// Move from temp copy directory to move directory
		Path movePath = Files.createTempDirectory( "metaTestPath" );
		long oldMoveSize = MetaUtils.recursiveSize( movePath.toFile() );
		MetaRenamer.main( new String [] { "-v", "-m", "-s", copyPath.toString(), "-d", movePath.toString() } );
		long newMoveSize = MetaUtils.recursiveSize( movePath.toFile() );

		// System.out.println( "   oldMoveSize=" + oldMoveSize + ", newMoveSize=" + newMoveSize );
		assertTrue( "moved directory size compare", newMoveSize > oldMoveSize );
		assertEquals( "moved directory exact size",  919522, newMoveSize );

		@SuppressWarnings("unused")
		long newCopySize = MetaUtils.recursiveSize( copyPath.toFile() );
		// System.out.println( "   oldCopySize=" + oldCopySize + ", newCopySize=" + newCopySize );
		// assertTrue( "moved directory size compare", newCopySize < oldCopySize );
		
		// Assure nothing was moved/deleted from source directoryu
		long sourceSizeNew = MetaUtils.recursiveSize( sourcePath.toFile() );
		assertEquals( "source directory exact size",  sourceSize, sourceSizeNew );

		// Clean up
		MetaUtils.deleteFolder( copyPath.toFile() );
		MetaUtils.deleteFolder( movePath.toFile() );
		// System.gc();
		// try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}
		try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}
		long moveCleanSize = MetaUtils.recursiveSize( movePath.toFile() );
		long copyCleanSize = MetaUtils.recursiveSize( copyPath.toFile() );

		// These sizes intermittently fail, so downgrading cleanup failure to a warning.
		if ( 0 != copyCleanSize )
			System.err.println( "   clean up of testing copy directory failed.");
		// assertEquals( "cleaned copy directory exact size", 0, copyCleanSize );	
		if ( 0 != moveCleanSize )
			System.err.println( "   clean up of testing move directory failed.");
		// assertEquals( "cleaned move directory exact size", 0, moveCleanSize );
	}
	
	@Test
    public void testMoveCorrectDirectory() throws Exception {
		// Tests move if sourceDir == destination Dir and file names must be changed.
		Path sourcePath = Paths.get( "src/test/resources/info/danbecker/metarenamer/correctDirBadFileName"  );
		long sourceSize = MetaUtils.recursiveSize( sourcePath.toFile() );
		
		// Copy to a temp directory so we don't muck up the source directory
		Path copyPath = Files.createTempDirectory( "metaTestPath" );
		MetaUtils.copyFolder( sourcePath,  copyPath );
		try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}
		long copySize = MetaUtils.recursiveSize( copyPath.toFile() );

		// System.out.println( "   sourceSize=" + sourceSize + ", copySize=" + copySize );
		assertEquals( "copied directory size",  sourceSize, copySize );
		assertEquals( "copied directory exact size",  919522, copySize );

		// Rename/move files in copy directory.
		MetaRenamer.main( new String [] { "-v", "-m", "-s", copyPath.toString() } );
		long moveSize = MetaUtils.recursiveSize( copyPath.toFile() );

		// System.out.println( "   copySize=" + copySize + ", moveSize=" + moveSize );
		assertTrue( "moved directory size compare", copySize == moveSize );
		assertEquals( "moved directory exact size",  919522, moveSize );

		// Clean up
		try { Thread.sleep( 1000 ); } catch (InterruptedException e) {	}
		MetaUtils.deleteFolder( copyPath.toFile() );
		long cleanSize = MetaUtils.recursiveSize( copyPath.toFile() );
		assertEquals( "cleaned copy directory exact size", 0, cleanSize );
	}

	@Test
	public void testLoadDoNotParse() throws IOException {
		Set<String> doNotParse = new TreeSet<String>();
		assertEquals( "doNotParse size",  0, doNotParse.size() );
		
		MetaRenamer.readDoNotParse( "src/test/resources/doNotParse.txt" , doNotParse);
		assertEquals( "doNotParse size",  3, doNotParse.size() );
	}
	
	@Test
	public void testDoNotParse() throws IOException {
		Set<String> doNotParse = new TreeSet<String>();
		assertEquals( "doNotParse size",  0, doNotParse.size() );
		
		MetaRenamer.readDoNotParse( "src/test/resources/doNotParse.txt" , doNotParse);
		assertEquals( "doNotParse size",  3, doNotParse.size() );
		
		assertEquals( "doNotParse test",  true, MetaRenamer.doNotParseStartsWith( doNotParse, "images/jpg" ) );
		assertEquals( "doNotParse test",  true, MetaRenamer.doNotParseStartsWith( doNotParse, "application/pdf" ) );
		assertEquals( "doNotParse test", false, MetaRenamer.doNotParseStartsWith( doNotParse, "audio/mp3" ) );
		assertEquals( "doNotParse test", false, MetaRenamer.doNotParseStartsWith( doNotParse, "" ) );
	}

	@Test
	public void testReadDateTime() {
		// Happy paths
		MetaRenamer.dateTimeComparator = MetaRenamer.Comparator.FALSE;
		MetaRenamer.dateTimeCompare = null;;
		assertEquals( "dateTime",  MetaRenamer.Comparator.FALSE, MetaRenamer.dateTimeComparator );
		assertNull( "dateTime",  MetaRenamer.dateTimeCompare );
		
		DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");				

		LocalDateTime dateTimeExpected = f.parseLocalDateTime( "2012-01-10T23:13:26" );
		MetaRenamer.readDateTime( "GT2012-01-10T23:13:26");
		assertEquals( "dateTime",  MetaRenamer.Comparator.GT, MetaRenamer.dateTimeComparator );
		assertEquals( "dateTime",  dateTimeExpected.toDate(), MetaRenamer.dateTimeCompare );

		f = DateTimeFormat.forPattern("yyyy-MM-dd");				
		
		dateTimeExpected = f.parseLocalDateTime( "2015-07-04" );
		MetaRenamer.readDateTime( "LE2015-07-04");
		assertEquals( "dateTime",  MetaRenamer.Comparator.LE, MetaRenamer.dateTimeComparator );
		assertEquals( "dateTime",  dateTimeExpected.toDate(), MetaRenamer.dateTimeCompare );
	
		// Bad comparator
		MetaRenamer.readDateTime( "FOO2015-07-04");
		assertEquals( "dateTime",  MetaRenamer.Comparator.LE, MetaRenamer.dateTimeComparator ); // last one set
		
		// Bad date
		dateTimeExpected = f.parseLocalDateTime( "2015-07-04" );
		try {
			MetaRenamer.readDateTime( "EQ2001");
		} catch ( IllegalArgumentException e ) {
			assertNotNull( "dateTime",  e );
			assertEquals( "dateTime",  MetaRenamer.Comparator.EQ, MetaRenamer.dateTimeComparator );
			assertEquals( "dateTime",  dateTimeExpected.toDate(), MetaRenamer.dateTimeCompare );			
		}

		// Slashes to dashes
		dateTimeExpected = f.parseLocalDateTime( "2015-07-01" );
		MetaRenamer.readDateTime( "LE2015/07/01");
		assertEquals( "dateTime",  MetaRenamer.Comparator.LE, MetaRenamer.dateTimeComparator );
		assertEquals( "dateTime",  dateTimeExpected.toDate(), MetaRenamer.dateTimeCompare );
		dateTimeExpected = f.parseLocalDateTime( "2015-01-01" );
		// MetaRenamer.readDateTime( "LE2015" + "\\" + "01" + "\\" + "01");
		// assertEquals( "dateTime",  MetaRenamer.Comparator.LE, MetaRenamer.dateTimeComparator );
		// assertEquals( "dateTime",  dateTimeExpected.toDate(), MetaRenamer.dateTimeCompare );
	}
	
	@Test
	public void testTestDateTime() {
		// Happy paths
		DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");						
		LocalDateTime test = f.parseLocalDateTime( "2015-07-04" );
		MetaRenamer.readDateTime( "FALSE2015-07-04");
		
		assertFalse( "dateTime",  MetaRenamer.testDateTime( null, null,  null ) );
		assertFalse( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.Comparator.FALSE, null, null ) );
		assertTrue( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.Comparator.TRUE, null, null ) );
		assertFalse( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.Comparator.EQ, null, test.toDate() ) );
		assertFalse( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.Comparator.EQ, test.toDate(), null ) );

		MetaRenamer.readDateTime( "EQ2015-07-04");
		assertTrue( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, test.toDate() ) );
		test = f.parseLocalDateTime( "2015-07-05" );
		assertFalse( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, test.toDate() ) );
		MetaRenamer.readDateTime( "NE2015-07-04");
		assertTrue( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, test.toDate() ) );

		test = f.parseLocalDateTime( "2015-07-05" );
		MetaRenamer.readDateTime( "GT2015-07-04");
		assertTrue( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, test.toDate() ) );
		MetaRenamer.readDateTime( "GE2015-07-04");
		assertTrue( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, test.toDate() ) );
		MetaRenamer.readDateTime( "LT2015-07-04");
		assertFalse( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, test.toDate() ) );
		MetaRenamer.readDateTime( "LE2015-07-04");
		assertFalse( "dateTime",  MetaRenamer.testDateTime( MetaRenamer.dateTimeComparator, MetaRenamer.dateTimeCompare, test.toDate() ) );
	}
}