/*	Any functionality that should be abstracted out of the main parser.
* 	Currently:
*	- Translates XOR and equivalence statements into forms that are more easily processed.
*	- Tidies up order of operations
*/
grammar Pretranslate;

options {
  language = Java;
}

@header {
	import java.util.HashMap;
	import java.util.ArrayList;
}

@members {
	String expr="";
	int count;
	ArrayList<String> print=new ArrayList<String>();
	ArrayList<String> replace=new ArrayList<String>();
	HashMap dataTypes = new HashMap();
	ArrayList<String> RHS = new ArrayList<String>();
	ArrayList<String> printQueue = new ArrayList<String>();

	//Checks for variable names as substrings of another variable name.
	// eg: I want "SET," but not "RESET"
	public Boolean testBeforeAfter(String text,String match){
		String before="";
		String after="";
		if(text.indexOf(match)>0){
			before = text.substring(text.indexOf(match)-1,text.indexOf(match));
		}
		if(text.indexOf(match)+match.length() < text.length()){
			after = text.substring(text.indexOf(match)+match.length(),text.indexOf(match)+match.length()+1);
		}

		if((!before.matches("[a-zA-Z]")&&!before.equals("_")&&!before.matches("[0-9]"))&&(!after.matches("[a-zA-Z]")&&!after.equals("_")&&!after.matches("[0-9]")))
			return true;
		else
			return false;
	}

}

lustre			:	(compilationUnit)* EOF{
					for(int count=0;count<printQueue.size();count++){
						System.out.println(printQueue.get(count));
					}
				};
compilationUnit		:	preConstDeclaration preNodeDeclaration nodeDefinition {printQueue.add("tel;");};

// Node Declaration Parser Rules

preConstDeclaration	:	constDeclaration 
				{
					if($constDeclaration.text!=null){
						printQueue.add($constDeclaration.text);
					};
				};
constDeclaration	:	('const' (IDENTIFIER) (',' IDENTIFIER)* ':' BASICTYPE ('=' (shift_expression) )? ';')*;
preNodeDeclaration	:	nodeDeclaration 
				{	printQueue.add($nodeDeclaration.text);
					printQueue.add("let");
				};		//Used for printing purposes
nodeDeclaration		:	'node' IDENTIFIER '(' nodeArgs ')' ( 'returns' ) '(' nodeArgs ')' ';' preConstDeclaration  nodeVars*;

nodeVars		:	'var' nodeArgs;
nodeArgs		:	(nodeArgsHelper ':' BASICTYPE ('^' (shift_expression) )? ';'?
				{	dataTypes.put($nodeArgsHelper.text,$BASICTYPE.text);
				})+;
nodeArgsHelper		:	IDENTIFIER
				| IDENTIFIER ',' nodeArgsHelper;

nodeDefinition		:	'let' nodeBody* 'tel' ';';
nodeBody		:	label? nodeAff '=' expression ';' 	
				{	
					if(print.isEmpty()){
						expr="\t";
						if($label.text!=null)	expr=expr+$label.text+" ";
						expr=expr+$nodeAff.text+" = "+$expression.text+";";
						printQueue.add("\n"+expr);
					}else{ //Print out modified line
						expr="\t";
						if($label.text!=null)	expr=expr+$label.text+" ";
						String replaced=$expression.text;
						for(int count=replace.size()-1;count>=0;count--){
							replaced=replaced.replace(replace.get(count),print.get(count));
						}
						expr=expr+$nodeAff.text+" = "+replaced+";";
						printQueue.add("\n"+expr);
						print=new ArrayList<String>();
						replace=new ArrayList<String>();
					}
					RHS = new ArrayList<String>();

				
				}
				| asserT ';' ; 

label    		:	IDENTIFIER ':';
asserT    		:	'assert'  expression;

nodeAff			:	IDENTIFIER vectorHelper? 
				| '(' IDENTIFIER vectorHelper? (',' IDENTIFIER vectorHelper?)+ ')';

vectorHelper		:	'[' expression ('..' expression )?  ']'; //bug present there

// Expression Parser Rules

expression		:	binary_expression;

current    		:	'current' '(' expression ')';

binary_expression	:	a=if_expression ('->' b=if_expression)*;
if_expression 		: 	'if' followBy_expression 'then' followBy_expression ('else' followBy_expression)? 
				| followBy_expression ; 
followBy_expression	:	a=or_expression ( ('or'|'nor') b=or_expression)*;
or_expression		:	a=and_expression ( ('and'|'nand') b=and_expression)*;
and_expression		:	a=xor_expression ( ('xor'|'nxor') b=xor_expression{
					if($b.text!=null){
						replace.add($a.text+" xor "+$b.text);
						print.add("(("+$a.text+" or "+$b.text+") and not ("+$a.text+" and "+$b.text+"))");
					}
				})*;
