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
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.gt.hipe.ide.codegen.BuildPropertiesHelper;
import org.emoflon.ibex.gt.hipe.ide.codegen.ManifestHelper;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternSet;
import org.emoflon.ibex.tgg.compiler.transformations.patterns.ContextPatternTransformation;
import org.emoflon.ibex.tgg.ide.admin.BuilderExtension;
import org.emoflon.ibex.tgg.ide.admin.IbexTGGBuilder;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.gen.MODELGEN;
import org.emoflon.ibex.tgg.operational.strategies.modules.IbexExecutable;
import org.emoflon.ibex.tgg.operational.strategies.opt.CC;
import org.emoflon.ibex.tgg.operational.strategies.opt.CO;
import org.emoflon.ibex.tgg.operational.strategies.sync.INITIAL_BWD;
import org.emoflon.ibex.tgg.operational.strategies.sync.INITIAL_FWD;
import org.emoflon.ibex.tgg.operational.strategies.sync.SYNC;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.moflon.core.utilities.ClasspathUtil;
import org.moflon.core.utilities.LogUtils;
import org.moflon.tgg.mosl.tgg.TripleGraphGrammarFile;

import hipe.generator.HiPEGenerator;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEContainer;
import hipe.searchplan.SearchPlan;
import hipe.searchplan.simple.TGGSimpleSearchPlan;
import hipe.searchplan.simple.TGGTriangleSearchPlan;

public class IbexHiPEBuilderExtension implements BuilderExtension {

	private static final Logger logger = Logger.getLogger(IbexHiPEBuilderExtension.class);
	
	private String projectName;
	private String projectPath;
	
	private List<String> metaModelImports;
	
