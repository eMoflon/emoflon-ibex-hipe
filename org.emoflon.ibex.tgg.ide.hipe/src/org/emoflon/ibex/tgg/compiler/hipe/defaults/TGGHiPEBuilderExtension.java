package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXModel;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXPatternSet;
import org.emoflon.ibex.common.project.BuildPropertiesHelper;
import org.emoflon.ibex.common.project.ManifestHelper;
import org.emoflon.ibex.gt.build.hipe.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternModelPackage;
import org.emoflon.ibex.tgg.codegen.TGGEngineBuilderExtension;
import org.emoflon.ibex.tgg.compiler.transformations.patterns.ContextPatternTransformation;
import org.emoflon.ibex.tgg.editor.builder.TGGBuildUtil;
import org.emoflon.ibex.tgg.editor.tgg.TripleGraphGrammarFile;
import org.emoflon.ibex.tgg.runtime.config.options.IbexOptions;
import org.emoflon.ibex.tgg.runtime.strategies.gen.MODELGEN;
import org.emoflon.ibex.tgg.runtime.strategies.integrate.INTEGRATE;
import org.emoflon.ibex.tgg.runtime.strategies.modules.IbexExecutable;
import org.emoflon.ibex.tgg.runtime.strategies.opt.CC;
import org.emoflon.ibex.tgg.runtime.strategies.opt.CO;
import org.emoflon.ibex.tgg.runtime.strategies.sync.INITIAL_BWD;
import org.emoflon.ibex.tgg.runtime.strategies.sync.INITIAL_FWD;
import org.emoflon.ibex.tgg.runtime.strategies.sync.SYNC;
import org.emoflon.ibex.tgg.tggl.tGGL.EditorFile;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.moflon.core.utilities.ClasspathUtil;
import org.moflon.core.utilities.LogUtils;

import hipe.generator.HiPEGenerator;
import hipe.generator.HiPEGeneratorConfig;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEContainer;
import hipe.searchplan.SearchPlan;
import hipe.searchplan.simple.LocalSearchPlan;

public class TGGHiPEBuilderExtension implements TGGEngineBuilderExtension {

	private static final Logger logger = Logger.getLogger(TGGHiPEBuilderExtension.class);
	
	private IProject project;
	private String projectName;
	private String projectPath;
	
	private List<String> metaModelImports;
	
