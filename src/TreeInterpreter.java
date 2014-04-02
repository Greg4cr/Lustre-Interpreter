/*
	Gregory Gay (greg@greggay.com)
	TreeInterpreter
	Last Updated: 4/02/2014 
		(Interpreter can now resume execution from the end of an existing trace)

	Listener that performs actions on the parse tree derived from a Lustre model.
*/


import java.util.HashMap;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.Stack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.TreeSet;
import java.io.BufferedReader;
import java.io.FileReader;

public class TreeInterpreter extends LustreBaseListener {
	// Mapping of variables to values
	ArrayList<String> varList = new ArrayList<String>();
	ArrayList<String> oracle = new ArrayList<String>();
	HashMap<String,String> valMap = new HashMap<String,String>();
	HashMap<String,String> preMap = new HashMap<String,String>();
	ArrayList<String> expressionOrder = new ArrayList<String>();
	// Mapping of types to variables
	HashMap<String,String> typeMap = new HashMap<String,String>();
	// Collection of tags
	HashMap<String,LazyConditionLocationSet> tagSet = new HashMap<String, LazyConditionLocationSet>();
	HashMap<String,LazyConditionLocationSet> preTagSet = new HashMap<String, LazyConditionLocationSet>();
	Stack<HashSet<ConditionLocation>> tagStack = new Stack<HashSet<ConditionLocation>>();
	// Used variables (to keep track of integers used in purely mathematical operations, as they are outside of tag system
	HashMap<String,ArrayList<String>> usedVars = new HashMap<String, ArrayList<String>>();
	Boolean expFlag=false;
	// Do the same for tags coming from pre(X) expressions
	HashMap<String,ArrayList<String>> seqUsedVars = new HashMap<String, ArrayList<String>>();
	// Keep track of test obligation satisfaction.
	HashMap<String,Boolean> obligationMatrix = new HashMap<String,Boolean>();
	// Current variable being evaluated
	String currentExpression;
	// Stack of result values
	Stack<String> stack = new Stack<String>();
	// Current simulation round
	int round = 0;
	// Current Decision
	int decision=0;
	// Model name
	String model;
	// Tracking OMC/DC 
	Boolean omcdcFlag=false;
	// Was a custom oracle set?
	Boolean customOracle = false;
	int total=0;
	// Are we within a pre
	Boolean preFlag=false;
	// Are we resuming from a partial execution?
	int partialFlag=0;

	//Populate variable type map
	@Override
	public void exitNodeArgs(LustreParser.NodeArgsContext ctx){
		String variable="";
		String type="";

		if(round==1 || partialFlag>0){
			for(int child=0;child<ctx.getChildCount();child++){
				if((child+1<ctx.getChildCount())&&(ctx.getChild(child+1).getText().equals(":"))){
					variable=ctx.getChild(child).getText();
				}else if((child-1>0)&&(ctx.getChild(child-1).getText().equals(":"))){
					type=ctx.getChild(child).getText();
					typeMap.put(variable,type);
					if(!varList.contains(variable) && !customOracle){
						varList.add(variable);
					}
					// Replace 1/0 inputs with true/false
					if(type.equals("bool")&&valMap.containsKey(variable)){
						if(valMap.get(variable).equals("1")){
							valMap.put(variable,"true");
						}else if(valMap.get(variable).equals("0")){
							valMap.put(variable,"false");
						}
					}
					if(type.equals("bool")&&preMap.containsKey(variable)){
						if(preMap.get(variable).equals("1")){
							preMap.put(variable,"true");
						}else if(preMap.get(variable).equals("0")){
							preMap.put(variable,"false");
						}
					}
					variable="";
					type="";
				}
			}
		}else{
			for(String v : valMap.keySet()){
				if(typeMap.get(v).equals("bool")){
					if(valMap.get(v).equals("1")){
						valMap.put(v,"true");
					}else if(valMap.get(v).equals("0")){
						valMap.put(v,"false");
					}
				}
			}	
		}
	}

	// Deal with constants
	@Override
	public void enterConstDeclaration(LustreParser.ConstDeclarationContext ctx){
		currentExpression = ctx.getChild(1).getText();
		String type = ctx.getChild(3).getText();
		String value = ctx.getChild(5).getText();
		if(type.equals("bool")){
			if(value.equals("1")){
				value="true";
			}else if(value.equals("0")){
				value="false";
			}
		}

		valMap.put(currentExpression,value);
		typeMap.put(currentExpression,type);
		currentExpression = "";
	}

