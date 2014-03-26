/*
	Gregory Gay (greg@greggay.com)
	ConditionLocationSetIntf
	Last Updated: 09/16/2013

	Interface for a Condition Location Set
*/

import java.util.HashSet;
import java.util.Set;

public interface ConditionLocationSetIntf<SELF extends ConditionLocationSetIntf<SELF>> {
	
	public SELF union(SELF other);	
	public HashSet<ConditionLocation> resolveInternal(HashSet<ConditionLocation> resolvedSet); 
	public boolean isResolved(); 
	public void resolve();
	public boolean equals(Object other);
	public Set<ConditionLocation> resolvedConditionSet();
}