	@Override
	public void run(IProject project, EditorFile editorModel, EditorFile flattenedEditorModel) {
		LogUtils.info(logger, "Starting HiPE TGG builder ... ");
		
		try {
			repairMetamodelResource();
		} catch (Exception e2) {
			LogUtils.error(logger, e2.getMessage());
			return;
		}
		
		this.project = project;
		projectName = project.getName();
		projectPath = projectName;
		

		metaModelImports = flattenedEditorModel.getImports().stream()
				.map(imp -> imp.getName())
				.collect(Collectors.toList());
		
		LogUtils.info(logger, "Cleaning old code..");
		cleanOldCode(project.getLocation().toPortableString());
		
		IFolder srcGenFolder = project.getFolder("src-gen");
		IFolder genFolder = project.getFolder("gen");
		try {
			ClasspathUtil.makeSourceFolderIfNecessary(srcGenFolder);
			ClasspathUtil.makeSourceFolderIfNecessary(genFolder);
		} catch (CoreException e1) {
			// TODO Auto-generated catch block
			LogUtils.error(logger, e1.getMessage());
		}

		LogUtils.info(logger, "Building TGG options...");
		
		LogUtils.info(logger, "Building TGG operational strategy...");
		Collection<IbexExecutable> executables = new HashSet<>();
		try {
			executables.add(new INITIAL_FWD(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), project, metaModelImports, true)));
			executables.add(new INITIAL_BWD(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), project, metaModelImports, false)));
			executables.add(new SYNC(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), project, metaModelImports, false)));
			executables.add(new CC(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), project, metaModelImports, false)));
			executables.add(new CO(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), project, metaModelImports, false)));
			executables.add(new MODELGEN(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), project, metaModelImports, false)));
			executables.add(new INTEGRATE(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath).patterns.optimizePattern(true), project, metaModelImports, false)));
		} catch (IOException e) {
			LogUtils.error(logger, e);
			return;
		}
		
		// create the actual project path
		projectPath = project.getLocation().toPortableString();
		EPackage srcPkg = flattenedEditorModel.getSchema().getSourceTypes().get(0);
		EPackage trgPkg = flattenedEditorModel.getSchema().getTargetTypes().get(0);
		EPackage corrPkg = flattenedEditorModel.eClass().getEPackage();
		try {
			if(srcPkg == null || trgPkg == null || corrPkg == null) {
				throw new RuntimeException("Could not get flattened trg or src model from editor model.");
			}
		} catch (Exception e) {
			LogUtils.error(logger, e); 
			return;
		}
		
		// initialize eclasses to prevent concurrent modification exceptions
		initializeEClasses(flattenedEditorModel.getSchema().getSourceTypes());
		initializeEClasses(flattenedEditorModel.getSchema().getTargetTypes());
		
		executables.forEach(this::initializeEClasses);
		
		String srcModel = srcPkg.getName();
		String trgModel = trgPkg.getName();
		IProject srcProject = getProjectInWorkspace(srcModel, project.getWorkspace());
		IProject trgProject = getProjectInWorkspace(trgModel, project.getWorkspace());
		String srcPkgName = null;
		String trgPkgName = null;
		
		if(srcProject != null) {
			srcPkgName = getRootPackageName(srcProject);
		}
		if(trgProject != null) {
			trgPkgName = getRootPackageName(trgProject);
		}
		
		LogUtils.info(logger, "Building missing app stubs...");
		try {
			generateRegHelper(srcProject, trgProject, srcPkgName, trgPkgName);
			generateDefaultStubs(editorModel, flattenedEditorModel);
		} catch(Exception e) {
			LogUtils.error(logger, e);
		}
		
		LogUtils.info(logger, "Updating Manifest & build properties..");
		updateManifest();
		updateBuildProperties();
		
		double tic = System.currentTimeMillis();
		executables.parallelStream().forEach(executable -> {
			LogUtils.info(logger, executable.getClass().getName() + ": Compiling ibex patterns from TGG patterns...");
			ContextPatternTransformation compiler = new ContextPatternTransformation(executable.getOptions(), executable.getOptions().matchDistributor());
		
			// initialize eclasses to prevent concurrent modification exceptions
			initializeEClasses(executable.getOptions().tgg.tgg().getSrc());
			initializeEClasses(executable.getOptions().tgg.tgg().getTrg());
			
			IBeXModel ibexModel = compiler.transform();
			IBeXPatternSet ibexPatterns = ibexModel.getPatternSet();
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Converting IBeX to HiPE Patterns..");
			IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
			HiPEContainer container = transformation.transform(ibexPatterns);
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Creating search plan & generating Rete network..");
			SearchPlan searchPlan = new LocalSearchPlan(container);
			searchPlan.generateSearchPlan();
			HiPENetwork network = searchPlan.getNetwork();
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Generating Code..");
			
			String packageName = null;
			if(executable instanceof INITIAL_FWD) 
				packageName = "initfwd";
			else if(executable instanceof INITIAL_BWD) 
				packageName = "initbwd";
			else if(executable instanceof SYNC) 
				packageName = "sync";
			else if(executable instanceof CC && !(executable instanceof CO)) 
				packageName = "cc";
			else if(executable instanceof CO) 
				packageName = "co";
			else if(executable instanceof MODELGEN) 
				packageName = "modelgen";
			else if(executable instanceof INTEGRATE) 
				packageName = "integrate";
			else
				throw new RuntimeException("Unsupported Operational Strategy detected");
			
			HiPEGeneratorConfig config = new HiPEGeneratorConfig();
			HiPEGenerator.generateCode(projectName+"." + packageName + ".", projectPath, network, config);
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Code generation completed");
			String hipePath = "src-gen/" + projectName + "/" + packageName + "/hipe/engine/";
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Saving HiPE patterns and HiPE network..");
			saveResource(container, projectPath +"/" + hipePath + "/hipe-patterns.xmi");
			saveResource(network, projectPath +"/" + hipePath + "/hipe-network.xmi");
			saveResource(ibexModel, projectPath +"/" + hipePath + "/ibex-patterns.xmi");
		});
		double toc = System.currentTimeMillis();
		LogUtils.info(logger, "Pattern compilation and code generation completed in "+ (toc-tic)/1000.0 + " seconds.");
		
		LogUtils.info(logger, "Refreshing workspace and cleaning build ..");
		try {
			project.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
//			builder.getProject().build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
		} catch (CoreException e) {
			LogUtils.error(logger, e.getMessage());
		}
		
		LogUtils.info(logger, "## HiPE ## --> HiPE build complete!");
	}
	
	/**
	 * initalize all eclasses (transitively) of a package by calling EAllSuperTypes and EAllReferences once
	 * @param packages
	 */
	private void initializeEClasses(EList<EPackage> packages) {
		for(EPackage pkg : packages) {
			initializeEClasses(pkg);
		}
	}
	
	/**
	 * initalize all eclasses (transitively) of a package by calling EAllSuperTypes and EAllReferences once
	 * @param package
	 */
	private void initializeEClasses(EPackage pkg) {
		for(EClassifier c : pkg.getEClassifiers()) {
			if(c instanceof EClass ec) {
				ec.getEAllSuperTypes();
				ec.getEAllReferences();
			}
		}
		for(EPackage sub : pkg.getESubpackages()) {
			initializeEClasses(sub);
		}
	}
	
	private void initializeEClasses(IbexExecutable ie) {
		ie.getOptions().tgg.getFlattenedConcreteTGGRules().forEach(r -> {
			r.getNodes().forEach(n -> {
				EClass type = n.getType();
				type.getEAllSuperTypes();
				type.getEAllReferences();
			});
		});
	}

	public IbexOptions createIbexOptions(String projectName, String projectPath) {
		IbexOptions options = new IbexOptions();
		options.project.name(projectName);
		options.project.path(projectPath);
		options.debug.ibexDebug(false);
		return options;
	}
	
	public void generateDefaultStubs(TripleGraphGrammarFile editorModel, TripleGraphGrammarFile flattenedEditorModel) throws CoreException {
		TGGBuildUtil.createDefaultDebugRunFile(project, HiPEFilesGenerator.MODELGEN_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateModelGenDebugFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.MODELGEN_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateModelGenFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.SYNC_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateSyncAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.INITIAL_FWD_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateInitialFwdAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.INITIAL_BWD_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateInitialBwdAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.CC_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateCCAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.CO_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateCOAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.FWD_OPT_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateFWDOptAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.BWD_OPT_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateBWDOptAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultRunFile(project, HiPEFilesGenerator.INTEGRATE_APP, (projectName, fileName)
				-> HiPEFilesGenerator.generateIntegrateAppFile(projectName, fileName));
		TGGBuildUtil.createDefaultConfigFile(project, HiPEFilesGenerator.DEFAULT_REGISTRATION_HELPER, (projectName, fileName)
				-> HiPEFilesGenerator.generateDefaultRegHelperFile(projectName));
	}
	
	public void generateRegHelper(IProject srcProject, IProject trgProject, String srcPkg, String trgPkg) throws Exception {
		String input_srcProject = srcProject == null ? "<<SRC_Project>>" : srcProject.getName();
		String input_trgProject = trgProject == null ? "<<TRG_Project>>" : trgProject.getName();
		String input_srcPackage = srcPkg == null ? "<<SRC_Package>>" : srcPkg;
		String input_trgPackage = trgPkg == null ? "<<TRG_Package>>" : trgPkg;
		
		TGGBuildUtil.createDefaultConfigFile(project, HiPEFilesGenerator.REGISTRATION_HELPER, (projectName, fileName)
				-> HiPEFilesGenerator.generateRegHelperFile(projectName, input_srcProject, input_trgProject, input_srcPackage, input_trgPackage));
	}
	
	private void cleanOldCode(String projectPath) {
		List<File> hipeRootDirectories = new LinkedList<>();
		hipeRootDirectories.add(new File(projectPath+"/gen"));
		hipeRootDirectories.add(new File(projectPath+"/src-gen/" + projectName + "/sync/hipe"));
		hipeRootDirectories.add(new File(projectPath+"/src-gen/" + projectName + "/cc/hipe"));
		hipeRootDirectories.add(new File(projectPath+"/src-gen/" + projectName + "/co/hipe"));
		hipeRootDirectories.add(new File(projectPath+"/src-gen/" + projectName + "/initbwd/hipe"));
		hipeRootDirectories.add(new File(projectPath+"/src-gen/" + projectName + "/initfwd/hipe"));
		hipeRootDirectories.add(new File(projectPath+"/src-gen/" + projectName + "/modelgen/hipe"));
		hipeRootDirectories.add(new File(projectPath+"/src-gen/" + projectName + "/integrate/hipe"));
		hipeRootDirectories.parallelStream().forEach(dir -> {
			if(dir.exists()) {
				LogUtils.info(logger, "--> Cleaning old source files in root folder: "+dir.getPath());
				if(!deleteDirectory(dir)) {
					LogUtils.error(logger, "Folder couldn't be deleted!");

				}
			} else {
				LogUtils.info(logger, "--> "+ dir.getPath() + ":\n No previously generated code found, nothing to do!");
			}
		});
	}
	
	private void updateManifest() {
		try {
			IFile manifest = ManifestFileUpdater.getManifestFile(project);
			ManifestHelper helper = new ManifestHelper();
			helper.loadManifest(manifest);
			if(!helper.sectionContainsContent("Require-Bundle", "org.emoflon.ibex.tgg.runtime.hipe")) {
				helper.addContentToSection("Require-Bundle", "org.emoflon.ibex.tgg.runtime.hipe");
			}
			
			File rawManifest = new File(projectPath+"/"+manifest.getFullPath().removeFirstSegments(1).toPortableString());
			
			helper.updateManifest(rawManifest);
			
		} catch (CoreException | IOException e) {
			LogUtils.error(logger, "Failed to update MANIFEST.MF \n"+e.getMessage());
		}
	}
	
	private void updateBuildProperties() {
		File buildProps = new File(projectPath+"/build.properties");
		BuildPropertiesHelper helper = new BuildPropertiesHelper();
		try {
			helper.loadProperties(buildProps);
			
			if(!helper.containsSection("source..")) {
				helper.appendSection("source..");
			}
			
			if(!helper.sectionContainsContent("source..", "src-gen/")) {
				helper.addContentToSection("source..", "src-gen/");
			}
			
			if(!helper.sectionContainsContent("source..", "gen/")) {
				helper.addContentToSection("source..", "gen/");
			}
			helper.updateProperties(buildProps);
			
		} catch (CoreException | IOException e) {
			LogUtils.error(logger, "Failed to update build.properties. \n"+e.getMessage());
		}
		
	}

	private static boolean deleteDirectory(File dir) {
		File[] contents  = dir.listFiles();
		if(contents != null) {
			for(File file : contents) {
				deleteDirectory(file);
			}
		}
		return dir.delete();
	}
	
	private void createNewDirectory(String path) {
		File dir = new File(path);
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				LogUtils.error(logger, "Directory in: "+path+" could not be created!");
			}else {
				LogUtils.info(logger, "--> Directory in: "+path+" created!");
			}
		}else {
			LogUtils.info(logger, "--> Directory already present in: "+path+", nothing to do.");
		}
	}
	
	private void saveResource(EObject model, String path) {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi-resource", new XMIResourceFactoryImpl());
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		
		URI uri = URI.createFileURI(path);
		Resource modelResource = rs.createResource(uri);
		modelResource.getContents().add(model);
		
		Map<Object, Object> saveOptions = ((XMIResource)modelResource).getDefaultSaveOptions();
		saveOptions.put(XMIResource.OPTION_ENCODING,"UTF-8");
		saveOptions.put(XMIResource.OPTION_USE_XMI_TYPE, Boolean.TRUE);
		saveOptions.put(XMIResource.OPTION_SAVE_TYPE_INFORMATION,Boolean.TRUE);
		saveOptions.put(XMIResource.OPTION_SCHEMA_LOCATION_IMPLEMENTATION, Boolean.TRUE);
		
		try {
			((XMIResource)modelResource).save(saveOptions);
		} catch (IOException e) {
			LogUtils.error(logger, "Couldn't save debug resource: \n "+e.getMessage());
		}
	}
	
	private static IProject getProjectInWorkspace(String modelName, IWorkspace workspace) {
		IProject[] projects = workspace.getRoot().getProjects();
		for(IProject project : projects) {
			if(project.getName().toLowerCase().equals(modelName.toLowerCase())) {
				return project;
			}
		}
		LogUtils.info(logger, "The project belonging to model "+modelName+" could not be found in the workspace.");
		return null;
	}
	
	private static String getRootPackageName(IProject project) {
		String upperPkgName = project.getName();
		String firstLower = project.getName().substring(0, 1).toLowerCase()+project.getName().substring(1);
		String lowerPkgName = project.getName().toLowerCase();
		
		IPath projectPath = project.getLocation().makeAbsolute();
		Path srcPath = Paths.get(projectPath.toPortableString()+"/src");
		File srcFolder = srcPath.toFile();
		if(srcFolder.exists() && srcFolder.isDirectory()) {
			for(String fName : srcFolder.list()) {
				if(fName.equals(upperPkgName)) {
					return upperPkgName;
				}
				if(fName.equals(firstLower)) {
					return firstLower;
				}
				if(fName.equals(lowerPkgName)) {
					return lowerPkgName;
				}
			}	
		}
		
		
		srcPath = Paths.get(projectPath.toPortableString()+"/src-gen");
		srcFolder = srcPath.toFile();
		if(srcFolder.exists() && srcFolder.isDirectory()) {
			for(String fName : srcFolder.list()) {
				if(fName.equals(upperPkgName)) {
					return upperPkgName;
				}
				if(fName.equals(firstLower)) {
					return firstLower;
				}
				if(fName.equals(lowerPkgName)) {
					return lowerPkgName;
				}
			}
		}
		
		srcPath = Paths.get(projectPath.toPortableString()+"/gen");
		srcFolder = srcPath.toFile();
		if(srcFolder.exists() && srcFolder.isDirectory()) {
			for(String fName : srcFolder.list()) {
				if(fName.equals(upperPkgName)) {
					return upperPkgName;
				}
				if(fName.equals(firstLower)) {
					return firstLower;
				}
				if(fName.equals(lowerPkgName)) {
					return lowerPkgName;
				}
			}
		}
		
		LogUtils.info(logger, "The project belonging to model "+project.getName()+" does not seem to have generated code.");
		return null;
	}
	
	private static void repairMetamodelResource() throws Exception {
		org.eclipse.emf.ecore.EPackage.Registry reg = EPackage.Registry.INSTANCE;
		EPackage pk = reg.getEPackage("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore");
		if(pk == null || pk.eIsProxy()) {
			reg.remove("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore");

			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().putIfAbsent("ecore", new EcoreResourceFactoryImpl());
			ResourceSet rs = new ResourceSetImpl();
			rs.getResourceFactoryRegistry().getExtensionToFactoryMap().putIfAbsent("ecore", new EcoreResourceFactoryImpl());
			Resource modelResource = rs.createResource(URI.createURI("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore"));
			pk = IBeXPatternModelPackage.eINSTANCE;
			modelResource.getContents().add(pk);

			EcoreUtil.resolveAll(pk);
			IBeXPatternModelPackage.eINSTANCE.eClass();
			reg.put("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore", pk);
			
		}
		
		EPackage pk2 = reg.getEPackage("platform:/plugin/org.emoflon.ibex.tgg.core.language/model/Language.ecore");
		if(pk2 == null || pk2.eIsProxy()) {
			reg.remove("platform:/plugin/org.emoflon.ibex.tgg.core.language/model/Language.ecore");

			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().putIfAbsent("ecore", new EcoreResourceFactoryImpl());
			ResourceSet rs = new ResourceSetImpl();
			rs.getResourceFactoryRegistry().getExtensionToFactoryMap().putIfAbsent("ecore", new EcoreResourceFactoryImpl());
			Resource modelResource = rs.createResource(URI.createURI("platform:/plugin/org.emoflon.ibex.tgg.core.language/model/Language.ecore"));
			pk2 = LanguagePackage.eINSTANCE;
			modelResource.getContents().add(pk2);

			EcoreUtil.resolveAll(pk2);
			LanguagePackage.eINSTANCE.eClass();
			reg.put("platform:/plugin/org.emoflon.ibex.tgg.core.language/model/Language.ecore", pk2);
		}
	}
}
