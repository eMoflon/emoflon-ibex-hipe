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
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.common.project.BuildPropertiesHelper;
import org.emoflon.ibex.common.project.ManifestHelper;
import org.emoflon.ibex.gt.codegen.GTEngineBuilderExtension;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXModel;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternSet;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.moflon.core.utilities.ClasspathUtil;
import org.moflon.core.utilities.LogUtils;

import hipe.generator.HiPEGenerator;
import hipe.generator.HiPEGeneratorConfig;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEContainer;
import hipe.searchplan.SearchPlan;
import hipe.searchplan.simple.TriangleSearchPlan;

public class GTHiPEBuilderExtension implements GTEngineBuilderExtension{

	public static final String BUILDER_ID = "org.emoflon.ibex.gt.editor.ui.hipe.builder";
	private static Logger logger = Logger.getLogger(GTHiPEBuilderExtension.class);
	
	private String packageName;
	private String projectPath;

	@Override
	public void run(IProject project, IPath packagePath, IBeXModel ibexModel) {
		LogUtils.info(logger, "## HiPE ## Generating HiPE-Engine code..");	
		double tic = System.currentTimeMillis();
		
		packageName = packagePath.toString().replace("/", ".");

		IBeXPatternSet ibexPatterns = ibexModel.getPatternSet();
		
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
		HiPEGeneratorConfig config = new HiPEGeneratorConfig();
		HiPEGenerator.generateCode(packageName+".", projectPath, network, config);
		
		double toc = System.currentTimeMillis();
		LogUtils.info(logger, "Code generation completed in "+ (toc-tic)/1000.0 + " seconds.");	
		
		LogUtils.info(logger, "Saving HiPE patterns and HiPE network..");
		saveResource(container, projectPath + "/src-gen/" + packagePath.toString() +"/hipe/engine/hipe-patterns.xmi");
		saveResource(network, projectPath + "/src-gen/" + packagePath.toString() +"/hipe/engine/hipe-network.xmi");
		
		LogUtils.info(logger, "Refreshing workspace and cleaning build ..");
		try {
			project.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
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
