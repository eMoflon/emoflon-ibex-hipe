package org.emoflon.ibex.gt.hipe.runtime;

import org.emoflon.ibex.common.operational.IPatternInterpreterProperties;

public class HiPEProperties implements IPatternInterpreterProperties {
	
	@Override
	public boolean uses_synchroneous_matching() {
		return true;
	}
	
	@Override
	public boolean supports_arithmetic_attr_constraints() {
		return true;
	}
	
	@Override
	public boolean supports_count_matches() {
		return true;
	}

}
