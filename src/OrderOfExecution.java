/*
	Gregory Gay (greg@greggay.com)
	OrderOfExecution
	Last Updated: 09/13/2013

	Listener that reads in a parse tree and rearranges expressions so that 
	variables are always defined before use.
*/


import java.util.HashMap;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.Stack;
import java.util.ArrayList;

public class OrderOfExecution extends LustreBaseListener {
	// Mapping of variables to values
	ArrayList<String> undefined = new ArrayList<String>();
	// Current variable being evaluated
	String currentExpression;
	// Number unresolved variables
	int numUnresolved;
	// New version of model
	ArrayList<String> newModel = new ArrayList<String>();
	// Flag for right-hand side of an arrow expression
	Boolean preFlag=false;
	// Flag so variable being defined isn't counted as unresolved
	Boolean headerFlag=true;
	// Flag to only make unresolved list once
	Boolean firstRound=true;

	@Override
	public void exitLustre(LustreParser.LustreContext ctx){
		firstRound=false;
	}

	//Populate variable list
	@Override
	public void exitNodeArgs(LustreParser.NodeArgsContext ctx){
		String variable="";
		String type="";
	
		if(firstRound==true){
			for(int child=0;child<ctx.getChildCount();child++){
				if((child+1<ctx.getChildCount())&&(ctx.getChild(child+1).getText().equals(":"))){
					variable=ctx.getChild(child).getText();
					undefined.add(variable);
				}
			}
		}
	}

	// Remove inputs from unresolved list
	@Override
	public void exitNodeDeclaration(LustreParser.NodeDeclarationContext ctx){
		String inputs = ctx.in.getText();
		String[] parts = inputs.split(";");
		
		for(int word=0;word<parts.length;word++){
			String part = parts[word].split(":")[0];
			undefined.remove(part);
		}
	}

	//Flag so that variable being defined isn't flagged
	@Override
	public void enterNodeBody(LustreParser.NodeBodyContext ctx){
		if(ctx.op.getText().equals("=")){
			headerFlag=true;	
			numUnresolved=0;
		}
	}

	//Are all variables resolved?
	@Override 
	public void exitNodeBody(LustreParser.NodeBodyContext ctx){
		if(ctx.op.getText().equals("=")){
			currentExpression=ctx.v.getText();
	
			if(numUnresolved==0 && !newModel.contains(currentExpression)){
				undefined.remove(currentExpression);	
				newModel.add(currentExpression);		
			//	System.out.println(undefined.size());
			}
				//System.out.println(ctx.getText()+"= "+numUnresolved);
		}
	}

	// Pre operator
	@Override
	public void enterSimple_expr_p25(LustreParser.Simple_expr_p25Context ctx){
		if(ctx.getChildCount() == 2){
			if(ctx.op.getText().equals("pre")){
				preFlag=true;
			}
		}
	}

	// Pre operator
	@Override
	public void exitSimple_expr_p25(LustreParser.Simple_expr_p25Context ctx){
		if(ctx.getChildCount() == 2){
			if(ctx.op.getText().equals("pre")){
				preFlag=false;
			}
		}
	}

	// Get literal values
	@Override
        public void visitTerminal(TerminalNode node) {
            	if(undefined.contains(node.getText())&&preFlag==false&&headerFlag==false){	
			numUnresolved++;
		}
		headerFlag=false;
        }

	@Override
	public void enterLustre(LustreParser.LustreContext ctx){
		numUnresolved=0;
	}


	//Helper functions
	public ArrayList<String> getResolved(){
		return newModel;
	}
	
	public ArrayList<String> getUnresolved(){
		return undefined;
	}
}
