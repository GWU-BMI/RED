package gov.va.research.red.cat;

import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTReaderTest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RegExCollapserTest {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCollapsed(){
		Collapser colla=new Collapser();		
		Random rand = new Random();
		for(int i=0;i<100;i++){
			int a=rand.nextInt(12)+2;
			
			String caseRand="";
			for(int len=0;len<a;len++){
				int b=rand.nextInt(12)+4;
				caseRand+="[A-Z]{1,"+b+"}\\s{1,10}";
				
			}
			a=rand.nextInt(10)+5;
			caseRand+="[A-Z]{1,"+a+"}\\p{Punct}\\s{1,10}";
			
			
			String comp="(?:[A-Z]{1,1}(?:\\s{1,10}|\\p{Punct})){1,3}";
			RegEx regex=new RegEx(caseRand);
			StringBuilder sb=colla.collapse(regex);	
			String res=sb.toString();
			int j=0;
			int k=0;
			while(j<res.length()){
				if(j==11){
					k=12;
					while(true){
						try{
						  int tmp=Integer.parseInt(String.valueOf(res.charAt(j)));
						  j++;
						}
						catch(Exception ex){
							break;
						}
					}
					continue;
					
				}
				if(k==comp.length()-2){
					
						k=comp.length()-1;
						while(true){
							try{
							  int tmp=Integer.parseInt(String.valueOf(res.charAt(j)));
							  j++;
							}
							catch(Exception ex){
								break;
							}
						}
						continue;
						
					
					
				}
				
				Assert.assertEquals(res.charAt(j), comp.charAt(k));
				j++;
				k++;
			}
			
		}
		
		
		}
	
	
}
