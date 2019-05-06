package org.emoflon.ibex.gt.hipe.ide.codegen;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.emoflon.ibex.gt.codegen.GTEngineExtension;

/**
 * Registers the HiPE engine for code generation.
 */
public class GTHiPEExtension implements GTEngineExtension {

	@Override
	public Set<String> getDependencies() {
		return new HashSet<String>(Arrays.asList("org.emoflon.ibex.gt.hipe"));
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
}