	//Reset counters
	@Override
	public void enterNodeBody(LustreParser.NodeBodyContext ctx){
		if(ctx.op.getText().equals("=")){
			currentExpression=ctx.v.getText();	
			decision=0;
			expFlag=false;
			tagStack=new Stack<HashSet<ConditionLocation>>();
			//System.out.println(currentExpression);
		}
	}

	//Populate val map with EXPVAR = EXPRESULT
	@Override 
	public void exitNodeBody(LustreParser.NodeBodyContext ctx){
		if(ctx.op.getText().equals("=")){
			String result = stack.pop();	
			//System.out.println(currentExpression+"="+result);

			// All math done as doubles, so convert to int if it's supposed to be int. 
			if(typeMap.get(currentExpression).equals("int") || typeMap.get(currentExpression).contains("subrange")){
				try{
					int intermediate = (int) Math.round(Double.parseDouble(result));
					result=Integer.toString(intermediate);
				}catch(Exception e){
					//System.out.println(currentExpression+"="+result);
				}
			}

			valMap.put(currentExpression,result);
			if(omcdcFlag==true){
				//Create collection of tags
				LazyConditionLocationSet tags = new LazyConditionLocationSet();
		
				while(!tagStack.isEmpty()){
					HashSet<ConditionLocation> rs = tagStack.pop();
					tags.addTagSet(rs);
				}

				//Add to big list of tag sets
				tagSet.put(currentExpression,tags);
			}

			if(round==1 || partialFlag>0){
				expressionOrder.add(currentExpression);
			}

			expFlag=true;
		}
	}

	// Arrow evaluation
	@Override
	public void exitBinary_expression(LustreParser.Binary_expressionContext ctx){
		if(ctx.getChildCount()==3 && ctx.op.getText().equals("->")){
			String right =stack.pop();
			String left=stack.pop();

			HashSet<ConditionLocation> rightTags = tagStack.pop();
			HashSet<ConditionLocation> leftTags = tagStack.pop();
			HashSet<ConditionLocation> propogate = new HashSet<ConditionLocation>();
			// If the first round, we only propogate results and tags on the left of the arrow
			if(round==1){
				stack.push(left);
				if(omcdcFlag==true){
					propogate.addAll(leftTags);
					tagStack.push(propogate);

					if(usedVars.containsKey(currentExpression)){
						String[] words = ctx.getChild(2).toStringTree().replaceAll("[()]","").split(" ");
						ArrayList<String> remains = usedVars.get(currentExpression);
						for(String part: words){
							if(remains.contains(part)){
								remains.remove(part);
							}
						}
						usedVars.put(currentExpression,remains);	
					}
				}

			// On subsequent rounds, only propogate tags and results from right of the arrow
			}else if(round>1){
				stack.push(right);
				if(omcdcFlag==true){
					propogate.addAll(rightTags);
					tagStack.push(propogate);
		
					if(usedVars.containsKey(currentExpression)){
						String[] words = ctx.getChild(0).toStringTree().replaceAll("[()]","").split(" ");
						ArrayList<String> remains = usedVars.get(currentExpression);
						for(String part: words){
							if(remains.contains(part)){
								remains.remove(part);
							}
						}
						usedVars.put(currentExpression,remains);
					}
				}
			}
		}
	}

	// If statement is a decision
	@Override
	public void enterIf_expression(LustreParser.If_expressionContext ctx){
		if(ctx.getChildCount() == 6 && ctx.op.getText().equals("if")){
			decision++;
		}
	}

	// IF-THEN-ELSE evaluation
	@Override
	public void exitIf_expression(LustreParser.If_expressionContext ctx){
		if(ctx.getChildCount() == 6 && ctx.op.getText().equals("if")){
			String elsePart = stack.pop();
			String thenPart = stack.pop();
			String ifPart= stack.pop();

			if(Boolean.parseBoolean(ifPart)==true){
				stack.push(thenPart);
			}else{
				stack.push(elsePart);
			}

			if(omcdcFlag==true){
				HashSet<ConditionLocation> rightTags = tagStack.pop();
				HashSet<ConditionLocation> leftTags = tagStack.pop();
				HashSet<ConditionLocation> condTags = tagStack.pop();

				HashSet<ConditionLocation> propogate = new HashSet<ConditionLocation>();
				// Only propogage tags from conditional and then branches if condition is true
				if(Boolean.parseBoolean(ifPart)==true){
					propogate.addAll(condTags);
					propogate.addAll(leftTags);
					tagStack.push(propogate);

					if(usedVars.containsKey(currentExpression)){
						String[] words = ctx.getChild(5).toStringTree().replaceAll("[()]","").split(" ");
						ArrayList<String> remains = usedVars.get(currentExpression);
						for(String part: words){
							if(remains.contains(part)){
								remains.remove(part);
							}
						}
						usedVars.put(currentExpression,remains);	
					}
				// Otherwise, propogate tags from conditional and false branches
				}else{
					propogate.addAll(condTags);
					propogate.addAll(rightTags);
					tagStack.push(propogate);

					if(usedVars.containsKey(currentExpression)){
						String[] words = ctx.getChild(3).toStringTree().replaceAll("[()]","").split(" ");
						ArrayList<String> remains = usedVars.get(currentExpression);
						for(String part: words){
							if(remains.contains(part)){
								remains.remove(part);
							}
						}
						usedVars.put(currentExpression,remains);	
					}

				}
			}
		}
	}

