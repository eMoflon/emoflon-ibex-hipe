package org.emoflon.ibex.gt.hipe.ide.codegen;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.common.project.BuildPropertiesHelper;
import org.emoflon.ibex.common.project.ManifestHelper;
import org.emoflon.ibex.gt.editor.ui.builder.GTBuilderExtension;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternModelPackage;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternSet;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.moflon.core.utilities.ClasspathUtil;
import org.moflon.core.utilities.LogUtils;

import hipe.generator.HiPEGenerator;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEContainer;
import hipe.searchplan.SearchPlan;
import hipe.searchplan.simple.TriangleSearchPlan;

public class GTHiPEBuilderExtension implements GTBuilderExtension{

	public static final String BUILDER_ID = "org.emoflon.ibex.gt.editor.ui.hipe.builder";
	private static Logger logger = Logger.getLogger(GTHiPEBuilderExtension.class);
	
	private String packageName;
	private String packagePath;
	private String projectPath;
	
	@Override
	public void run(IProject project) {
		try {
			project.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
//			project.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
		} catch (CoreException e) {
			LogUtils.error(logger, e.getMessage());
		}
	}

	@Override
	public void run(IProject project, IPath packagePath) {
		LogUtils.info(logger, "## HiPE ## Generating HiPE-Engine code..");
		try {
			repairMetamodelResource();
		} catch (Exception e2) {
			LogUtils.error(logger, "Could not reload the previously unloaded IBeXPatternModel.ecore");
			return;
		}
		
		double tic = System.currentTimeMillis();
		
		packageName = packagePath.toString().replace("/", ".");
		
		if(project.getFullPath().makeAbsolute().toPortableString().equals(packagePath.makeAbsolute().toPortableString())) {
			this.packagePath = packagePath.makeAbsolute().toPortableString();
		}else {
			this.packagePath = project.getFullPath().makeAbsolute().toPortableString();
		}
		
		LogUtils.info(logger, "Loading IBeX patterns..");
		String patternPath = "/src-gen//" + packageName.replace(".", "//") + "//api//ibex-patterns.xmi";
		IBeXPatternSet ibexPatterns;
		try {
			ibexPatterns = loadIBeXPatterns(project, patternPath);
		} catch (Exception e2) {
			LogUtils.error(logger, e2.getMessage());
			return;
		}
		
		projectPath = project.getLocation().toPortableString();
		
		LogUtils.info(logger, "Cleaning old code..");
		cleanOldCode();

		LogUtils.info(logger, "Updating Manifest & build properties..");
		updateManifest(project);
		updateBuildProperties();
		IFolder srcGenFolder = project.getFolder("src-gen");
		try {
			ClasspathUtil.makeSourceFolderIfNecessary(srcGenFolder);
		} catch (CoreException e1) {
			LogUtils.error(logger, e1.getMessage());
		}
		
		LogUtils.info(logger, "Converting IBeX to HiPE Patterns..");
		IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
		HiPEContainer container = transformation.transform(ibexPatterns);
		
		LogUtils.info(logger, "Creating search plan & generating Rete network..");
		SearchPlan searchPlan = new TriangleSearchPlan(container);
//		SearchPlan searchPlan = new SimpleSearchPlan(container);
		searchPlan.generateSearchPlan();
		HiPENetwork network = searchPlan.getNetwork();
		
		LogUtils.info(logger, "Generating Code..");
		HiPEGenerator.generateCode(packageName+".", projectPath, network);
		
		double toc = System.currentTimeMillis();
		LogUtils.info(logger, "Code generation completed in "+ (toc-tic)/1000.0 + " seconds.");	
		
		LogUtils.info(logger, "Saving HiPE patterns and HiPE network..");
		String debugFolder = projectPath + "/debug";
		createNewDirectory(debugFolder);
		saveResource(container, debugFolder+"/hipe-patterns.xmi");
		saveResource(network, debugFolder+"/hipe-network.xmi");
		
		LogUtils.info(logger, "Refreshing workspace and cleaning build ..");
		try {
			project.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
//			project.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
		} catch (CoreException e) {
			LogUtils.error(logger, e.getMessage());
		}
		LogUtils.info(logger, "## HiPE ## --> HiPE build complete!");
	}
	
	private void updateManifest(IProject project) {
		try {
			IFile manifest = ManifestFileUpdater.getManifestFile(project);
			ManifestHelper helper = new ManifestHelper();
			helper.loadManifest(manifest);

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

			helper.updateProperties(buildProps);
			
		} catch (CoreException | IOException e) {
			LogUtils.error(logger, "Failed to update build.properties \n"+e.getMessage());
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
	
	private static void createNewDirectory(String path) {
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
	
	private void cleanOldCode() {
		File dir = new File(projectPath+"/src-gen/" + packageName.replace(".", "//") + "/hipe");
		if(dir.exists()) {
			LogUtils.info(logger, "--> Cleaning old source files in root folder: "+projectPath+"/src-gen/" + packageName.replace(".", "//") + "/hipe");
			if(!deleteDirectory(dir)) {
				LogUtils.error(logger, "Folder couldn't be deleted!");
			}
		} else {
			LogUtils.info(logger, "--> No previously generated code found, nothing to do!");
		}
	}
	
	private static IBeXPatternSet loadIBeXPatterns(IProject project, String path) throws Exception {
		Resource res = loadResource(project, path);
		return (IBeXPatternSet)res.getContents().get(0);
	}
	
	private static Resource loadResource(IProject project, String path) throws Exception {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		
		IPath ipath = project.getFile(path).getLocation();
		URI uri = URI.createFileURI(ipath.toOSString());
		Resource modelResource = null;
		try {
			modelResource = rs.getResource(uri, true);
		}catch(Exception e) {
			LogUtils.error(logger, "Couldn't load ibex pattern model.. Message was: \n" + e.getMessage());
		}
		
		if(modelResource == null)
			modelResource = rs.getResource(uri, true);
		
		EcoreUtil.resolveAll(rs);
		
		if(modelResource.getContents().isEmpty()) {
			throw new RuntimeException("Couldn't load ibex pattern model, workaround failed. Pattern set is empty.");
		}
		
		return modelResource;
	}
	
	private static void repairMetamodelResource() throws Exception {
		org.eclipse.emf.ecore.EPackage.Registry reg = EPackage.Registry.INSTANCE;
		EPackage pk = reg.getEPackage("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore");
		if(pk == null || pk.eIsProxy()) {
			if(pk.eResource() != null)
				pk.eResource().unload();
			
			reg.remove("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore");

			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
			ResourceSet rs = new ResourceSetImpl();
			rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
			Resource modelResource = rs.createResource(URI.createURI("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore"));
			pk = IBeXPatternModelPackage.eINSTANCE;
			modelResource.getContents().add(pk);

			pk = (EPackage) EcoreUtil.resolve(pk, rs);
			IBeXPatternModelPackage.eINSTANCE.eClass();
			reg.put("platform:/resource/org.emoflon.ibex.patternmodel/model/IBeXPatternModel.ecore", pk);
			
		}
	}
	
	public static void saveResource(EObject model, String path) {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		
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

}
