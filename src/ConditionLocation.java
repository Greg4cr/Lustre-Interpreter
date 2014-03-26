/*
	Mike Whalen (mike.whalen@gmail.com) and Gregory Gay (greg@greggay.com)
	ConditionLocation
	Last Updated: 09/30/2013

	Condition Location class
*/


public class ConditionLocation{
	protected String file;
	protected String line;
	protected Integer decision;
	protected String condition;
	protected Boolean value;
 
 	public ConditionLocation(String file, Object line, Object decision, Object condition, Boolean value) {
		this.file = file;
		this.line = (String) line;
	 	this.decision = (Integer) decision;
	 	this.condition = (String) condition;
		this.value = value;
 	}
 
 	public String getFile() {
	 	return file;
 	}
 
	public String getLine() {
		return line;
	}
 
	public String getCondition(){ 
	 	return condition;
 	}
 
 	public Integer getDecision(){
	 	return decision;
 	}

	public Boolean getValue(){
		return value;
	}
 
	public void setFile(String file){
		this.file = file;
	}

	public void setLine(Object line){
		this.line = (String) line;
	}

	public void setDecision(Object decision){
		this.decision = (Integer) decision;
	}

 	public void setCondition(Object condition) {
	 	this.condition = (String) condition;
 	}

	public void setValue(Boolean value){
		this.value = value;
	}


	public String toString(){
		//return condition.toString()+"_AT_"+line.toString()+"_NONMASKED_"+value.toString();
		return file+"_"+line.toString()+"_"+decision.toString()+"_"+condition.toString()+"_"+value.toString();
	}
}