	// OR expressions are decisions
	@Override
	public void enterFollowBy_expression(LustreParser.FollowBy_expressionContext ctx){
		if(ctx.getChildCount() == 3 && ctx.op.getText().equals("or")){
			decision++;
		}
	}

	// OR/NOR Comparisons
	@Override
	public void exitFollowBy_expression(LustreParser.FollowBy_expressionContext ctx) {
		if(ctx.getChildCount() == 3){
			if (ctx.op.getText().equals("or")){
				// OR
				Boolean right = Boolean.parseBoolean(stack.pop());
				Boolean left = Boolean.parseBoolean(stack.pop());
				stack.push(Boolean.toString(left || right));

				if(omcdcFlag==true){
					HashSet<ConditionLocation> rightTags = tagStack.pop();
					HashSet<ConditionLocation> leftTags = tagStack.pop();

					HashSet<ConditionLocation> propogate = new HashSet<ConditionLocation>();

					// If they're both false, everything propogates
					if(left==false && right==false){
						propogate.addAll(leftTags);
						propogate.addAll(rightTags);
						tagStack.push(propogate);
					// If the right is false, only the left propogates
					}else if(left==true && right==false){
						propogate.addAll(leftTags);
						tagStack.push(propogate);

						if(usedVars.containsKey(currentExpression)){
							String[] words = ctx.getChild(2).toStringTree().replaceAll("[()]","").split(" ");
							ArrayList<String> remains = usedVars.get(currentExpression);
							for(String part: words){
								if(remains.contains(part)){
									remains.remove(part);
								}
							}
							usedVars.put(currentExpression,remains);
						}	
					// If the left is false, only the right propogates
					}else if(left==false && right==true){
						propogate.addAll(rightTags);
						tagStack.push(propogate);
			
						if(usedVars.containsKey(currentExpression)){
							String[] words = ctx.getChild(0).toStringTree().replaceAll("[()]","").split(" ");
							ArrayList<String> remains = usedVars.get(currentExpression);
							for(String part: words){
								if(remains.contains(part)){
									remains.remove(part);
								}
							}
							usedVars.put(currentExpression,remains);	
						}
					// If both are true, nothing propogates
					}else if(left==true && right==true){
						tagStack.push(propogate);

						if(usedVars.containsKey(currentExpression)){
							usedVars.put(currentExpression,new ArrayList<String>());	
						}
					}
				}

			}else if(ctx.op.getText().equals("nor")){
				// NOR
				Boolean right = Boolean.parseBoolean(stack.pop());
				Boolean left = Boolean.parseBoolean(stack.pop());
				stack.push(Boolean.toString(!(left || right)));
			}
		}
	}	

	// AND expressions are decisions
	@Override
	public void enterOr_expression(LustreParser.Or_expressionContext ctx){
		if(ctx.getChildCount() == 3 && ctx.op.getText().equals("and")){
			decision++;
		}
	}

