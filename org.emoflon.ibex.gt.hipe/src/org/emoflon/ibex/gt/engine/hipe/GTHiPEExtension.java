package org.emoflon.ibex.gt.engine.hipe;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.emoflon.ibex.common.engine.IBeXPMEngineInformation;

/**
 * Registers the HiPE engine for code generation.
 */
public class GTHiPEExtension implements IBeXPMEngineInformation {

	@Override
	public Set<String> getDependencies() {
		return new HashSet<String>(Arrays.asList(
				// Hipe deps

				// Ibex Hipe deps
				"org.emoflon.ibex.gt.hipe"));
	}

	@Override
	public Set<String> getImports() {
		return new HashSet<String>(Arrays.asList("org.emoflon.ibex.gt.hipe.runtime.HiPEGTEngine"));
	}

	@Override
	public String getEngineName() {
		return "HiPE";
	}

	@Override
	public String getEngineClassName() {
		return "HiPEGTEngine";
	}

	@Override
	public boolean uses_synchroneous_matching() {
		return true;
	}

	@Override
	public boolean supports_arithmetic_attr_constraints() {
		return true;
	}

	@Override
	public boolean supports_boolean_attr_constraints() {
		return true;
	}

	@Override
	public boolean supports_count_matches() {
		return true;
	}
}
