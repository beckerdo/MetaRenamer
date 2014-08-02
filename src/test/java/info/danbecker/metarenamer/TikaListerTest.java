package info.danbecker.metarenamer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.cli.TikaCLI;
import org.junit.Test;

public class TikaListerTest {
	
	@SuppressWarnings("unused")
	private static Logger logger = LoggerFactory.getLogger(TikaListerTest.class);
	
	@Test
    public void testVersion() throws Exception {
		TikaCLI.main(new String[]{"--version"});
	}

//	@Test
//    public void testHelp() throws Exception {
//		TikaCLI.main(new String[]{"--help"});
//	}
//
//	@Test
//    public void testParsers() throws Exception {
//		TikaCLI.main(new String[]{"--list-parsers"});
//	}
//
//	@Test
//    public void testParserDetails() throws Exception {
//		TikaCLI.main(new String[]{"--list-parser-details"});
//	}
//
//	@Test
//    public void testDetectors() throws Exception {
//		TikaCLI.main(new String[]{"--list-detectors"});
//	}
//
//	@Test
//    public void testMetadataModels() throws Exception {
//		TikaCLI.main(new String[]{"--list-met-models"});
//	}
//
//	@Test
//    public void testSupportedTypes() throws Exception {
//		TikaCLI.main(new String[]{"--list-supported-types"});
//	}

}