	// AND/NAND Comparisons
	@Override
	public void exitOr_expression(LustreParser.Or_expressionContext ctx) {
		if(ctx.getChildCount() == 3){
			if (ctx.op.getText().equals("and")){
				// AND
				Boolean right = Boolean.parseBoolean(stack.pop());
				Boolean left = Boolean.parseBoolean(stack.pop());
				stack.push(Boolean.toString(left && right));

				if(omcdcFlag==true){

					HashSet<ConditionLocation> rightTags = tagStack.pop();
					HashSet<ConditionLocation> leftTags = tagStack.pop();

					HashSet<ConditionLocation> propogate = new HashSet<ConditionLocation>();

					// If both are true, everything propogates
					if(left==true && right==true){
						propogate.addAll(leftTags);
						propogate.addAll(rightTags);
						tagStack.push(propogate);
					// If the right is false and left is true, right propogates
					}else if(left==true && right==false){
						propogate.addAll(rightTags);
						tagStack.push(propogate);
						if(usedVars.containsKey(currentExpression)){
							String[] words = ctx.getChild(0).toStringTree().replaceAll("[()]","").split(" ");
							ArrayList<String> remains = usedVars.get(currentExpression);
							for(String part: words){
								if(remains.contains(part)){
									remains.remove(part);
								}
							}
							usedVars.put(currentExpression,remains);	
						}
					// If the left is false and the right is true, left propogates
					}else if(left==false && right==true){
						propogate.addAll(leftTags);
						tagStack.push(propogate);
						if(usedVars.containsKey(currentExpression)){
							String[] words = ctx.getChild(2).toStringTree().replaceAll("[()]","").split(" ");
							ArrayList<String> remains = usedVars.get(currentExpression);
							for(String part: words){
								if(remains.contains(part)){
									remains.remove(part);
								}
							}
							usedVars.put(currentExpression,remains);	
						}
					// If both are false, nothing propogates
					}else if(left==false && right==false){
						tagStack.push(propogate);
						if(usedVars.containsKey(currentExpression)){
							usedVars.put(currentExpression,new ArrayList<String>());	
						}
					}
				}				

			}else if(ctx.op.getText().equals("nand")){
				// NAND
				Boolean right = Boolean.parseBoolean(stack.pop());
				Boolean left = Boolean.parseBoolean(stack.pop());
				stack.push(Boolean.toString(!(left && right)));
			}
		}
	}	

	// XOR/NXOR Comparisons
	@Override
	public void exitAnd_expression(LustreParser.And_expressionContext ctx) {
		if(ctx.getChildCount() == 3){
			if (ctx.op.getText().equals("xor")){
				// XOR
				Boolean right = Boolean.parseBoolean(stack.pop());
				Boolean left = Boolean.parseBoolean(stack.pop());
				stack.push(Boolean.toString((left && !right)||(!left && right)));

			}else if(ctx.op.getText().equals("nxor")){
				// NXOR
				Boolean right = Boolean.parseBoolean(stack.pop());
				Boolean left = Boolean.parseBoolean(stack.pop());
				stack.push(Boolean.toString(!((left && !right)||(!left && right))));
			}
		}
	}

	@Override
	public void enterXor_expression(LustreParser.Xor_expressionContext ctx){
		if(ctx.getChildCount() == 3 && (ctx.op.getText().equals("=") || ctx.op.getText().equals("<>"))){
			expFlag=true;
		}
	}


	// Equality Comparisons
	@Override
	public void exitXor_expression(LustreParser.Xor_expressionContext ctx) {
		if(ctx.getChildCount() == 3 && stack.size()>=2){
			if (ctx.op.getText().equals("=")){
				//Boolean equality
				String right=stack.pop();
				String left=stack.pop();

				if((right.equals("true") || right.equals("false"))&&(left.equals("true") || left.equals("false"))){
					Boolean r = Boolean.parseBoolean(right);
					Boolean l = Boolean.parseBoolean(left);
					stack.push(Boolean.toString(l == r));

				}else if(isNumeric(left)&&isNumeric(right)){
					double r = Double.parseDouble(right);
					double l = Double.parseDouble(left);
					stack.push(Boolean.toString(l == r));
					
					if(omcdcFlag==true){
						// Numeric equality is a condition (boolean is a decision, has been pretranslated into an easier format)
						ConditionLocation tag = new ConditionLocation(model,currentExpression,decision,ctx.getText(),true);
						ConditionLocation tag2 = new ConditionLocation(model,currentExpression,decision,ctx.getText(),false);
						
						if(!obligationMatrix.containsKey(tag.toString())){
							obligationMatrix.put(tag.toString(),false);
						}
						if(!obligationMatrix.containsKey(tag2.toString())){
							obligationMatrix.put(tag2.toString(),false);
						}

						HashSet<ConditionLocation> ts = new HashSet<ConditionLocation>();
						// Add obligations to matrix
						if(l == r){
							if(obligationMatrix.get(tag.toString())!=true){
								ts.add(tag);
							}
						}else{
							if(obligationMatrix.get(tag2.toString())!=true){
								ts.add(tag2);
							}
						}	
						tagStack.pop();
						tagStack.pop();
						tagStack.push(ts);
					}
				}else{
					stack.push("false");
				}

			}else if(ctx.op.getText().equals("<>")){
				//Boolean inequality
				String right=stack.pop();
				String left=stack.pop();

				if((right.equals("true") || right.equals("false"))&&(left.equals("true") || left.equals("false"))){
					Boolean r = Boolean.parseBoolean(right);
					Boolean l = Boolean.parseBoolean(left);
					stack.push(Boolean.toString(l != r));	
				}else if(isNumeric(left)&&isNumeric(right)){
					double r = Double.parseDouble(right);
					double l = Double.parseDouble(left);
					stack.push(Boolean.toString(l != r));
					
					if(omcdcFlag==true){
						// Numeric inequality is a condition (boolean is a decision, has been pretranslated into an easier format)
						ConditionLocation tag = new ConditionLocation(model,currentExpression,decision,ctx.getText(),true);
						ConditionLocation tag2 = new ConditionLocation(model,currentExpression,decision,ctx.getText(),false);
						if(!obligationMatrix.containsKey(tag.toString())){
							obligationMatrix.put(tag.toString(),false);
						}
						if(!obligationMatrix.containsKey(tag2.toString())){
							obligationMatrix.put(tag2.toString(),false);
						}

						HashSet<ConditionLocation> ts = new HashSet<ConditionLocation>();
						if(l != r){
							if(obligationMatrix.get(tag.toString())!=true){
								ts.add(tag);
							}
						}else{
							if(obligationMatrix.get(tag2.toString())!=true){
								ts.add(tag2);
							}
						}
						tagStack.pop();
						tagStack.pop();
						tagStack.push(ts);
					}
				}else{
					stack.push("false");
				}
			}

			expFlag=false;
		}
	}

