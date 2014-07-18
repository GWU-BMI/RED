package gov.va.research.red.ex;

import gov.va.research.red.CVScore;
import gov.va.research.red.ex.REDExCrossValidator;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class REDExCrossValidatorTest {

	private static final Logger LOG = LoggerFactory.getLogger(REDExCrossValidatorTest.class);

	private static final String TEST_VTT_FILENAME = "weight1000.vtt";

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	
	@Test
	public void testCrossValidate() {
		try {
			REDExCrossValidator rexcv = new REDExCrossValidator();
			List<CVScore> results = rexcv.crossValidate(Arrays.asList(new File[] { new File(TEST_VTT_FILENAME) }), "weight", 10);
			int i = 0;
			for (CVScore score : results) {
				LOG.info("--- Run " + (i++) + " ---");
				LOG.info(score.getEvaluation());
			}
			LOG.info("--- Aggregate ---");
			CVScore aggregate = CVScore.aggregate(results);
			LOG.info(aggregate.getEvaluation());
			
			Assert.assertTrue("Accuracy is very bad", aggregate.calcAccuracy() > 0.25f);

		} catch (IOException e) {
			throw new AssertionError("Cross validation failed.", e);
		}
	}
}