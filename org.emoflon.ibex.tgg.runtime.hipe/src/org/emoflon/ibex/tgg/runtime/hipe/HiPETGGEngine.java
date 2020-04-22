package org.emoflon.ibex.tgg.runtime.hipe;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emoflon.ibex.common.operational.IMatch;
import org.emoflon.ibex.common.operational.IMatchObserver;
import org.emoflon.ibex.gt.hipe.runtime.HiPEGTEngine;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXContext;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternSet;
import org.emoflon.ibex.tgg.compiler.patterns.PatternSuffixes;
import org.emoflon.ibex.tgg.compiler.patterns.PatternUtil;
import org.emoflon.ibex.tgg.operational.IBlackInterpreter;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.gen.MODELGEN;
import org.emoflon.ibex.tgg.operational.strategies.modules.IbexExecutable;
import org.emoflon.ibex.tgg.operational.strategies.opt.CC;
import org.emoflon.ibex.tgg.operational.strategies.opt.CO;
import org.emoflon.ibex.tgg.operational.strategies.sync.INITIAL_BWD;
import org.emoflon.ibex.tgg.operational.strategies.sync.INITIAL_FWD;
import org.emoflon.ibex.tgg.operational.strategies.sync.SYNC;

import hipe.engine.match.ProductionMatch;

/**
 * Engine for (bidirectional) graph transformations with HiPE.
 */
public class HiPETGGEngine extends HiPEGTEngine implements IBlackInterpreter {
	private IbexOptions options;
	private IBeXPatternSet ibexPatterns;
	private IbexExecutable executable;

	/**
	 * Creates a new DemoclesTGGEngine.
	 */
	public HiPETGGEngine() {
		super();
	}

	@Override
	public void initialise(IbexExecutable executable, final IbexOptions options, Registry registry,  IMatchObserver matchObserver) {
		super.initialise(registry, matchObserver);
		
		this.options = options;
		this.executable = executable; 
		
		Resource r = null;
		try {
			r = loadResource(options.project.path() + "/debug/" +  getIbexPatternFileName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		ibexPatterns = (IBeXPatternSet) r.getContents().get(0);
		
		for(IBeXContext context : ibexPatterns.getContextPatterns()) {
			PatternUtil.registerPattern(context.getName(), PatternSuffixes.extractType(context.getName()));
		}
		
		initPatterns(ibexPatterns);
	}

	@Override
	public void initPatterns(final IBeXPatternSet ibexPatternSet) {
		this.ibexPatternSet = ibexPatternSet;
		setPatterns(ibexPatternSet);
		generateHiPEClassName(options.project.name());
	}	
	
//	@Override
	protected String getPackageName(URI patternURI) {
		Pattern pattern = Pattern.compile("^(.*/)(.*)(/debug/.*)$");
		Matcher matcher = pattern.matcher(patternURI.toString());
		matcher.matches();
		String packageName = matcher.group(2);
		return packageName;
	}
	
	@Override
	protected String getProjectName() {
		return options.project.name();
	}
	
	private String getIbexPatternFileName() {
		if(executable instanceof INITIAL_FWD) {
			return "initial_fwd_ibexPatterns.xmi";
		}
		if(executable instanceof INITIAL_BWD) {
			return "initial_bwd_ibexPatterns.xmi";
		}
		if(executable instanceof SYNC) {
			return "sync_ibexPatterns.xmi";
		}
		if(executable instanceof CC) {
			return "cc_ibexPatterns.xmi";
		}
		if(executable instanceof CO) {
			return "co_ibexPatterns.xmi";
		}
		if(executable instanceof MODELGEN) {
			return "modelgen_ibexPatterns.xmi";
		}
		throw new RuntimeException("Unsupported operationalization detected! - " + executable.getClass().getSimpleName());
	}
	
	@Override
	protected String getNetworkFileName() {
		if(executable instanceof INITIAL_FWD) {
			return "initial_fwd_hipe-network.xmi";
		}
		if(executable instanceof INITIAL_BWD) {
			return "initial_bwd_hipe-network.xmi";
		}
		if(executable instanceof SYNC) {
			return "sync_hipe-network.xmi";
		}
		if(executable instanceof CC) {
			return "cc_hipe-network.xmi";
		}
		if(executable instanceof CO) {
			return "co_hipe-network.xmi";
		}
		if(executable instanceof MODELGEN) {
			return "modelgen_hipe-network.xmi";
		}
		throw new RuntimeException("Unsupported operationalization detected! - " + executable.getClass().getSimpleName());
	}
	
	@Override
	protected void generateHiPEClassName(String projectName) {
		if(executable instanceof INITIAL_FWD) {
			engineClassName = projectName.replace("/", ".")+".initfwd.hipe.engine.HiPEEngine";	
		} 
		else if(executable instanceof INITIAL_FWD) {
			engineClassName = projectName.replace("/", ".")+".initbwd.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CC) {
			engineClassName = projectName.replace("/", ".")+".cc.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CO) {
			engineClassName = projectName.replace("/", ".")+".co.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof SYNC) {
			engineClassName = projectName.replace("/", ".")+".sync.hipe.engine.HiPEEngine";	
		} else {
			engineClassName = projectName.replace("/", ".")+".modelgen.hipe.engine.HiPEEngine";	
		}
	}
	
	@Override
	public void monitor(final ResourceSet resourceSet) {
		if (options.debug.ibexDebug()) {
			savePatterns(resourceSet, options.project.path() + "/debug/ibex-patterns.xmi", Arrays.asList(ibexPatterns));
		}

		super.monitor(resourceSet);
	}

	/**
	 * Use this method to get extra debug information concerning the rete network.
	 * Currently not used to reduce debug output.
	 */
	@SuppressWarnings("unused")
	private void printReteNetwork() {
		//TODO
	}

	private void savePatterns(ResourceSet rs, String path, Collection<EObject> patterns) {
		Resource hipePatterns = rs.createResource(URI.createPlatformResourceURI(path, true));
		hipePatterns.getContents().addAll(patterns);
		try {
			hipePatterns.save(null);
			rs.getResources().remove(hipePatterns);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected IMatch createMatch(ProductionMatch match, final String patternName) {
		return new HiPETGGMatch(match, patternName);
	}
	
}