	@Override
	public void enterEqual_expression(LustreParser.Equal_expressionContext ctx){
		if(ctx.getChildCount() == 3 && (ctx.op.getText().equals("<") || ctx.op.getText().equals("<=") || ctx.op.getText().equals(">") || ctx.op.getText().equals(">="))){
			expFlag=true;
		}
	}

	// GT/LT Comparisons
	@Override
	public void exitEqual_expression(LustreParser.Equal_expressionContext ctx) {
		if(ctx.getChildCount() == 3){
			if (ctx.op.getText().equals("<")){
				//Less than
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Boolean.toString(left < right));
		
				if(omcdcFlag==true){
					ConditionLocation tag = new ConditionLocation(model,currentExpression,decision,ctx.getText(),true);
					ConditionLocation tag2 = new ConditionLocation(model,currentExpression,decision,ctx.getText(),false);
					if(!obligationMatrix.containsKey(tag.toString())){
						obligationMatrix.put(tag.toString(),false);
					}
					if(!obligationMatrix.containsKey(tag2.toString())){
						obligationMatrix.put(tag2.toString(),false);
					}

					HashSet<ConditionLocation> ts = new HashSet<ConditionLocation>();
					if(left < right){	
						if(obligationMatrix.get(tag.toString())!=true){
							ts.add(tag);
						}
					}else{	
						if(obligationMatrix.get(tag2.toString())!=true){
							ts.add(tag2);
						}
					}	
					tagStack.pop();
					tagStack.pop();
					tagStack.push(ts);
				}

			}else if(ctx.op.getText().equals("<=")){
				//Less than or equal to
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Boolean.toString(left <= right));

				if(omcdcFlag==true){
					ConditionLocation tag = new ConditionLocation(model,currentExpression,decision,ctx.getText(),true);
					ConditionLocation tag2 = new ConditionLocation(model,currentExpression,decision,ctx.getText(),false);
					if(!obligationMatrix.containsKey(tag.toString())){
						obligationMatrix.put(tag.toString(),false);
					}
					if(!obligationMatrix.containsKey(tag2.toString())){
						obligationMatrix.put(tag2.toString(),false);
					}

					HashSet<ConditionLocation> ts = new HashSet<ConditionLocation>();
					if(left <= right){
						if(obligationMatrix.get(tag.toString())!=true){
							ts.add(tag);
						}
					}else{
						
						if(obligationMatrix.get(tag2.toString())!=true){
							ts.add(tag2);
						}
					}
					tagStack.pop();
					tagStack.pop();
					tagStack.push(ts);
				}

			}else if(ctx.op.getText().equals(">=")){
				//Greater than or equal to
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Boolean.toString(left >= right));
			