xor_expression		:	a=equal_expression ( c=('='|'<>') b=equal_expression{
					if($b.text!=null){
						String typeA="";
						String typeB="";
						for(int count=0;count<RHS.size();count++){
							if(dataTypes.get(RHS.get(count)).toString().equals("bool")){
								if($a.text.contains(RHS.get(count))&&this.testBeforeAfter($a.text,RHS.get(count))){
									typeA="bool";
								}
								if($b.text.contains(RHS.get(count))&&this.testBeforeAfter($b.text,RHS.get(count))){
									typeB="bool";
								}	
							}
						}

						if($c.text.equals("=")&&(typeA.equals("bool")&&typeB.equals("bool"))){
							replace.add($a.text+" = "+$b.text);
							print.add("(("+$a.text+" and "+$b.text+") or not ("+$a.text+" or "+$b.text+"))");
							replace.add($a.text+"= "+$b.text);
							print.add("(("+$a.text+" and "+$b.text+") or not ("+$a.text+" or "+$b.text+"))");
						replace.add($a.text+" ="+$b.text);
							print.add("(("+$a.text+" and "+$b.text+") or not ("+$a.text+" or "+$b.text+"))");
							replace.add($a.text+"="+$b.text);
							print.add("(("+$a.text+" and "+$b.text+") or not ("+$a.text+" or "+$b.text+"))");
						}else if($c.text.equals("<>") &&(typeA.equals("bool")&&typeB.equals("bool"))){
							replace.add($a.text+" <> "+$b.text);
							print.add("(("+$a.text+" or "+$b.text+") and not ("+$a.text+" and "+$b.text+"))");
							replace.add($a.text+"<> "+$b.text);
							print.add("(("+$a.text+" or "+$b.text+") and not ("+$a.text+" and "+$b.text+"))");
							replace.add($a.text+" <>"+$b.text);
							print.add("(("+$a.text+" or "+$b.text+") and not ("+$a.text+" and "+$b.text+"))");
							replace.add($a.text+"<>"+$b.text);
							print.add("(("+$a.text+" or "+$b.text+") and not ("+$a.text+" and "+$b.text+"))");
						}
					}
				})*;
equal_expression	:	compare_expression ( ('<'|'<='|'>='|'>') compare_expression)*;
compare_expression	:	shift_expression ( ('<<'|'>>') shift_expression)*;
shift_expression	:	add_expression ( ('+'|'-') add_expression)*;
add_expression		:	mul_expression ( ('*'|'/'|'div'|'%') mul_expression)*;
mul_expression		:	when_expression ('when' when_expression)*;
when_expression		:	unaryExpression;

unaryExpression : simple_expr_p25 ; 

simple_expr_p25 : 
     '-' simple_expr_p30  
   | 'pre' a=simple_expr_p30
   | 'not' a=simple_expr_p30 
   | simple_expr_p30;

simple_expr_p30 : simple_expr_term ; 
        
simple_expr_term :
     IDENTIFIER {
		//Form list of RHS variables
		if(!RHS.contains($IDENTIFIER.text)){
			RHS.add($IDENTIFIER.text);
		}
	}
   | literal 
   | '(' expression ')' 
   ; 

literal : DECIMAL 
        | TYPED_INT_LIT
        | REAL 
        | TYPED_REAL_LIT
        | BOOLCONSTANT ;


// Expression Lexer Rules

TYPED_REAL_LIT : DECIMAL '.'DECIMAL ('s' | 'd') ; 
TYPED_INT_LIT : DECIMAL 'int' DECIMAL ; 
//REAL_LIT : DIGIT+ '.' DIGIT+ ; 
//INT_LIT :   DIGIT+ ;
//BIT_INT_TYPE : 'int' DECIMAL? ; 

BASICTYPE		:	'bool' 
				| 'int' 
				| 'real';
BOOLCONSTANT		:	'true' 
				| 'false';
DECIMAL    		:	('0'..'9')+;
REAL    		:	FloatPrefix;
IDENTIFIER		:    	( 'a'..'z' | 'A'..'Z' | '_' ) ( 'a'..'z' | 'A'..'Z' | '_' | '0'..'9' )*;
fragment FloatPrefix	:    	'0'..'9'+ ( '.' '0'..'9'+ ) ( ( 'e' | 'E' ) ( '+' | '-' )?  '0'..'9'+ )?;
COMMENT    		:	('--' | '/*')  ~( '\n' | '\r' )* NEWLINE {$channel=HIDDEN;};
COMMENTBLOCK		:	'/*' ( options {greedy=false;} : . )* '*/' {$channel = HIDDEN;}; 
NEWLINE			:	( '\n' | '\r' | '\r\n' ) {$channel=HIDDEN;};
WS			:    	( ' ' | '\u000C' | '\t' ) {$channel=HIDDEN;};
