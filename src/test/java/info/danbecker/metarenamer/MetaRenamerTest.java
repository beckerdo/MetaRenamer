package info.danbecker.metarenamer;

import info.danbecker.metarenamer.MetaRenamer.FileAction;
import static info.danbecker.metarenamer.MetaRenamer.PatternPortion.*;
import static info.danbecker.metarenamer.MetaRenamer.FileAction.*;
import static info.danbecker.metarenamer.FileAttribute.*;

import java.io.File;
import java.io.IOException;
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

		result = MetaRenamer.checkPath( ".", EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, FileAttribute.DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ". exists readable", result );
		
		result = MetaRenamer.checkPath( ".", EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, FileAttribute.DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ". exists readable cached", result );
		
		result = MetaRenamer.checkPath( "..", EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, FileAttribute.DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ".. exists readable", result );

		// Do it twice to see if cache works
	    result = MetaRenamer.checkPath( "..", EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, FileAttribute.DIRECTORY ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( ".. exists readable cached", result );

		// Text file
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/PathA/testA.txt", 
			EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, WRITABLE, FileAttribute.FILE), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( "testA.txt exists readable writeable file", result );

		// Text file (recall that these results cached)
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/PathB/testB.txt", 
			EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, WRITABLE, HIDDEN ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( "testB.txt not exists readable writeable hidden", !result );

		// Non-existent file
		result = MetaRenamer.checkPath( "blah", 
			EnumSet.of( ALL ), EnumSet.of( EXISTS, HIDDEN ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( "blah not exists readable writeable", !result );

	
	}

	@Test
    public void testCheckPathActions() throws IOException {
		MetaRenamer.testMode = true;
		MetaRenamer.verbose = false;
		boolean result = false;

		// Text file create test mode
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/pathA/blah.txt", 
			EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, WRITABLE ), EnumSet.of( CREATE )  );	
		assertTrue( "blah.txt test mode not created", !result );

		// Text file create
		String tempFileName = "src/test/resources/info/danbecker/metarenamer/pathX/blah.txt";
		MetaRenamer.testMode = false;
		MetaRenamer.verbose = true;
		result = MetaRenamer.checkPath( tempFileName, 
				EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, WRITABLE ), EnumSet.of( CREATE )  );	
		assertTrue( "blah.txt created exists", result );

		// Text file update
		File file = new File( tempFileName );
		long lastModified = file.lastModified();		
		result = MetaRenamer.checkPath( tempFileName, 
				EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE, WRITABLE ), EnumSet.of( UPDATE )  );	
		assertTrue( "blah.txt updated exists", result );
		// file = new File( tempFileName );
		assertTrue( "blah.txt updatedexists", file.lastModified() > lastModified );

		// Text file delete
		result = MetaRenamer.checkPath( tempFileName, 
			EnumSet.of( ALL ), EnumSet.noneOf( FileAttribute.class ), EnumSet.of( DELETE )  );	
		assertTrue( "blah.txt deleted", result );

		// Dir delete
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/pathX", 
			EnumSet.of( ALL ), EnumSet.noneOf( FileAttribute.class ), EnumSet.of( DELETE )  );	
		assertTrue( "pathX deleted", result );
		MetaRenamer.verbose = false;
	}

	@Test
    public void testStats() throws IOException {
		boolean result = false;
		MetaRenamer.testMode = true;
		MetaRenamer.verbose = true;
		MetaRenamer.cachePaths = false;
		
		MetaRenamer.filesVisited = 0;
		MetaRenamer.filesCollided = 0;
		MetaRenamer.filesRenamed = 0;
		MetaRenamer.filesCreated = 0;
		MetaRenamer.dirsVisited = 0;
		MetaRenamer.dirsCollided = 0;
		MetaRenamer.dirsRenamed = 0;
		MetaRenamer.dirsCreated = 0;

		// Text file create test mode
		result = MetaRenamer.checkPath( "src/test/resources/info/danbecker/metarenamer/intervals/034-Interval Studies.mp3", 
			EnumSet.of( ALL ), EnumSet.of( EXISTS, READABLE ), EnumSet.noneOf( FileAction.class )  );	
		assertTrue( "files visited", MetaRenamer.filesVisited == 0 );
		assertTrue( "files collided", MetaRenamer.filesCollided == 0 );
		assertTrue( "files renamed", MetaRenamer.filesRenamed == 0 );
		assertTrue( "files created", MetaRenamer.filesCreated == 0 );
		assertTrue( "dirs visited", MetaRenamer.dirsVisited == 0 );
		assertTrue( "dirs collided", MetaRenamer.dirsCollided == 0 );
		assertTrue( "dirs renamed", MetaRenamer.dirsRenamed == 0 );
		assertTrue( "dirs created", MetaRenamer.dirsCreated == 0 );
		
		assertTrue( "mp3 exists", result );

	}

}
