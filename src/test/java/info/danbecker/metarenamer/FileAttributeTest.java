package info.danbecker.metarenamer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileAttributeTest {
	
	@SuppressWarnings("unused")
	private static Logger logger = LoggerFactory.getLogger(FileAttributeTest.class);
	
	@Test
    public void testEnums() {
		
		for ( FileAttribute fa : FileAttribute.values() ) { 
	
			assertEquals("Basic enum test", fa.name().toLowerCase(), fa.toString());
		}

	}

}
