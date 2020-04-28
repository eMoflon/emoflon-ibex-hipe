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

import org.eclipse.core.resources.IFile;
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

		BasicMonitor monitor = new BasicMonitor.Printing(System.out);
		try {
			EcoreImporter importer = new EcoreImporter();
			importer.setModelLocation(metaModelUri.toString());
			IFile genModelFile = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(genModelLocation));
			if(!genModelFile.exists()) {
				Resource res = new ResourceSetImpl().createResource(URI.createPlatformResourceURI(genModelFile.getFullPath().toString(), true));
				GenModel dummyGenModel = GenModelFactory.eINSTANCE.createGenModel();
				res.getContents().add(dummyGenModel);
				res.save(Collections.emptyMap());
			}
			importer.computeEPackages(monitor);
			importer.adjustEPackages(monitor);
			
			Set<EPackage> importedEPackages = new HashSet<>();
			for (GenModel referencedGen : importer.getExternalGenModels()) {
				for (GenPackage genPackage : referencedGen.getGenPackages()) {
					EPackage ePackage = importer.getReferredEPackage(genPackage);
					if (ePackage != null && !metaModelUri.toString().equals(ePackage.getNsURI())) {
						importer.getReferencedGenPackages().add(genPackage);
						importer.getReferenceGenPackageConvertInfo(genPackage).setValidReference(true);
						importer.getEPackageConvertInfo(ePackage).setConvert(false);
						importedEPackages.add(ePackage);
					}
				}
			}
			for(EPackage ePackage : importer.getEPackages()) {
				if(!importedEPackages.contains(ePackage)) {
					importer.getEPackageConvertInfo(ePackage).setConvert(true);
				}
			}
			importer.setGenModelContainerPath(new Path(pluginID).append("model"));
			importer.setGenModelFileName(importer.computeDefaultGenModelFileName());
			importer.prepareGenModelAndEPackages(monitor);
			GenModel genModel = importer.getGenModel();
			genModel.setModelDirectory(options.project.path() + "/gen/");
			genModel.setGenerateSchema(true);
			genModel.setCanGenerate(true);
		    	genModel.reconcile();
	
			EcoreUtil.resolveAll(importer.getGenModelResourceSet());
//			importer.saveGenModelAndEPackages(monitor);
		    	genModel.eResource().save(Collections.emptyMap());
		    
			Generator generator = GenModelUtil.createGenerator(genModel);
			generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, monitor);
		} catch (Exception e) {
			System.err.println("Could not generate TGG metamodel code!");
		}
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
