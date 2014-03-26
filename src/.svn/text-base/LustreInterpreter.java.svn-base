/*
	Gregory Gay (greg@greggay.com)
	LustreInterpreter
	Last Updated: 03/26/2014

	Interpreter for the Lustre synchronous language
*/

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;
import java.io.*;

public class LustreInterpreter{
	public static void main(String[] args) throws Exception {
		// Read in inputs from a file
		ArrayList<String> inputs = readInputFile(args[1]);
		// Set the simulation mode
		String mode = args[2];

		try{
			//Create a lexer stream
			LustreLexer lex = new LustreLexer(new ANTLRFileStream(args[0]));
			//Pass it to the token stream
			CommonTokenStream tokens = new CommonTokenStream(lex);
			//Pass the token stream to the parser
			LustreParser parser = new LustreParser(tokens);

			//Get parse tree
			ParseTree tree = parser.lustre();
			ParseTreeWalker walker = new ParseTreeWalker();

			OrderOfExecution order = new OrderOfExecution();

			if(mode.equals("simulate")){
				walker.walk(order,tree);
				// If not all variables in order	
				while(order.getUnresolved().size() > 0){	
					walker.walk(order,tree);
				}
				ArrayList<String> vars = order.getResolved();
				// Now simulate from reordered file
				modelPrinter(args[0],vars);
				lex = new LustreLexer(new ANTLRFileStream("ordered.lus"));
				tokens = new CommonTokenStream(lex);
				parser = new LustreParser(tokens);
				tree = parser.lustre();

				TreeInterpreter interpreter = new TreeInterpreter();
				interpreter.setModel(args[0]);		
				//if ordering specified, set ordering
				if(args.length>3){
					interpreter.setTraceOrder(readOracleFile(args[3]));
				}	

				for(int round=1;round<inputs.size();round++){
					//Seed in input values
					HashMap<String,String> currentInput = getInputMap(inputs.get(0),inputs.get(round));
					interpreter.seedInput(currentInput);

					//Perform the simulation
					walker.walk(interpreter, tree);
				}
			}else if(mode.equals("order")){			
				walker.walk(order,tree);
				// If not all variables in order
				while(order.getUnresolved().size() > 0){	
					walker.walk(order,tree);
				}
				ArrayList<String> vars = order.getResolved();

				modelPrinter(args[0],vars);
			}else if(mode.equals("omcdc")){
				walker.walk(order,tree);
				// If not all variables in order	
				while(order.getUnresolved().size() > 0){	
					walker.walk(order,tree);
				}
				ArrayList<String> vars = order.getResolved();
				// Now simulate from reordered file
				modelPrinter(args[0],vars);
				lex = new LustreLexer(new ANTLRFileStream("ordered.lus"));
				tokens = new CommonTokenStream(lex);
				parser = new LustreParser(tokens);
				tree = parser.lustre();

				TreeInterpreter interpreter = new TreeInterpreter();
				interpreter.trackOMCDC(true);		
				interpreter.setModel(args[0]);	
				interpreter.setOracle(readOracleFile(args[3]));

				for(int round=1;round<inputs.size();round++){	
					//Seed in input values
					HashMap<String,String> currentInput = getInputMap(inputs.get(0),inputs.get(round));
					interpreter.seedInput(currentInput);

					//Perform the simulation
					walker.walk(interpreter, tree);
				}
				
				// Print out obligation matrix
				obligationPrinter(interpreter.getObligations());
			}
			
		}catch(RecognitionException e){
			e.printStackTrace();
		}
	}

	// Read input file into an array list
	public static ArrayList<String> readInputFile(String filename) throws Exception{
		ArrayList<String> inputs = new ArrayList<String>();
			
		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line ="";

			while((line=reader.readLine())!=null){
				inputs.add(line);
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}

		return inputs;
	}

	// Read in oracle file
	public static ArrayList<String> readOracleFile(String filename) throws Exception{
		ArrayList<String> oracle = new ArrayList<String>();

		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line ="";

			while((line=reader.readLine())!=null){
				String[] vars=line.split(",");
				for(int part=0;part<vars.length;part++){
					oracle.add(vars[part]);
				}
			}
		
		}catch(Exception e){
			e.printStackTrace();
		}

		return oracle;
	}

	// Pretty printer for the obligation matrix
	public static void obligationPrinter(HashMap<String,Boolean> matrix) throws Exception{
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter("matrix.csv",true));
			TreeSet<String> keys = new TreeSet<String>(matrix.keySet());

			String header="";
			String out="";
			float met=0;
			float total=0;
			for(String obligation : keys){
				header=header+obligation+",";
				Boolean result=matrix.get(obligation);
				if(result==true){
					out=out+"1,";
					met++;
				}else{
					out=out+"0,";
				}
				total++;
			}
			header=header.substring(0,header.length()-1);
			System.out.println("\n"+header);
			//writer.write(header+"\n");
			out=out.substring(0,out.length()-1);
			System.out.println(out);
			writer.write(out+"\n");
			System.out.println("\nObligation Summary:\nMet:"+met+"/"+total+" = "+(met/total));

			writer.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}


	// Pretty file printer, to print out Lustre model in def-before-use order
	public static void modelPrinter(String filename, ArrayList<String> order) throws Exception{
		ArrayList<String> otherLines = new ArrayList<String>();
		HashMap<String,String> expressions = new HashMap<String,String>();

		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line="";
			Boolean incomplete = false;
			String exp="";
			String var="";

			while((line=reader.readLine())!=null){
				if(incomplete==true){
					exp = exp+" "+line.trim();
					if(line.contains(";")){
						incomplete=false;
						expressions.put(var,exp);
					}
				}else if(line.contains("=") && !line.contains("const")){
					var=line.split("=")[0].trim();
					exp=line;
					if(!line.contains(";")){
						incomplete=true;
					}else{
						expressions.put(var,exp);
					}
				}else if(incomplete==false){
					otherLines.add(line);
				}
			}

			BufferedWriter writer = new BufferedWriter(new FileWriter("ordered.lus"));

			// Print header lines
			for(int l=0;l<otherLines.size();l++){
				String current=otherLines.get(l);
				if(current.trim().equals("let")||current.contains("let ")){
					writer.write(current+"\n");
					//Print out ordered expressions
					for(int where=0;where<order.size();where++){
						String expr = expressions.get(order.get(where));
						writer.write(expr.replaceAll("(?:/\\*(?:[^*]|(?:\\*+[^*/]))*\\*+/)|(?://.*)","")+"\n");
					}
				}else if(!current.equals("")){
					writer.write(current+"\n");
				}
			}
			writer.close();

		}catch(Exception e){
			e.printStackTrace();
		}
	}

	// Transform CSV style input sequence into mapping for tree walker
	public static HashMap<String,String> getInputMap(String header, String current) throws Exception{
		HashMap<String,String> inputs = new HashMap<String,String>();
		String[] vars = header.split(",");
		String[] vals = current.split(",");

		if (vars.length != vals.length){
			throw new Exception("Number of variables and number of values do not match.");	
		}else{
			for(int position=0;position<vars.length;position++){
				inputs.put(vars[position],vals[position]);
			}
		}

		return inputs;
	}
}

