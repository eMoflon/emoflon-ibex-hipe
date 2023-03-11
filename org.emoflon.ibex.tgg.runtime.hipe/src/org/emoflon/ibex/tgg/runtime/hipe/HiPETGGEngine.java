package org.emoflon.ibex.tgg.runtime.hipe;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.common.engine.IBeXPMEngineInformation;
import org.emoflon.ibex.common.engine.IMatch;
import org.emoflon.ibex.tgg.compiler.patterns.PatternSuffixes;
import org.emoflon.ibex.tgg.compiler.patterns.PatternUtil;
import org.emoflon.ibex.tgg.runtime.BlackInterpreter;
import org.emoflon.ibex.tgg.runtime.config.options.IbexOptions;
import org.emoflon.ibex.tgg.runtime.matches.SimpleTGGMatch;
import org.emoflon.ibex.tgg.runtime.strategies.integrate.INTEGRATE;
import org.emoflon.ibex.tgg.runtime.strategies.modules.IMatchObserver;
import org.emoflon.ibex.tgg.runtime.strategies.modules.IbexExecutable;
import org.emoflon.ibex.tgg.runtime.strategies.opt.CC;
import org.emoflon.ibex.tgg.runtime.strategies.opt.CO;
import org.emoflon.ibex.tgg.runtime.strategies.sync.INITIAL_BWD;
import org.emoflon.ibex.tgg.runtime.strategies.sync.INITIAL_FWD;
import org.emoflon.ibex.tgg.runtime.strategies.sync.SYNC;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.TGGModel;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.TGGOperationalRule;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.TGGRule;
import org.emoflon.ibex.tgg.util.benchmark.TimeMeasurable;
import org.emoflon.ibex.tgg.util.benchmark.TimeRegistry;
import org.emoflon.ibex.tgg.util.benchmark.Timer;
import org.emoflon.ibex.tgg.util.benchmark.Times;
import org.emoflon.smartemf.persistence.SmartEMFResourceFactoryImpl;

import hipe.engine.HiPEContentAdapter;
import hipe.engine.IHiPEEngine;
import hipe.engine.match.ProductionMatch;
import hipe.engine.message.production.ProductionResult;

/**
 * Engine for (bidirectional) graph transformations with HiPE.
 */
public class HiPETGGEngine extends BlackInterpreter<ProductionMatch> implements TimeMeasurable {
	
	private IHiPEEngine engine;
	
	private final Times times = new Times();
	
	protected String engineClassName;
	
	/**
	 * The HiPE patterns.
	 */
	protected Map<String, String> patterns;
	
	/**
	 * The base uri
	 */
	protected URI base;

	/**
	 * Creates a new HiPETGGEngine.
	 */
	public HiPETGGEngine(TGGModel model, ResourceSet resourceSet) {
		super(model, resourceSet);
		TimeRegistry.register(this);
		base = URI.createPlatformResourceURI("/", true);
	}
	
	public HiPETGGEngine(TGGModel model, ResourceSet resourceSet, IHiPEEngine engine) {
		this(model, resourceSet);
		this.engine = engine;
	}

