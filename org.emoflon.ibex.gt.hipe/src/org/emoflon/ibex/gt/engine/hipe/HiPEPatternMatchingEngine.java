package org.emoflon.ibex.gt.engine.hipe;

import static org.emoflon.ibex.common.collections.CollectionFactory.cfactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXPattern;
import org.emoflon.ibex.common.emf.EMFSaveUtils;
import org.emoflon.ibex.common.engine.IBeXPMEngineInformation;
import org.emoflon.ibex.gt.engine.IBeXGTPatternMatcher;
import org.emoflon.ibex.gt.gtmodel.IBeXGTModel.GTModel;
import org.emoflon.smartemf.persistence.SmartEMFResource;

import hipe.engine.HiPEContentAdapter;
import hipe.engine.HiPEOptions;
import hipe.engine.IHiPEEngine;
import hipe.engine.match.ProductionMatch;
import hipe.engine.message.production.ProductionResult;
import hipe.network.HiPENetwork;

/**
 * Engine for (unidirectional) graph transformations with HiPE.
 */
public class HiPEPatternMatchingEngine extends IBeXGTPatternMatcher<ProductionMatch> {
	private static final Logger logger = Logger.getLogger(HiPEPatternMatchingEngine.class);

	/**
	 * The HiPE patterns.
	 */
	protected Map<String, String> patterns;

	/**
	 * The pattern matcher module.
	 */
	protected IHiPEEngine engine;

	/**
	 * The EMF notification adapter -> delegates model notifications to the HiPE
	 * engine
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

	protected String hipeEngineClassName;

	/**
	 * Creates a new HiPEGTEngine.
	 */
	public HiPEPatternMatchingEngine(final GTModel ibexModel, final ResourceSet model) {
		super(ibexModel, model);
	}

	/**
	 * Sets the ePackage registry and the match observer (1) should be called first.
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void initialize() {
		hipeEngineClassName = generateHiPEClassName();

		this.patterns = cfactory.createObjectToObjectHashMap();
		for (IBeXPattern pattern : ibexModel.getPatternSet().getPatterns()) {
			if (pattern.getSignatureNodes().isEmpty() && pattern.getLocalNodes().isEmpty())
				continue;

			patterns.put(pattern.getName().replace("-", "_"), pattern.getName());

		}

		for (Resource r : model.getResources()) {
			if ("ecore".equals(r.getURI().fileExtension())) {
				logger.warn("Are you sure your resourceSet should contain a resource for a metamodel?: " + r.getURI());
				logger.warn("You should probably initialise this metamodel and make sure your "
						+ "resourceSet only contains models to be monitored by the pattern matcher.");
			}
		}

		model.getResources().forEach(r -> {
			EcoreUtil.resolveAll(model);
		});

		boolean[] foundConflicts = { false, false };
		Set<String> problems = new HashSet<>();
		Set<String> exceptions = new HashSet<>();
		EcoreUtil.UnresolvedProxyCrossReferencer//
				.find(model)//
				.forEach((eob, settings) -> {
					problems.add(eob.toString());
					settings.forEach(setting -> {
						EObject o = setting.getEObject();
						EStructuralFeature f = setting.getEStructuralFeature();

						try {
							if (f.isMany()) {
								((Collection<Object>) o.eGet(f)).remove(eob);
								foundConflicts[0] = true;
							} else {
								o.eSet(f, null);
								foundConflicts[1] = true;
							}
						} catch (Exception e) {
							exceptions.add(e.getMessage());
						}
					});
				});

		// Debugging output
		if (problems.size() > 0) {
			logger.error("Problems resolving proxy cross-references occurred for " + problems.size() + " EObjects.");
		}
		if (foundConflicts[0]) {
			logger.warn("Removed proxy from collection.  You should probably check why this cannot be resolved!");
		}
		if (foundConflicts[1]) {
			logger.warn("Removed proxy (set to null).  You should probably check why this cannot be resolved!");
		}
		exceptions.forEach(ex -> logger.warn("Unable to remove proxy: " + ex));

		// Insert model into engine
		initEngine(model.getResources());
	}

	protected String generateHiPEClassName() {
		return ibexModel.getMetaData().getPackage() + ".hipe.engine.HiPEEngine";
	}

	@SuppressWarnings("unchecked")
	protected void initEngine(final Collection<Resource> resources) {
		if (engine == null) {
			Class<? extends IHiPEEngine> engineClass = null;
			try {
				engineClass = (Class<? extends IHiPEEngine>) Class.forName(hipeEngineClassName);
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			try {
				if (engineClass == null) {
					throw new RuntimeException("Engine class: " + hipeEngineClassName + " -> not found!");
				}
				Constructor<? extends IHiPEEngine> constructor = engineClass.getConstructor();
				constructor.setAccessible(true);

				engine = constructor.newInstance();
			} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException
					| IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		try {
			HiPEOptions options = new HiPEOptions();
			options.cascadingNotifications = cascadingNotifications(resources);
			options.lazyInitialization = initializeLazy();
			engine.initialize(options);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		adapter = new HiPEContentAdapter(resources.stream().filter(res -> !res.getURI().toString().contains("-trash"))
				.collect(Collectors.toSet()), engine);
	}

	protected boolean cascadingNotifications(final Collection<Resource> resources) {
		return !resources.stream()
				.filter(res -> !(res instanceof SmartEMFResource && ((SmartEMFResource) res).getCascade())).findAny()
				.isPresent();
	}

	protected boolean initializeLazy() {
		return false;
	}

	protected String getNetworkFileName() {
		return "hipe-network.xmi";
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

	protected void addNewMatches(Map<String, ProductionResult> extractData) {
		for (String patternName : extractData.keySet()) {
			Collection<ProductionMatch> matches = extractData.get(patternName).getNewMatches();
			for (ProductionMatch match : matches) {
				if (!patterns.containsKey(patternName))
					continue;

				addMatch(match);
			}
		}
	}

	protected void deleteInvalidMatches(Map<String, ProductionResult> extractData) {
		for (String patternName : extractData.keySet()) {
			Collection<ProductionMatch> matches = extractData.get(patternName).getDeleteMatches();
			for (ProductionMatch match : matches) {
				if (!patterns.containsKey(patternName))
					continue;

				removeMatch(match);
			}
		}
	}

	@Override
	public void terminate() {
		adapter.removeAdapter();
		engine.terminate();
	}

	protected void saveNetworkForDebugging() {
		debugPath.ifPresent(path -> {
			List<HiPENetwork> network = new LinkedList<>();
			network.add(this.network);
			EMFSaveUtils.saveModel(network, path + "/hipe-network");
		});
	}

	@Override
	protected Map<String, Object> extractNodes(ProductionMatch match) {
		Map<String, Object> nodes = new HashMap<>();
		for (String name : match.getLabelToIndexMap().keySet()) {
			nodes.put(name, match.getSignatureNodes()[match.getLabelToIndexMap().get(name)]);
		}
		return nodes;
	}

	@Override
	protected String extractPatternName(ProductionMatch match) {
		return patterns.get(match.patternName);
	}

	@Override
	protected IBeXPMEngineInformation createEngineProperties() {
		return new GTHiPEExtension();
	}

}
