/*
	Gregory Gay (greg@greggay.com)
	Lustre.g
	Last Updated: 03/28/2014

	Lustre grammar for Antlr4
*/


grammar Lustre;  

options {
  language = Java;
}


lustre			:	(compilationUnit)* EOF;
compilationUnit		:	(constDeclaration)* nodeDeclaration nodeDefinition; 

// Node Declaration Parser Rules

constDeclaration	:	'const' IDENTIFIER ':' type '=' (simple_expr_p25) ';';
nodeDeclaration		:	'node' IDENTIFIER '(' in=nodeArgs ')' ( 'returns' ) '(' out=nodeArgs ')' ';' (constDeclaration)*  nodeVars*;
nodeVars		:	'var' nodeArgs;
nodeArgs		:	(nodeArgsHelper ':' type ('^' (shift_expression) )? ';'?)+;
nodeArgsHelper		:	IDENTIFIER
				| IDENTIFIER ',' nodeArgsHelper;
nodeDefinition		:	'let' nodeBody* 'tel' ';';
nodeBody 		:	label? v=nodeAff op='=' expression ';' | assert_expression ';' ; 
label    		:	IDENTIFIER ':';
assert_expression    	:	'assert'  expression;

nodeAff			:	IDENTIFIER vectorHelper? 
				| '(' IDENTIFIER vectorHelper? (',' IDENTIFIER vectorHelper?)+ ')';

vectorHelper		:	'[' expression ('..' expression )?  ']'; //bug present there

type			:	'bool' 
				| 'int' 
				| 'real'
				| 'subrange' '[' bound ',' bound ']' 'of' 'int';

bound			:	'-'? DECIMAL;

// Expression Parser Rules

expression		:	binary_expression;
current    		:	op='current' '(' expression ')';
binary_expression	:	if_expression  (op='->' if_expression)*;
if_expression 		: 	op='if' followBy_expression 'then' followBy_expression 'else' followBy_expression | followBy_expression ; 
followBy_expression	:	or_expression (op=('or'|'nor') or_expression)*;
or_expression		:	and_expression (op=('and'|'nand') and_expression)*;
and_expression		:	xor_expression (op=('xor'|'nxor') xor_expression)*;
xor_expression		:	equal_expression (op=('='|'<>') equal_expression)*;
equal_expression	:	compare_expression (op=('<'|'<='|'>='|'>') compare_expression)*;
compare_expression	:	shift_expression (op=('<<'|'>>') shift_expression)*;
shift_expression	:	add_expression (op=('+'|'-') add_expression)*;
add_expression		:	mul_expression (op=('*'|'/'|'div'|'mod') mul_expression)*;
mul_expression		:	when_expression (op='when' when_expression)*;
when_expression		:	unaryExpression;

unaryExpression : simple_expr_p25;

simple_expr_p25 : 
     op='-' simple_expr_p30  
   | op='pre' simple_expr_p30 
   | op='not' simple_expr_p30 
   | simple_expr_p30;

simple_expr_p30 : simple_expr_term ; 
        
simple_expr_term :
     IDENTIFIER
   | literal 
   | '(' expression ')'
   ; 

literal : DECIMAL 
        | TYPED_INT_LIT
        | REAL 
        | TYPED_REAL_LIT
        | BOOLCONSTANT;


// Expression Lexer Rules

TYPED_REAL_LIT : DECIMAL '.'DECIMAL ('s' | 'd') ; 
TYPED_INT_LIT : DECIMAL 'int' DECIMAL ; 

BOOLCONSTANT		:	'true' 
				| 'false';
DECIMAL    		:	('0'..'9')+;
REAL    		:	FloatPrefix;
IDENTIFIER		:    	( 'a'..'z' | 'A'..'Z' | '_' ) ( 'a'..'z' | 'A'..'Z' | '_' | '0'..'9' )*;
fragment FloatPrefix	:    	'0'..'9'+ ( '.' '0'..'9'+ ) ( ( 'e' | 'E' ) ( '+' | '-' )?  '0'..'9'+ )?;
COMMENT    		:	('--' | '/*')  ~( '\n' | '\r' )* NEWLINE -> channel(HIDDEN);
COMMENTBLOCK		:	'/*' .*? '*/'  -> channel(HIDDEN); 
NEWLINE			:	( '\n' | '\r' | '\r\n' )  -> channel(HIDDEN);
WS			:    	( ' ' | '\u000C' | '\t' )  -> channel(HIDDEN);
