/*******************************************************************************
 * Copyright (c) 2018 itemis AG and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Tamas Miklossy (itemis AG) - initial API and implementation
 * 
 *******************************************************************************/
package org.eclipse.gef.dot.tests

import javax.inject.Inject
import javax.inject.Named
import org.eclipse.gef.dot.internal.language.DotHtmlLabelUiInjectorProvider
import org.eclipse.xtext.junit4.InjectWith
import org.eclipse.xtext.junit4.XtextRunner
import org.eclipse.xtext.parser.antlr.Lexer
import org.eclipse.xtext.ui.LexerUIBindings
import org.junit.Test
import org.junit.runner.RunWith

import static extension org.eclipse.gef.dot.tests.DotTestUtils.lex
import static extension org.junit.Assert.*

@RunWith(XtextRunner)
@InjectWith(DotHtmlLabelUiInjectorProvider)
class DotHtmlLabelHighlightingLexerTests {
	
	@Inject @Named(LexerUIBindings.HIGHLIGHTING) Lexer lexer
	
	@Test(timeout = 2000)
	def void testLexing01() {
		'''<a b="'''
		.assertLexing(
		'''
		RULE_TAG_START '<'
		RULE_ID 'a'
		RULE_WS ' '
		RULE_ID 'b'
		RULE_ASSIGN '='
		0 '"'
		''')
	}
		
	@Test(timeout = 2000)
	def void testLexing02() {
		'''<TABLE ALIGN="'''
		.assertLexing(
		'''
		RULE_TAG_START '<'
		RULE_ID 'TABLE'
		RULE_WS ' '
		RULE_ID 'ALIGN'
		RULE_ASSIGN '='
		0 '"'
		''')
	}
	
	@Test(timeout = 2000)
	def void testLexing03() {
		'''<TABLE ALIGN=""></TABLE>'''
		.assertLexing(
		'''
		RULE_TAG_START '<'
		RULE_ID 'TABLE'
		RULE_WS ' '
		RULE_ID 'ALIGN'
		RULE_ASSIGN '='
		RULE_ATTR_VALUE '""'
		RULE_TAG_END '>'
		RULE_TAG_START_CLOSE '</'
		RULE_ID 'TABLE'
		RULE_TAG_END '>'
		''')
	}
	
	private def assertLexing(CharSequence modelAsText, CharSequence expected) {
		val antlrTokenFilePath = "../org.eclipse.gef.dot.ui/src-gen/org/eclipse/gef/dot/internal/ui/language/highlighting/antlr/lexer/CustomHighlightingInternalDotHtmlLabelLexer.tokens";
		val actual = lexer.lex(antlrTokenFilePath, modelAsText)
		expected.toString.trim.assertEquals(actual.toString.trim)
	}
	
}