	@Override
	public void initialize(IbexExecutable executable, final IbexOptions options, Registry registry, IMatchObserver matchObserver) {
		this.executable = executable;
		this.options = options;
		this.registry = registry;
		this.matchObserver = matchObserver;
		engineClassName = generateHiPEClassName();
		
		String cp = "";
		
		String path = executable.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		// this is a fix for situation where emoflon is executed within an eclipse plugin
		if(!path.contains("bin/"))
			path += "bin/";
		path +=  generateHiPEClassName().replace(".", "/").replace("HiPEEngine", "ibex-patterns.xmi");
		
		File file = new File(path);
		try {
			cp = file.getCanonicalPath();
			cp = cp.replace("%20", " ");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		Resource r = null;
		try {
			r = loadResource("file://" + cp);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		TGGModel ibexModel = (TGGModel) r.getContents().get(0);
		
		for(TGGRule tggRule : ibexModel.getRuleSet().getRules()) {
			for(TGGOperationalRule operationalRule : tggRule.getOperationalisations()) {
				PatternUtil.registerPattern(operationalRule.getName(), PatternSuffixes.extractType(operationalRule.getName()));				
			}
		}
		
	}
	
	protected Resource loadResource(String path) throws Exception {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi",new XMIResourceFactoryImpl());
		model.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi",new XMIResourceFactoryImpl());
		model.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		
		Resource modelResource = model.getResource(URI.createURI(path).resolve(base), true);
		EcoreUtil.resolveAll(model);
		
		if(modelResource == null)
			throw new IOException("File did not contain a valid model.");
		return modelResource;
	}

	protected String getProjectName() {
		return options.project.name();
	}
	
	protected String generateHiPEClassName() {
		String projectName = options.project.name();
		if(executable instanceof INITIAL_FWD) {
			return projectName.replace("/", ".")+".initfwd.hipe.engine.HiPEEngine";	
		} 
		else if(executable instanceof INITIAL_BWD) {
			return projectName.replace("/", ".")+".initbwd.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CC) {
			return projectName.replace("/", ".")+".cc.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CO) {
			return projectName.replace("/", ".")+".co.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof SYNC) {
			return projectName.replace("/", ".")+".sync.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof INTEGRATE) {
			return projectName.replace("/", ".")+".integrate.hipe.engine.HiPEEngine";	
		}
		else {
			return projectName.replace("/", ".")+".modelgen.hipe.engine.HiPEEngine";	
		}
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
	public void fetchMatches() {
		// Trigger the Rete network
		try {
			Map<String, ProductionResult> extractData = engine.extractData();
			addNewMatches(extractData);
			deleteInvalidMatches(extractData);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	protected void addNewMatches_slowly(Map<String, ProductionResult> extractData) {
		for (String patternName : extractData.keySet()) {
			Collection<ProductionMatch> matches = extractData.get(patternName).getNewMatches();
			for (ProductionMatch match : matches) {
				if (!patterns.containsKey(patternName))
					continue;

				addMatch(match);
			}
		}
	}

	protected void deleteInvalidMatches_slowly(Map<String, ProductionResult> extractData) {
		for (String patternName : extractData.keySet()) {
			Collection<ProductionMatch> matches = extractData.get(patternName).getDeleteMatches();
			for (ProductionMatch match : matches) {
				if (!patterns.containsKey(patternName))
					continue;

				removeMatch(match);
			}
		}
	}
	
	protected void addNewMatches(Map<String, ProductionResult> extractData) {
		// TODO Auto-generated method stub
		if(!options.patterns.parallelizeMatchProcessing()) {
			addNewMatches_slowly(extractData);
			return;
		}
		
		Collection<IMatch> iMatches = new LinkedList<>();
		for(String patternName : extractData.keySet()) {
			if(patterns.get(patternName) == null)
				continue;
			String pName = patterns.get(patternName);
			Collection<ProductionMatch> matches = extractData.get(patternName).getNewMatches();
			iMatches.addAll(matches.parallelStream().map(m -> createMatch(m, pName)).collect(Collectors.toList()));
		}
		matchObserver.addMatches(iMatches);
	}
	
	protected void deleteInvalidMatches(Map<String, ProductionResult> extractData) {
		if(!options.patterns.parallelizeMatchProcessing()) {
			deleteInvalidMatches_slowly(extractData);
			return;
		}
		
		for(String patternName : extractData.keySet()) {
			if(patterns.get(patternName) == null)
				continue;
			String pName = patterns.get(patternName);
			Collection<ProductionMatch> matches = extractData.get(patternName).getDeleteMatches();
			Collection<IMatch> iMatches = matches.parallelStream().map(m -> createMatch(m, pName)).collect(Collectors.toList());
			matchObserver.removeMatches(iMatches);
		}
	}

	protected IMatch createMatch(ProductionMatch match, final String patternName) {
		return new HiPETGGMatch(match, patternName);
	}
	
	@Override
	public void updateMatches() {
		Timer.start();
		// Trigger the Rete network
		try {
			Map<String, ProductionResult> extractData = engine.extractData();
			times.addTo("findMatches", Timer.stop());
			addNewMatches(extractData);
			deleteInvalidMatches(extractData);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Times getTimes() {
		return times;
	}
	
	public IbexOptions getOptions() {
		return options;
	}
	
	protected boolean cascadingNotifications(Collection<Resource> resources) {
		return options.project.usesSmartEMF();
	}

	protected boolean initializeLazy() {
		return options.project.usesSmartEMF();
	}

	@Override
	public void addMatch(IMatch match) {

	}

	@Override
	public void addMatches(Collection<IMatch> matches) {
		
	}

	@Override
	public void removeMatch(IMatch match) {
		
	}

	@Override
	public void removeMatches(Collection<IMatch> matches) {
		
	}

	@Override
	public SimpleTGGMatch transformToIMatch(ProductionMatch match) {
		return null;
	}

	@Override
	protected IBeXPMEngineInformation createEngineProperties() {
		return null;
	}

	@Override
	public void terminate() {
		
	}

	@Override
	public void monitor(Resource r) {
		r.eAdapters().add(new HiPEContentAdapter(observedResources, engine));
	}

	@Override
	public ResourceSet createAndPrepareResourceSet(final String workspacePath) {
		model = createAndPrepareResourceSet_internal(workspacePath);
		return createAndPrepareResourceSet_internal(workspacePath);
	}
	
	private ResourceSet createAndPrepareResourceSet_internal(final String workspacePath) {
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new SmartEMFResourceFactoryImpl(workspacePath));
//				.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		try {
			rs.getURIConverter().getURIMap().put(URI.createPlatformResourceURI("/", true), URI.createFileURI(new File(workspacePath).getCanonicalPath() + File.separator));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return rs;
	}
}
