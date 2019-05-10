package org.emoflon.ibex.gt.hipe.runtime;

import static org.emoflon.ibex.common.collections.CollectionFactory.cfactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import org.emoflon.ibex.common.emf.EMFSaveUtils;
import org.emoflon.ibex.common.operational.IContextPatternInterpreter;
import org.emoflon.ibex.common.operational.IMatch;
import org.emoflon.ibex.common.operational.IMatchObserver;
import IBeXLanguage.IBeXPatternSet;
import hipe.engine.HiPEContentAdapter;
import hipe.engine.IHiPEEngine;
import hipe.engine.match.ProductionMatch;
import hipe.engine.message.enums.MatchType;
import hipe.generator.HiPEGenerator;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEAbstractPattern;
import hipe.pattern.HiPEPartialPattern;
import hipe.pattern.HiPEPatternContainer;
import hipe.searchplan.mincut.MinCutSearchPlan;
import hipe.searchplan.simple.SimpleSearchPlan;

/**
 * Engine for (unidirectional) graph transformations with HiPE.
 */
public class HiPEGTEngine implements IContextPatternInterpreter {
	private static final Logger logger = Logger.getLogger(HiPEGTEngine.class);

	/**
	 * The registry.
	 */
	protected Registry registry;

	/**
	 * The match observer.
	 */
	protected IMatchObserver app;
	
	
	protected IBeXPatternSet ibexPatternSet;

	/**
	 * The matches.
	 */
	//protected Map<ProductionMatch, Map<String, IMatch>> matches;

	/**
	 * The HiPE patterns.
	 */
	protected Map<String, HiPEAbstractPattern> patterns;
	
	protected HiPEPatternContainer patternContainer;
	
	protected String engineClassName;

	protected Map<String, Class<?>> dynamicClasses;
	
	/**
	 * The pattern matcher module.
	 */
	protected IHiPEEngine engine;
	
	/**
	 * The EMF notification adapter -> delegates model notifications to the HiPE engine
	 */
	protected HiPEContentAdapter adapter;
	
	/**
	 * The Rete-Network used in the engine
	 */
	protected HiPENetwork network;

	/**
	 * The path for debugging output.
	 */
	protected Optional<String> debugPath = Optional.empty();

	/**
	 * Creates a new HiPEGTEngine.
	 */
	public HiPEGTEngine() {
		this.patterns = cfactory.createObjectToObjectHashMap();
		//this.matches = cfactory.createObjectToObjectHashMap();
	}
	
	@Override
	public ResourceSet createAndPrepareResourceSet(final String workspacePath) {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		return resourceSet;
	}
	
	/**
	 * Sets the ePackage registry and the match observer
	 * (1) should be called first.
	 */
	@Override
	public void initialise(Registry registry, IMatchObserver matchObserver) {
		this.registry = registry;
		this.app = matchObserver;
	}

	/**
	 * Sets the patterns as IBeXPatterns
	 * (2) should be called second.
	 */
	@Override
	public void initPatterns(final IBeXPatternSet ibexPatternSet) {
		IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
		this.ibexPatternSet = ibexPatternSet;
		setPatterns(transformation.transform(ibexPatternSet));
		generateHiPENetworkCode();
		savePatternsForDebugging();
		saveNetworkForDebugging();
	}
	
	protected void setPatterns(HiPEPatternContainer container) {
		this.patternContainer = container;
		this.patterns = cfactory.createObjectToObjectHashMap();
		for (HiPEAbstractPattern p : container.getPatterns()) {
			this.patterns.put(p.getName(), p);
		}
	}
	
