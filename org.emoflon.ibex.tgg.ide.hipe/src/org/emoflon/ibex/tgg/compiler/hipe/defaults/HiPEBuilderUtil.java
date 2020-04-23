package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.codegen.ecore.generator.Generator;
import org.eclipse.emf.codegen.ecore.genmodel.GenBase;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenBaseGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.util.GenModelUtil;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.Monitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.importer.ecore.EcoreImporter;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.modules.TGGResourceHandler;
import org.moflon.core.utilities.MoflonUtil;
import org.moflon.emf.codegen.StandalonePackageDescriptor;
import org.moflon.emf.codegen.resource.GenModelResourceFactory;

public class HiPEBuilderUtil {
	public Collection<String> metaModelImports;
	public Collection<EPackage> importedPackages = new LinkedList<>();
	private IbexOptions options;

	public HiPEBuilderUtil(IbexOptions options) {
		this.options = options;
	}

	public void loadDefaultSettings() {
		TGGResourceHandler resourceHandler = options.resourceHandler();
		resourceHandler.getResourceSet().getPackageRegistry().put("http://www.eclipse.org/emf/2002/GenModel",
				new StandalonePackageDescriptor("org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage"));

		resourceHandler.getResourceSet().getResourceFactoryRegistry().getExtensionToFactoryMap().put("genmodel",
				new GenModelResourceFactory());
		resourceHandler.getResourceSet().getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi",
				new XMIResourceFactoryImpl());
	}

	protected void generateMetaModelCode(URI base, String metaModelLocation, String genModelLocation,
			EPackage metaModel) {		
		String pluginID = options.project.name();
		
		URI metaModelUri = URI.createURI(metaModelLocation);
		metaModelUri = metaModelUri.resolve(base);

		Monitor monitor = BasicMonitor.toMonitor(new NullProgressMonitor());
		try {
			EcoreImporter importer = new EcoreImporter();
			importer.setModelLocation(metaModelUri.toString());
			importer.computeEPackages(monitor);
			importer.adjustEPackages(monitor);
			
			for(EPackage ePackage : importer.getEPackages()) {
				if(ePackage.getName().equals(metaModel.getName())) {
					importer.getEPackageConvertInfo(ePackage).setConvert(true);
				}else {
					importer.getEPackageConvertInfo(ePackage).setConvert(false);
				}
				
			}			
			
			importer.setGenModelContainerPath(new Path(pluginID).append("model"));
			importer.setGenModelFileName(importer.computeDefaultGenModelFileName());
			importer.prepareGenModelAndEPackages(monitor);

			GenModel genModel = importer.getGenModel();
			genModel.setModelDirectory(options.project.path() + "/gen/");
			
		    Set<GenPackage> removals = genModel.getGenPackages().stream().filter(pkg -> !pkg.getEcorePackage().getName().equals(metaModel.getName())).collect(Collectors.toSet());
		    removals.forEach(gp -> System.out.println("Removed GP: "+gp.getNSName()));
			removals.forEach(pkg -> genModel.getGenPackages().remove(pkg));
			genModel.getUsedGenPackages().addAll(removals);
			genModel.getUsedGenPackages().forEach(gp -> System.out.println("Added to used GP"+gp.getNSName()));
//			genModel.eResource().getContents().addAll(removals);
			
			for(GenPackage gp : removals) {
				GenModel fakeGen = GenModelFactory.eINSTANCE.createGenModel();
				fakeGen.setModelPluginID(gp.getEcorePackage().getNsPrefix());
				fakeGen.getGenPackages().add(gp);
				genModel.eResource().getContents().add(fakeGen);
			}
			
			genModel.setGenerateSchema(true);
			genModel.setCanGenerate(true);
		    genModel.reconcile();

			EcoreUtil.resolveAll(importer.getGenModelResourceSet());
		    genModel.eResource().save(Collections.emptyMap());
		    
//			Generator generator = GenModelUtil.createGenerator(genModel);
		    HiPEGenGenerator generator = new HiPEGenGenerator();
		    generator.setInput(genModel);
//			generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, monitor);
	        generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, options.project.name(), BasicMonitor.toMonitor(new NullProgressMonitor()), options.project.name());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Set<GenPackage> getReferencedGenPackages(ResourceSet resourceSet) {
		Set<GenPackage> referencedGenPackages = new HashSet<>();
		Map<String, URI> map = EcorePlugin.getEPackageNsURIToGenModelLocationMap(true);
		for (EPackage pack : importedPackages) {
			URI uri = map.get(pack.getNsURI());
			if (uri != null) {
				Resource resource;
				try {
					resource = resourceSet.getResource(uri, true);
				} catch (Exception e) {
					if (uri.isPlatformResource()) {
						uri = URI.createPlatformPluginURI(uri.toPlatformString(true), true);
						try {
							resource = resourceSet.getResource(uri, true);
						} catch (Exception e2) {
							continue;
						}
					} else {
						continue;
					}
				}
				GenModel referenced = (GenModel) resource.getContents().get(0);
				EList<GenPackage> genPackages = referenced.getGenPackages();
				referencedGenPackages.addAll(genPackages);
			}
		}
		return referencedGenPackages;
	}

	public Collection<EPackage> getImportedPackages() {
		return importedPackages;
	}

	public static IbexOptions registerResourceHandler(IbexOptions options, List<String> metaModelImports, boolean generateCode)
			throws IOException {
		HiPEBuilderUtil util = new HiPEBuilderUtil(options);
		options.resourceHandler(new TGGResourceHandler() {
			@Override
			protected void registerUserMetamodels() throws IOException {
				for (String imp : metaModelImports) {
					util.getImportedPackages().add(loadAndRegisterMetamodel(imp));
				}
				String metaModelLocation = options.project.path() + "/model/"
						+ MoflonUtil.lastCapitalizedSegmentOf(options.project.name()) + ".ecore";
				String genModelLocation = options.project.path() + "/model/"
						+ MoflonUtil.lastCapitalizedSegmentOf(options.project.name()) + ".genmodel";
				EPackage metaModel = loadAndRegisterCorrMetamodel(metaModelLocation);
				if(generateCode)
					util.generateMetaModelCode(base, metaModelLocation, genModelLocation, metaModel);
			}

			@Override
			protected void createAndPrepareResourceSet() {
				rs = new ResourceSetImpl();
				util.loadDefaultSettings();
			}

			@Override
			public void loadModels() throws IOException {
			}

			@Override
			public void saveModels() throws IOException {
			}
		});
		return options;
	}
}