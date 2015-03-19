/*
 *  Copyright 2015 United States Department of Veterans Affairs
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */
package gov.va.research.red.ex;

import static org.junit.Assert.*;
import gov.va.research.red.Token;
import gov.va.research.red.TokenType;
import gov.va.research.red.Tokenizer;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author vhaislreddd
 *
 */
public class TokenizerTest {

	private static final String INSTRUCTIONS_OF_SHURUPPAK =
			"In those days, in those far remote times,\r\n"
			+ "in those nights, in those faraway nights,\n"
			+ "in those years, in those far remote years.";
	private static final Token SINGLE_SPACE_TOKEN = new Token(" ", TokenType.WHITESPACE);
	private static final Token COMMA_TOKEN = new Token(",", TokenType.PUNCTUATION);
	
	private static final List<Token> tokenList = new ArrayList<>();
	
	static {
		tokenList.add(new Token("in", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("those", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("days", TokenType.WORD));
		tokenList.add(COMMA_TOKEN);
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("in", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("those", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("far", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("remote", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("times", TokenType.WORD));
		tokenList.add(COMMA_TOKEN);
		tokenList.add(new Token("\r\n", TokenType.WHITESPACE));
		tokenList.add(new Token("in", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("those", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("nights", TokenType.WORD));
		tokenList.add(COMMA_TOKEN);
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("in", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("those", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("faraway", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("nights", TokenType.WORD));
		tokenList.add(COMMA_TOKEN);
		tokenList.add(new Token("\n", TokenType.WHITESPACE));
		tokenList.add(new Token("in", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("those", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("years", TokenType.WORD));
		tokenList.add(COMMA_TOKEN);
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("in", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("those", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("far", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("remote", TokenType.WORD));
		tokenList.add(SINGLE_SPACE_TOKEN);
		tokenList.add(new Token("years", TokenType.WORD));
		tokenList.add(new Token(".", TokenType.PUNCTUATION));
	}
	
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

	/**
	 * Test method for {@link gov.va.research.red.Tokenizer#tokenize(java.lang.String)}.
	 */
	@Test
	public void testTokenize() {
		List<Token> tokens = Tokenizer.tokenize(INSTRUCTIONS_OF_SHURUPPAK);
		Assert.assertEquals(tokenList.size(), tokens.size());
		for (int i = 0; i < tokenList.size(); i++) {
			Assert.assertEquals(tokenList.get(i), tokens.get(i));
		}
	}

}
