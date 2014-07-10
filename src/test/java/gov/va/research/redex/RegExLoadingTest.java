package gov.va.research.redex;

import gov.va.research.redex.ExtractorLoader;
import gov.va.research.redex.LSTriplet;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

public class RegExLoadingTest {
	
	
	/**
	 * Test method for {@link gov.va.research.redex.ExtractorLoader#getRegularExpressions()}.
	 */
	@Test
	public void testgetRegularExpressions() {
		ExtractorLoader loader = new ExtractorLoader("ree.txt");
		try {
			List<LSTriplet> listOfTriplets = loader.getRegularExpressions();
			for(LSTriplet triplet : listOfTriplets){
				System.out.println(triplet.toStringRegEx());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
