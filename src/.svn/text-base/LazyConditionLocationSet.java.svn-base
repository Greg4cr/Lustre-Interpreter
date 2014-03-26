/*
	Mike Whalen (mike.whalen@gmail.com) and Gregory Gay (greg@greggay.com)
	LazyConditionLocationSet
	Last Updated: 09/24/2013

	Set of Condition Locations 
*/

import java.util.HashSet;
import java.util.Set;

public class LazyConditionLocationSet implements ConditionLocationSetIntf<LazyConditionLocationSet>{
	private HashSet<ConditionLocation> condSet ; 
	private HashSet<LazyConditionLocationSet> varCondSet;

	public LazyConditionLocationSet(){
		condSet = new HashSet<ConditionLocation>();
		varCondSet = new HashSet<LazyConditionLocationSet>();
	}

	public LazyConditionLocationSet(HashSet<ConditionLocation> cs, HashSet<LazyConditionLocationSet> vcs){
		condSet = new HashSet<ConditionLocation>();
		varCondSet = new HashSet<LazyConditionLocationSet>();

		condSet.addAll(cs);
		vcs.addAll(vcs);
	}

	public void addTagSet(HashSet<ConditionLocation> cs){
		condSet.addAll(cs);
	}

	public void addRefSet(HashSet<LazyConditionLocationSet> vcs){
		varCondSet.addAll(vcs);
	}

	public void addTag(ConditionLocation tag){
		condSet.add(tag);
	}
	
	public void addRef(LazyConditionLocationSet ref){
		varCondSet.add(ref);
	}
	
	public HashSet<ConditionLocation> getTags(){
		return condSet;
	}

	public HashSet<LazyConditionLocationSet> getRefs(){
		return varCondSet;
	}

	public LazyConditionLocationSet union(LazyConditionLocationSet other) {
		condSet.addAll(other.condSet);
		varCondSet.addAll(other.varCondSet);
		return this;
	}
	
	public HashSet<ConditionLocation> resolveInternal(HashSet<ConditionLocation> resolvedSet) {
		HashSet<ConditionLocation> rs = new HashSet<ConditionLocation>();
		rs.addAll(resolvedSet);
		rs.addAll(condSet);
		
		for (LazyConditionLocationSet e : varCondSet) {
			if(!(e==null)){
				rs=e.resolveInternal(rs);
			}
		}
	
		return rs;
	}
	
	public boolean isResolved() { 
		return varCondSet.isEmpty();
	}
	
	public void resolve() {
		if (!isResolved()) {
			HashSet<ConditionLocation> resolvedSet = new HashSet<ConditionLocation>();
			resolvedSet= resolveInternal(resolvedSet);
			condSet = resolvedSet ; 
			varCondSet.clear(); 
		}
	}
	
	// we explicitly override equality to be reference equivalence 
	// to make it fast.
	public boolean equals(Object other) {
		return (this == other);
	}
	
	public Set<ConditionLocation> resolvedConditionSet() {
		resolve(); 
		return condSet;
	}

	public String toString(){
		String out ="Tags: ";
		if(!condSet.isEmpty()){
			for(ConditionLocation cond : condSet){
				out=out+cond.toString()+",";
			}
		}
		out=out+"; References: ";
		if(!varCondSet.isEmpty()){
			for(LazyConditionLocationSet vcond : varCondSet){
				if(vcond!=null){
					out=out+"("+vcond.toString()+"),";
				}
			}
		}	
		return out;
	}
	
}
