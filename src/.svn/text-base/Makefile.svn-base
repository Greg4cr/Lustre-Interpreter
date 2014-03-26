compile:
	java -jar /usr/local/lib/antlr-4.1-complete.jar Lustre.g
	java -jar /home/whalen/code/antlr/antlr-3.4-complete-no-antlrv2.jar Pretranslate.g
	javac LustreInterpreter.java
	javac TreeInterpreter.java
	javac OrderOfExecution.java
	javac LazyConditionLocationSet.java
	javac ConditionLocation.java
	javac PretranslatePass.java
clean:
	rm *.class
	rm *.tokens
	rm LustreBaseListener.java
	rm LustreLexer*
	rm LustreParser*
	rm LustreListener.java
	rm PretranslateLexer*
	rm PretranslateParser*
