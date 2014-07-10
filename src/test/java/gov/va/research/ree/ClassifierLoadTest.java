package gov.va.research.ree;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import gov.va.research.redcat.ClassifierLoader;
import gov.va.research.redcat.ClassifierRegEx;

public class ClassifierLoadTest {
	
	/**
	 * Test method for {@link gov.va.research.ree.ClassifierLoader#getYesRegEx()}.
	 */
	@Test
	public void testgetYesRegEx() {
		ClassifierLoader loader = new ClassifierLoader("classifier.txt");
		try {
			List<ClassifierRegEx> regYesList = loader.getYesRegEx();
			System.out.println("Yes List");
			for(ClassifierRegEx regEx : regYesList){
				System.out.println(regEx.getRegEx());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Test method for {@link gov.va.research.ree.ClassifierLoader#getNoRegEx()}.
	 */
	@Test
	public void testgetNoRegEx() {
		ClassifierLoader loader = new ClassifierLoader("classifier.txt");
		try {
			List<ClassifierRegEx> regNoList = loader.getNoRegEx();
			System.out.println("No List");
			for(ClassifierRegEx regEx : regNoList){
				System.out.println(regEx.getRegEx());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