	protected void generateHiPENetworkCode() {
		SimpleSearchPlan searchPlan = new SimpleSearchPlan(patternContainer);
		//MinCutSearchPlan searchPlan = new MinCutSearchPlan(patternContainer);
		searchPlan.generateSearchPlan();
		network = searchPlan.getNetwork();
		
		URI patternURI = ibexPatternSet.eResource().getURI();
		Pattern pattern = Pattern.compile("^(.*src-gen/)(.*)(api/ibex-patterns.xmi)$");
		Matcher matcher = pattern.matcher(patternURI.toString());
		matcher.matches();
		String packageName = matcher.group(2);
		
		packageName = packageName.substring(0, packageName.length()-1);
		packageName = packageName.replace("/", ".");
		packageName = packageName+".api";
		
		engineClassName = packageName+".hipe.engine.HiPEEngine";
		try {
			double tic = System.currentTimeMillis();
			HiPEGenerator.generateCode(packageName+".",network);
			double toc = System.currentTimeMillis();
			System.out.println("code generation finished after " + (toc-tic)/1000.0 + "s");
			dynamicClasses = HiPEGenerator.generateDynamicClasses(packageName+".",network);
			tic = System.currentTimeMillis();
			System.out.println("compilation finished after " + (tic-toc)/1000.0 + "s");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Starts the monitoring for the given patterns (i.e. starts engine)
	 * (3) should be called third.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void monitor(final ResourceSet resourceSet) {
		double tic = System.currentTimeMillis();
		for (Resource r : resourceSet.getResources()) {
			if ("ecore".equals(r.getURI().fileExtension())) {
				logger.warn("Are you sure your resourceSet should contain a resource for a metamodel?: " + r.getURI());
				logger.warn("You should probably initialise this metamodel and make sure your "
						+ "resourceSet only contains models to be monitored by the pattern matcher.");
			}
		}

		EcoreUtil.resolveAll(resourceSet);

		EcoreUtil.UnresolvedProxyCrossReferencer//
				.find(resourceSet)//
				.forEach((eob, settings) -> {
					logger.error("Problems resolving: " + eob);
					settings.forEach(setting -> {
						EObject o = setting.getEObject();
						EStructuralFeature f = setting.getEStructuralFeature();

						try {
							if (f.isMany()) {
								((Collection<Object>) o.eGet(f)).remove(eob);
								logger.warn(
										"Removed proxy from collection.  You should probably check why this cannot be resolved!");
							} else {
								o.eSet(f, null);
								logger.warn(
										"Removed proxy (set to null).  You should probably check why this cannot be resolved!");
							}
						} catch (Exception e) {
							logger.warn("Unable to remove proxy: " + e);
						}
					});
				});
		
		initEngine(resourceSet);
		double toc = System.currentTimeMillis();
		System.out.println("engine initialized after " + (toc-tic)/1000.0 + "s");
	}
	
	@SuppressWarnings("unchecked")
	protected void initEngine(final ResourceSet resourceSet) {
		
		
		Class<? extends IHiPEEngine> engineClass = (Class<? extends IHiPEEngine>) dynamicClasses.get(engineClassName);
		
		try {
			double tic = System.currentTimeMillis();
			engine = engineClass.newInstance();
			double toc = System.currentTimeMillis();
			System.out.println("dynamic instantiation after " + (toc-tic)/1000.0 + "s");
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		
		try {
			double tic = System.currentTimeMillis();
			engine.initialize();
			double toc = System.currentTimeMillis();
			System.out.println("initialization after " + (toc-tic)/1000.0 + "s");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		double tic = System.currentTimeMillis();
		adapter = new HiPEContentAdapter(resourceSet, engine);
		double toc = System.currentTimeMillis();
		System.out.println("added adapter after " + (toc-tic)/1000.0 + "s");
	}
	
	@Override
	public void updateMatches() {
		// Trigger the Rete network
		double tic = System.currentTimeMillis();
		try {
			addNewMatches(engine.extractData(MatchType.NEW));
			deleteInvalidMatches(engine.extractData(MatchType.DELETED));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		double toc = System.currentTimeMillis();
		System.out.println("updated matches after " + (toc-tic)/1000.0 + "s");
	}
	
	private void addNewMatches(Map<String, Collection<ProductionMatch>> allMatches) {
		for(String patternName : allMatches.keySet()) {
			Collection<ProductionMatch> matches = allMatches.get(patternName);
			for(ProductionMatch match : matches) {
				IMatch iMatch = createMatch(match, patterns.get(patternName));
				app.addMatch(iMatch);
				System.out.println("added match: "+iMatch);
			}
		}
	}
	
	private void deleteInvalidMatches(Map<String, Collection<ProductionMatch>> allMatches) {
		for(String patternName : allMatches.keySet()) {
			Collection<ProductionMatch> matches = allMatches.get(patternName);
			for(ProductionMatch match : matches) {
				IMatch iMatch = createMatch(match, patterns.get(patternName));
				app.removeMatch(iMatch);
				System.out.println("deleted match: "+iMatch);
			}
		}
	}

	@Override
	public void terminate() {
		engine.terminate();
		//matches.clear();
	}

	@Override
	public void setDebugPath(final String debugPath) {
		this.debugPath = Optional.of(debugPath);
	}

	/**
	 * Saves the Democles patterns for debugging.
	 */
	protected void savePatternsForDebugging() {
		debugPath.ifPresent(path -> {
			List<HiPEAbstractPattern> sortedPatterns = patterns.values().stream()
					.sorted((p1, p2) -> p1.getName().compareTo(p2.getName())) // alphabetically by name
					.collect(Collectors.toList());
			EMFSaveUtils.saveModel(sortedPatterns, path + "/hipe-patterns");
		});
	}
	
	protected void saveNetworkForDebugging() {
		debugPath.ifPresent(path -> {
			List<HiPENetwork> network = new LinkedList<>();
			network.add(this.network);
			EMFSaveUtils.saveModel(network, path + "/hipe-network");
		});
	}

	

	protected IMatch createMatch(final ProductionMatch match, final HiPEAbstractPattern pattern) {
		return new HiPEGTMatch(match, pattern);
	}

}