				if(omcdcFlag==true){
					ConditionLocation tag = new ConditionLocation(model,currentExpression,decision,ctx.getText(),true);
					ConditionLocation tag2 = new ConditionLocation(model,currentExpression,decision,ctx.getText(),false);
					if(!obligationMatrix.containsKey(tag.toString())){
						obligationMatrix.put(tag.toString(),false);
					}
					if(!obligationMatrix.containsKey(tag2.toString())){
						obligationMatrix.put(tag2.toString(),false);
					}

					HashSet<ConditionLocation> ts = new HashSet<ConditionLocation>();
					if(left >= right){
						if(obligationMatrix.get(tag.toString())!=true){
							ts.add(tag);
						}
					}else{
						if(obligationMatrix.get(tag2.toString())!=true){
							ts.add(tag2);
						}
					}	
					tagStack.pop();
					tagStack.pop();
					tagStack.push(ts);
				}

			}else if(ctx.op.getText().equals(">")){
				//Greater than
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Boolean.toString(left > right));

				if(omcdcFlag==true){
					ConditionLocation tag = new ConditionLocation(model,currentExpression,decision,ctx.getText(),true);
					ConditionLocation tag2 = new ConditionLocation(model,currentExpression,decision,ctx.getText(),false);
					if(!obligationMatrix.containsKey(tag.toString())){
						obligationMatrix.put(tag.toString(),false);
					}
					if(!obligationMatrix.containsKey(tag2.toString())){
						obligationMatrix.put(tag2.toString(),false);
					}

					HashSet<ConditionLocation> ts = new HashSet<ConditionLocation>();
					if(left > right){
						if(obligationMatrix.get(tag.toString())!=true){
							ts.add(tag);
						}
					}else{
						
						if(obligationMatrix.get(tag2.toString())!=true){
							ts.add(tag2);
						}
					}	
					tagStack.pop();
					tagStack.pop();
					tagStack.push(ts);

				}
			}
			expFlag=false;
		}
	}

	// Addition and subtraction
	@Override
	public void exitShift_expression(LustreParser.Shift_expressionContext ctx) {
		if (ctx.getChildCount() == 3){
        		if (ctx.op.getText().equals("+")){
				//Addition
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Double.toString(left+right));
			}else if(ctx.op.getText().equals("-")){
				//Subtraction
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Double.toString(left-right));
			}
		}
	}

	// Multiplication, division, and modulo
	@Override
	public void exitAdd_expression(LustreParser.Add_expressionContext ctx){
		if (ctx.getChildCount() == 3){
			if(ctx.op.getText().equals("*")){
				//Multiplication
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Double.toString(left*right));
			}else if(ctx.op.getText().equals("/")||ctx.op.getText().equals("div")){
				//Division
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Double.toString(left/right));
			}else if(ctx.op.getText().equals("mod")){
				//Modulo
				double right = Double.parseDouble(stack.pop());
				double left = Double.parseDouble(stack.pop());
				stack.push(Double.toString(left % right));
			}
		}
	}

	@Override
	public void enterSimple_expr_p25(LustreParser.Simple_expr_p25Context ctx){
		if(ctx.getChildCount() == 2 && ctx.op.getText().equals("pre")){
			expFlag=true;
			preFlag=true;
		}
	}

	// Pre/Not/Negative Transforms
	@Override
	public void exitSimple_expr_p25(LustreParser.Simple_expr_p25Context ctx){
		if(ctx.getChildCount() == 2){
			if(ctx.op.getText().equals("-")){
				// Negative
				String right = stack.pop();
				if(isNumeric(right)){
					stack.push("-"+right);
				}
			}else if(ctx.op.getText().equals("pre")){
				// Pre
				expFlag=false;
				preFlag=false;
				if(round > 1){
					if(omcdcFlag==true){
						tagStack.pop();
						tagStack.push(new HashSet<ConditionLocation>()); 
						//get rid of tags for current state of this variable
						//but, let's get the tags that propogate through the delay
						ArrayList<String> preVars = new ArrayList<String>(); 
						if(seqUsedVars.containsKey(currentExpression)){
							preVars = seqUsedVars.get(currentExpression);
						}
						preVars.add(ctx.getChild(1).getText());
						seqUsedVars.put(currentExpression,preVars);
						// Still, add an obligation if it's a boolean (we'll deal with tagging for this later)
						if(typeMap.containsKey(ctx.getChild(1).getText())&&typeMap.get(ctx.getChild(1).getText()).equals("bool")){
							ConditionLocation tag = new ConditionLocation(model,currentExpression,-1,ctx.getChild(1).getText(),true);
							ConditionLocation tag2 = new ConditionLocation(model,currentExpression,-1,ctx.getChild(1).getText(),false);
							if(!obligationMatrix.containsKey(tag.toString())){
								obligationMatrix.put(tag.toString(),false);
							}
							if(!obligationMatrix.containsKey(tag2.toString())){
								obligationMatrix.put(tag2.toString(),false);
							}
						}
					}
				}
			}else if(ctx.op.getText().equals("not")){
				// Not
				String right = stack.pop();
				if(right.equals("true")||right.equals("false")){
					Boolean r = Boolean.parseBoolean(right);
					stack.push(Boolean.toString(!r));
				}
			}
		}
	}

	// Get literal values
	@Override
        public void visitTerminal(TerminalNode node) {
		String value="";
		Token symbol = node.getSymbol();
		HashSet<ConditionLocation> ts = new HashSet<ConditionLocation>();
	
		// Terminal is a variable name	
		if(preFlag==true){
			if(preMap.containsKey(symbol.getText())){
				value=preMap.get(symbol.getText());
			}else if(symbol.getType()==LustreParser.DECIMAL || symbol.getType()==LustreParser.REAL){
				value=symbol.getText();
			}else if(symbol.getType()==LustreParser.BOOLCONSTANT){
                		value=symbol.getText();
			}else if(typeMap.containsKey(symbol.getText())){	
				if(typeMap.get(symbol.getText()).equals("bool")){
					value="false"; //Unresolved variables, default value that shouldn't end up getting used
				}else{
					value="0";
				}	
			}
		
		}else if(valMap.containsKey(symbol.getText())){
			value=valMap.get(symbol.getText());

			if(!symbol.getText().equals(currentExpression)){
				//Add a condition if it's a boolean
				if(typeMap.containsKey(symbol.getText())&&typeMap.get(symbol.getText()).equals("bool")&&expFlag==false&&omcdcFlag==true){
					ConditionLocation tag = new ConditionLocation(model,currentExpression,decision,symbol.getText(),true);
					ConditionLocation tag2 = new ConditionLocation(model,currentExpression,decision,symbol.getText(),false);
					if(!obligationMatrix.containsKey(tag.toString())){
						obligationMatrix.put(tag.toString(),false);
					}
					if(!obligationMatrix.containsKey(tag2.toString())){
						obligationMatrix.put(tag2.toString(),false);
					}

					if(value.equals("true")){
						if(obligationMatrix.get(tag.toString())!=true){
							ts.add(tag);
						}else{
							ArrayList<String> refVars = new ArrayList<String>();
							if(usedVars.containsKey(currentExpression)){
								refVars=usedVars.get(currentExpression);
							}
							refVars.add(symbol.getText());
							usedVars.put(currentExpression,refVars);
						}
					}else if(value.equals("false")){

						if(obligationMatrix.get(tag2.toString())!=true){
							ts.add(tag2);
						}else{
							ArrayList<String> refVars = new ArrayList<String>();
							if(usedVars.containsKey(currentExpression)){
								refVars=usedVars.get(currentExpression);
							}
							refVars.add(symbol.getText());
							usedVars.put(currentExpression,refVars);
						}
					}	
				}
				//If it's an int and not part of an expression, add it to the used variables list to propogate tags
				if(typeMap.containsKey(symbol.getText())&&(typeMap.get(symbol.getText()).equals("int")||typeMap.get(symbol.getText()).equals("real")||typeMap.get(symbol.getText()).contains("subrange"))&&omcdcFlag==true){
					ArrayList<String> intVars = new ArrayList<String>();
					if(usedVars.containsKey(currentExpression)){
						intVars = usedVars.get(currentExpression);
					}
					intVars.add(symbol.getText());
					usedVars.put(currentExpression,intVars);
				}
			}
		// If it's a number, value = the terminal
		}else if(symbol.getType()==LustreParser.DECIMAL || symbol.getType()==LustreParser.REAL){
			value=symbol.getText();
		// Same if it's a boolean
		}else if(symbol.getType()==LustreParser.BOOLCONSTANT){
                	value=symbol.getText();
		// If it's unresolved (generally just pre (x) before we grab the previous value on the exit of the pre rule)
            	}else if(typeMap.containsKey(symbol.getText())){
			value=symbol.getText()+"(unresolved)"; //Unresolved variables, generally should just be RHS of -> expressions
		}

		if(value!=""){
			stack.push(value);
			tagStack.push(ts);
		}
        }

	// Print values out
	@Override
	public void exitLustre(LustreParser.LustreContext ctx){
		if(omcdcFlag==false){
			String out="";
			String header="";
			for(int where=0;where<varList.size();where++){
				if(round==1){
					header=header+varList.get(where)+",";
				}
				String val=valMap.get(varList.get(where));
			//	System.out.println(varList.get(where)+"="+val);
				if(val.equals("true")){
					out=out+"1,";
				}else if(val.equals("false")){
					out=out+"0,";
				}else{
					out=out+val+",";
				}
			}
			if(header.contains(",")){
				header=header.substring(0,header.length()-1);
				System.out.println(header);
			}
			out=out.substring(0,out.length()-1);
			System.out.println(out);
		}else{
			//Append lazy reference to tags that may propogate through variables
			// We do this at the end because def-before-use is only guaranteed for first round
			for(String var : expressionOrder){
				//System.out.println("\n"+var+";");
				LazyConditionLocationSet tags = tagSet.get(var);
				HashSet<ConditionLocation> concrete = tags.getTags();
				for( ConditionLocation tag : concrete){
					String v = tag.getCondition();
					//System.out.println(v);
					if(varList.contains(v)){
						tags.addRef(tagSet.get(v));
					}else{
						String op = v.replaceAll("[^=<>]","");
						String[] parts = v.split(op);
						for(String p : parts){
							//System.out.println(p);
							if(varList.contains(p)){
								tags.addRef(tagSet.get(p));
							}
						}
					}
				}

				if(usedVars.containsKey(var)){
					ArrayList<String> intVars = usedVars.get(var);
					for(String v : intVars){
						//System.out.println(v);
						tags.addRef(tagSet.get(v));
					}
				}
		
				if(seqUsedVars.containsKey(var)){
					ArrayList<String> preVars = seqUsedVars.get(var);
					for(String v : preVars){
						//System.out.println(v);
						tags.addRef(preTagSet.get(v));
					}
				}
					
				//System.out.println(tags.toString());
				tagSet.put(var,tags);
			}

			for(String var: expressionOrder){
				LazyConditionLocationSet entries = tagSet.get(var);
				if(!entries.isResolved()){
					try{
						entries.resolve();
					}catch(Exception e){
						e.printStackTrace();
					}
				}

				tagSet.put(var,entries);
			}

			for(String var: oracle){
				//System.out.println("\n"+var);
				HashSet<ConditionLocation> madeIt = tagSet.get(var).getTags();

				//	Iterator it = madeIt.iterator();
				//	while(it.hasNext()){
				//		System.out.println(it.next().toString());		
				//	}

				//Update obligations for observability
				for(ConditionLocation tag : madeIt){
					obligationMatrix.put(tag.toString(),true);
				}
			}
		}
	}

	// Helper functions

	// Get onligation matrix 
	public HashMap<String,Boolean> getObligations(){
		return obligationMatrix;
	}
	
	// Get expression ordering
	public ArrayList<String> getOrdering(){
		return expressionOrder;
	}

	// Set input values
	public void seedInput(HashMap<String,String> inputs){
		preMap = valMap;
		valMap = inputs;	
		preTagSet = tagSet;	
		round++;
		tagSet = new HashMap<String,LazyConditionLocationSet>();
		tagStack=new Stack<HashSet<ConditionLocation>>();
		usedVars = new HashMap<String,ArrayList<String>>();
		seqUsedVars = new HashMap<String,ArrayList<String>>();
		
		partialFlag--;
	}

	// Resume from a partial trace
	public void resumeExecution(String filename){
		// Read in file
		HashMap<String,String> prevVals = new HashMap<String,String>();

		// Print trace
		try{
			BufferedReader traceFile = new BufferedReader(new FileReader(filename));

			int lineNum=0;
			String header="";
			String current="";
			String line="";
			while((line=traceFile.readLine())!=null){
				lineNum++;
				if(lineNum==1){
					header=line;
				}else{
					current=line;
				}
				System.out.println(line);
			}

			String[] vars = header.split(",");
			String[] vals = current.split(",");

			for(int position=0;position<vars.length;position++){
				prevVals.put(vars[position],vals[position]);
			}

			//Populate value map for last round and set round counter
			valMap = prevVals;
			round = lineNum-1;
			partialFlag=2;
			
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	// Set oracle
	public void setOracle(ArrayList<String> outputs){
		oracle=outputs;
	}
	
	// Set trace ordering
	public void setTraceOrder(ArrayList<String> order){
		varList=order;
		customOracle=true;
	}

	// Set model name
	public void setModel(String name){
		model=name;
	}

	// Flag OMC/DC Tracking
	public void trackOMCDC(Boolean on){
		omcdcFlag=on;
	}

	//Get round number
	public int getRound(){
		return round;
	}

	// Check if a string is a number
	public static boolean isNumeric(String str)
	{
  		return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}
}