	@Override
	public void run(IbexTGGBuilder builder, TripleGraphGrammarFile editorModel, TripleGraphGrammarFile flattenedEditorModel) {
		LogUtils.info(logger, "Starting HiPE TGG builder ... ");
		
		projectName = builder.getProject().getName();
		projectPath = projectName;
		
		metaModelImports = flattenedEditorModel.getImports().stream()
				.map(imp -> imp.getName())
				.collect(Collectors.toList());
		
		LogUtils.info(logger, "Cleaning old code..");
		cleanOldCode(builder.getProject().getLocation().toPortableString());
		
		IFolder srcGenFolder = builder.getProject().getFolder("src-gen");
		IFolder genFolder = builder.getProject().getFolder("gen");
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
			executables.add(new INITIAL_FWD(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), metaModelImports)));
			executables.add(new INITIAL_BWD(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), metaModelImports)));
			executables.add(new SYNC(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), metaModelImports)));
			executables.add(new CC(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), metaModelImports)));
			executables.add(new CO(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), metaModelImports)));
			executables.add(new MODELGEN(HiPEBuilderUtil.registerResourceHandler(createIbexOptions(projectName, projectPath), metaModelImports)));
		} catch (IOException e) {
			LogUtils.error(logger, e);
			return;
		}
		
		// create the actual project path
		projectPath = builder.getProject().getLocation().toPortableString();
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
		
		String srcModel = srcPkg.getName();
		String trgModel = trgPkg.getName();
		IProject srcProject = getProjectInWorkspace(srcModel, builder.getProject().getWorkspace());
		IProject trgProject = getProjectInWorkspace(trgModel, builder.getProject().getWorkspace());
		
		String srcPkgName = getRootPackageName(srcProject);
		String trgPkgName = getRootPackageName(trgProject);
		String srcProjectName = srcProject.getName();
		String trgProjectName = trgProject.getName();
		String srcNS = srcPkg.getNsPrefix();
		String trgNS = trgPkg.getNsPrefix();
		
		LogUtils.info(logger, "Building missing app stubs...");
		try {
			generateRegHelper(builder, srcProject, trgProject, srcPkgName, trgPkgName);
			generateDefaultStubs(builder, editorModel, flattenedEditorModel);
		}catch(Exception e) {
			LogUtils.error(logger, e);
		}
		
		LogUtils.info(logger, "Updating Manifest & build properties..");
		updateManifest(builder.getProject(), srcProjectName, trgProjectName);
		updateBuildProperties();
		
		double tic = System.currentTimeMillis();
		executables.parallelStream().forEach(executable -> {
			LogUtils.info(logger, executable.getClass().getName() + ": Compiling ibex patterns from TGG patterns...");
			ContextPatternTransformation compiler = new ContextPatternTransformation(executable.getOptions(), executable.getOptions().matchDistributor());
			IBeXPatternSet ibexPatterns = compiler.transform();
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Converting IBeX to HiPE Patterns..");
			IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
			HiPEContainer container = transformation.transform(ibexPatterns);
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Creating search plan & generating Rete network..");
			SearchPlan searchPlan = new TGGSimpleSearchPlan(container);
//			SearchPlan searchPlan = new TGGTriangleSearchPlan(container);
//			SearchPlan searchPlan = new TriangleSearchPlan(container);
//			SearchPlan searchPlan = new SimpleSearchPlan(container);
			searchPlan.generateSearchPlan();
			HiPENetwork network = searchPlan.getNetwork();
			
			LogUtils.info(logger,  executable.getClass().getName() + ": Generating Code..");
			
			if(executable instanceof INITIAL_FWD) 
				HiPEGenerator.generateCode(projectName+".initfwd.", projectPath, network);
			else if(executable instanceof INITIAL_BWD) 
				HiPEGenerator.generateCode(projectName+".initbwd.", projectPath, network);
			else if(executable instanceof SYNC) 
				HiPEGenerator.generateCode(projectName+".sync.", projectPath, network);
			else if(executable instanceof CC && !(executable instanceof CO)) 
				HiPEGenerator.generateCode(projectName+".cc.", projectPath, network);
			else if(executable instanceof CO) 
				HiPEGenerator.generateCode(projectName+".co.", projectPath, network);
			else if(executable instanceof MODELGEN) 
				HiPEGenerator.generateCode(projectName+".modelgen.", projectPath, network);
			else
				throw new RuntimeException("Unsupported Operational Strategy detected");
			LogUtils.info(logger,  executable.getClass().getName() + ": Code generation completed");

			LogUtils.info(logger,  executable.getClass().getName() + ": Saving HiPE patterns and HiPE network..");
			String debugFolder = projectPath + "/debug";
			createNewDirectory(debugFolder);
			saveResource(container, debugFolder+"/" +  executable.getClass().getSimpleName().toLowerCase() + "_hipe-patterns.xmi");
			saveResource(network, debugFolder+"/" +  executable.getClass().getSimpleName().toLowerCase() + "_hipe-network.xmi");
			saveResource(ibexPatterns, debugFolder+"/" +  executable.getClass().getSimpleName().toLowerCase() + "_ibexPatterns.xmi");
		});
		double toc = System.currentTimeMillis();
		LogUtils.info(logger, "Pattern compilation and code generation completed in "+ (toc-tic)/1000.0 + " seconds.");
		
		LogUtils.info(logger, "Refreshing workspace and cleaning build ..");
		try {
			builder.getProject().getWorkspace().getRoot().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
//			builder.getProject().build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
		} catch (CoreException e) {
			LogUtils.error(logger, e.getMessage());
		}
		
		LogUtils.info(logger, "## HiPE ## --> HiPE build complete!");
	}
	
	public IbexOptions createIbexOptions(String projectName, String projectPath) {
		IbexOptions options = new IbexOptions();
		options.project.name(projectName);
		options.project.path(projectPath);
		options.debug.ibexDebug(false);
		options.propagate.optimizeSyncPattern(true);
		return options;
	}
	
	public void generateDefaultStubs(IbexTGGBuilder builder, TripleGraphGrammarFile editorModel, TripleGraphGrammarFile flattenedEditorModel) throws CoreException {
		builder.createDefaultDebugRunFile(HiPEFilesGenerator.MODELGEN_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateModelGenDebugFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.MODELGEN_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateModelGenFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.SYNC_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateSyncAppFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.INITIAL_FWD_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateInitialFwdAppFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.INITIAL_BWD_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateInitialBwdAppFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.CC_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateCCAppFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.CO_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateCOAppFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.FWD_OPT_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateFWDOptAppFile(projectName, fileName));
		builder.createDefaultRunFile(HiPEFilesGenerator.BWD_OPT_APP, (projectName, fileName) 
				-> HiPEFilesGenerator.generateBWDOptAppFile(projectName, fileName));
		builder.enforceDefaultConfigFile(HiPEFilesGenerator.DEFAULT_REGISTRATION_HELPER, (projectName, fileName)
				-> HiPEFilesGenerator.generateDefaultRegHelperFile(projectName));
	}
	
	public void generateRegHelper(IbexTGGBuilder builder, IProject srcProject, IProject trgProject, String srcPkg, String trgPkg) throws Exception {
		if(srcProject == null || trgProject == null) {
			LogUtils.info(logger, "Project belonging to src or trg model could not be found in the workspace. "
					+ "Therefore, the default registration helper file could not be created.");
			return;
		}
		if(srcPkg == null || trgPkg == null) {
			LogUtils.info(logger, "Source code belonging to src or trg model could not be found in the workspace. "
					+ "Therefore, the default registration helper file could not be created.");
			return;
		}
		builder.enforceDefaultConfigFile(HiPEFilesGenerator.REGISTRATION_HELPER, (projectName, fileName)
				-> HiPEFilesGenerator.generateRegHelperFile(projectName, srcProject.getName(), trgProject.getName(), srcPkg, trgPkg));
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
	
	private void updateManifest(IProject project, String srcPkg, String trgPkg) {
		try {
			IFile manifest = ManifestFileUpdater.getManifestFile(project);
			ManifestHelper helper = new ManifestHelper();
			helper.loadManifest(manifest);
			if(!helper.sectionContainsContent("Require-Bundle", "org.emoflon.ibex.tgg.runtime.hipe")) {
				helper.addContentToSection("Require-Bundle", "org.emoflon.ibex.tgg.runtime.hipe");
			}
			// TODO: This works in most cases except for Modisco, since there is no generated code present.
			// Fixit: MocaTreeToProcess complains about API access and only allows explicit package imports.
			if(srcPkg != null && !helper.sectionContainsContent("Require-Bundle", srcPkg)) {
				helper.addContentToSection("Require-Bundle", srcPkg);
			}
			
			if(trgPkg != null && !helper.sectionContainsContent("Require-Bundle", trgPkg)) {
				helper.addContentToSection("Require-Bundle", trgPkg);
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
}
