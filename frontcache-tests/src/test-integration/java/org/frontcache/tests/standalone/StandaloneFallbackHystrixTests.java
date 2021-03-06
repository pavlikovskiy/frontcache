package org.frontcache.tests.standalone;

import java.io.File;

import org.frontcache.tests.TestConfig;
import org.frontcache.tests.base.FallbackHystrixTests;
import org.junit.AfterClass;

public class StandaloneFallbackHystrixTests extends FallbackHystrixTests {


/*	
	// do not cleanup before customeFallbackTest2LoadFromURL()
	// failed scenario
	// 1. server started and fallfack files generated
	// 2. file deleted from here (setUpClass())
	// 3. tests failed because file deleted
	
	// clean up files after tests
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		return;
	}
//*/
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		// cleanup after customeFallbackTest2LoadFromURL()
		String frontcacheHome = System.getProperty(TestConfig.FRONTCACHE_TEST_PROJECT_DIR_KEY) + "/FRONTCACHE_HOME_STANDALONE";
		File fallbackDataFile = new File(new File(frontcacheHome), "fallbacks/fallback2.txt");
		if (fallbackDataFile.exists())
			fallbackDataFile.delete();
		fallbackDataFile = new File(new File(frontcacheHome), "fallbacks/" + TestConfig.TEST_DOMAIN_FC1  + "/fallback2.txt");
		if (fallbackDataFile.exists())
			fallbackDataFile.delete();
	}
	

	@Override
	public String getFrontCacheBaseURLDomainFC1() {
		return getStandaloneBaseURLDomainFC1();
	}


	@Override
	public String getFrontCacheBaseURLLocalhost() {
		return getStandaloneBaseURLLocalhost();
	}

}
