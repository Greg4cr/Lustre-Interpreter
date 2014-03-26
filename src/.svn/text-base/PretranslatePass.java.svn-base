import org.antlr.runtime.*;


public class PretranslatePass{
	public static void main(String[] args) throws Exception {
		//Create a lexer stream
		PretranslateLexer lex = new PretranslateLexer(new ANTLRFileStream(args[0]));
		//Pass it to the token stream
		CommonTokenStream tokens = new CommonTokenStream(lex);
		//Pass the token stream to the parser
		PretranslateParser parser = new PretranslateParser(tokens);

		//Call a parser rule (each rule creates a corresponding method)
		try{
			parser.lustre();
		}catch(RecognitionException e){
			e.printStackTrace();
		}
	}
}

