package gov.va.research.ree;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CrossValidateTest {

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
			CrossValidate cv = new CrossValidate();
			List<CVScore> results = cv.crossValidate(new File(TEST_VTT_FILENAME), "weight", 2);
			int i = 0;
			for (CVScore score : results) {
				System.out.println("--- Run " + (i++) + " ---");
				System.out.println(score.getEvaluation());
			}
			System.out.println("--- Aggregate ---");
			CVScore aggregate = CVScore.aggregate(results);
			System.out.println(aggregate.getEvaluation());
			
			Assert.assertTrue("Accuracy is very bad", aggregate.calcAccuracy() > 0.25f);

		} catch (IOException e) {
			throw new AssertionError("Cross validation failed.", e);
		}
	}
}
